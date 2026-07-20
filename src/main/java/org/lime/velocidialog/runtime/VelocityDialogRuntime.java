package org.lime.velocidialog.runtime;

import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.event.ClickCallback;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogResponseView;
import org.lime.velocidialog.api.dialog.Dialogs;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.action.DialogActionCallback;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.codec.DialogNbtCodec;
import org.lime.velocidialog.protocol.DialogPacketBodies;
import org.lime.velocidialog.protocol.DialogProtocolMapping;
import org.lime.velocidialog.protocol.MinecraftVarInts;
import org.lime.velocidialog.protocol.ProtocolPhase;
import org.lime.velocidialog.protocol.ShowDialogPayload;
import org.slf4j.Logger;

/** CONFIGURATION-first dialog transport and public {@link Dialogs} provider. */
public final class VelocityDialogRuntime implements Dialogs.Provider,
        DialogInternals.CallbackRegistrar, AutoCloseable {
    private static final int MAX_PENDING_OPERATIONS = 64;

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private final VelocityConnectionAccess connections;
    private final CallbackRegistry callbacks;
    private final Object lifecycleLock = new Object();
    private final Map<Player, Queue<PendingOperation>> pending = new IdentityHashMap<>();
    private volatile ScheduledTask cleanupTask;
    private volatile boolean closed;

    public VelocityDialogRuntime(
            final ProxyServer proxy,
            final Object plugin,
            final Logger logger) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.connections = VelocityConnectionAccess.discover();
        this.callbacks = new CallbackRegistry(proxy, plugin, logger);
        DialogInternals.installCallbackRegistrar(this);
    }

    /** Starts periodic expiry after Velocity has emitted ProxyInitializeEvent. */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("Velocidialog runtime is closed");
        }
        if (cleanupTask == null) {
            cleanupTask = proxy.getScheduler().buildTask(plugin, callbacks::purgeExpired)
                    .delay(Duration.ofSeconds(5))
                    .repeat(Duration.ofSeconds(5))
                    .schedule();
        }
    }

    /** Entry point installed into Player.showDialog(DialogLike). */
    public void showFromBridge(final Object playerObject, final Object dialogObject) {
        if (!(playerObject instanceof Player player)) {
            throw new IllegalArgumentException("Dialog audience is not a Velocity Player");
        }
        if (!(dialogObject instanceof DialogLike dialogLike)) {
            throw new IllegalArgumentException("showDialog argument is not a DialogLike");
        }
        if (!(dialogLike instanceof Dialog dialog)) {
            throw new IllegalArgumentException("Unsupported DialogLike implementation: "
                    + dialogLike.getClass().getName()
                    + "; use org.lime.velocidialog.api.dialog.Dialog");
        }

        dispatch(PendingOperation.show(player, dialog, () -> true))
                .whenComplete((ignored, failure) -> logAsyncFailure("show", player, failure));
    }

    /** Entry point installed into Player.closeDialog(). */
    public void closeFromBridge(final Object playerObject) {
        if (!(playerObject instanceof Player player)) {
            throw new IllegalArgumentException("Dialog audience is not a Velocity Player");
        }
        dispatch(PendingOperation.close(player))
                .whenComplete((ignored, failure) -> logAsyncFailure("close", player, failure));
    }

    public boolean handleConfigurationClick(final Object player, final Object packet) {
        return callbacks.handleConfiguration(player, packet);
    }

    public boolean handlePlayClick(final Object player, final Object buffer) {
        return callbacks.handlePlay(player, buffer);
    }

    @Override
    public DialogAction.CustomClickAction register(
            final DialogActionCallback callback,
            final ClickCallback.Options options) {
        if (closed) {
            throw new IllegalStateException("Velocidialog runtime is closed");
        }
        return callbacks.register(callback, options).action();
    }

    @Override
    public CompletionStage<DialogResponseView> showAndAwait(
            final Player player,
            final Function<DialogAction.CustomClickAction, Dialog> factory,
            final Duration timeout) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Velocidialog runtime is closed"));
        }
        if (!DialogProtocolMapping.supports(player.getProtocolVersion().getProtocol())) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "Dialogs are not mapped for client protocol "
                            + player.getProtocolVersion().getProtocol()));
        }

        final CompletableFuture<DialogResponseView> response = new CompletableFuture<>();
        final CallbackRegistry.Registration registration;
        try {
            registration = callbacks.register(
                    (view, audience) -> response.complete(view),
                    1,
                    timeout,
                    player,
                    response::completeExceptionally);
        } catch (final RuntimeException registrationFailure) {
            return CompletableFuture.failedFuture(registrationFailure);
        }

        final Dialog dialog;
        try {
            dialog = Objects.requireNonNull(factory.apply(registration.action()),
                    "dialog factory returned null");
        } catch (final Throwable factoryFailure) {
            callbacks.remove(registration.id(), factoryFailure);
            return response;
        }

        final AtomicReference<ScheduledTask> timeoutTask = new AtomicReference<>();
        try {
            timeoutTask.set(proxy.getScheduler().buildTask(plugin, () -> {
                final TimeoutException failure = new TimeoutException(
                        "No dialog response received within " + timeout);
                if (callbacks.expire(registration.id(), failure)) {
                    response.completeExceptionally(failure);
                }
            }).delay(timeout).schedule());
        } catch (final RuntimeException schedulingFailure) {
            callbacks.remove(registration.id(), schedulingFailure);
            return response;
        }

        response.whenComplete((value, failure) -> {
            final ScheduledTask task = timeoutTask.get();
            if (task != null) {
                task.cancel();
            }
            final Throwable removalReason;
            if (failure != null) {
                removalReason = unwrap(failure);
            } else {
                removalReason = new CancellationException(
                        "Dialog response stage was completed externally");
            }
            callbacks.remove(registration.id(), removalReason);
        });

        dispatch(PendingOperation.show(player, dialog, () -> !response.isDone()))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        callbacks.remove(registration.id(), unwrap(failure));
                    }
                });
        return response;
    }

    /** Flushes operations requested from PostLogin as soon as CONFIGURATION is entered. */
    public void enteredConfiguration(final Player player) {
        if (closed || !connections.supports(player)) {
            return;
        }
        try {
            final Object connection = connections.connection(player);
            connections.eventLoop(connection).execute(() -> {
                final Queue<PendingOperation> operations;
                synchronized (lifecycleLock) {
                    operations = pending.remove(player);
                }
                if (operations == null) {
                    return;
                }
                PendingOperation operation;
                while ((operation = operations.poll()) != null) {
                    executeSafely(operation, connection, false);
                }
            });
        } catch (final RuntimeException failure) {
            logger.warn("Unable to flush dialogs on entering CONFIGURATION for {}", player, failure);
        }
    }

    public void disconnected(final Player player) {
        final Queue<PendingOperation> operations;
        synchronized (lifecycleLock) {
            operations = pending.remove(player);
        }
        if (operations != null) {
            final CancellationException failure = new CancellationException(
                    "Player disconnected before the dialog could be sent");
            operations.forEach(operation -> operation.result().completeExceptionally(failure));
        }
        callbacks.disconnect(player);
    }

    private CompletionStage<Void> dispatch(final PendingOperation operation) {
        if (closed) {
            operation.result().completeExceptionally(
                    new IllegalStateException("Velocidialog runtime is closed"));
            return operation.result();
        }
        final Player player = operation.player();
        final int protocol = player.getProtocolVersion().getProtocol();
        if (!DialogProtocolMapping.supports(protocol)) {
            // Player.showDialog is specified as a safe no-op on unsupported protocol versions.
            operation.result().complete(null);
            return operation.result();
        }
        if (!connections.supports(player)) {
            operation.result().completeExceptionally(new IllegalArgumentException(
                    "Player is not Velocity's ConnectedPlayer: " + player.getClass().getName()));
            return operation.result();
        }

        try {
            final Object connection = connections.connection(player);
            final Executor eventLoop = connections.eventLoop(connection);
            eventLoop.execute(() -> executeSafely(operation, connection, true));
        } catch (final RuntimeException failure) {
            operation.result().completeExceptionally(failure);
        }
        return operation.result();
    }

    private void executeOrDefer(
            final PendingOperation operation,
            final Object connection,
            final boolean mayDefer) {
        if (operation.result().isDone()) {
            return;
        }
        if (!operation.active().getAsBoolean()) {
            operation.result().completeExceptionally(
                    new CancellationException("Dialog operation is no longer active"));
            return;
        }
        if (closed || connections.isClosed(connection)) {
            operation.result().completeExceptionally(
                    new CancellationException("Player connection is closed"));
            return;
        }

        final ProtocolState state = connections.outboundProtocolState(connection);
        if (state == ProtocolState.CONFIGURATION || state == ProtocolState.PLAY) {
            writeNow(operation, connection, state);
            return;
        }
        if (mayDefer && (state == ProtocolState.LOGIN || state == ProtocolState.HANDSHAKE)) {
            enqueuePending(operation, connection);
            return;
        }
        operation.result().completeExceptionally(new IllegalStateException(
                "Cannot send a dialog while player protocol state is " + state));
    }

    private void enqueuePending(
            final PendingOperation operation,
            final Object connection) {
        synchronized (lifecycleLock) {
            // Recheck under the same lock used by close/disconnect so an operation cannot be
            // inserted after either cleanup has passed this player.
            if (closed || connections.isClosed(connection)) {
                operation.result().completeExceptionally(
                        new CancellationException("Player connection is closed"));
                return;
            }
            final Queue<PendingOperation> queue = pending.computeIfAbsent(
                    operation.player(), ignored -> new ArrayDeque<>());
            if (queue.size() >= MAX_PENDING_OPERATIONS) {
                operation.result().completeExceptionally(new IllegalStateException(
                        "Too many dialog operations queued before CONFIGURATION"));
                return;
            }
            queue.add(operation);
        }
    }

    private void executeSafely(
            final PendingOperation operation,
            final Object connection,
            final boolean mayDefer) {
        try {
            executeOrDefer(operation, connection, mayDefer);
        } catch (final RuntimeException | LinkageError failure) {
            operation.result().completeExceptionally(failure);
        }
    }

    private void writeNow(
            final PendingOperation operation,
            final Object connection,
            final ProtocolState state) {
        final int protocol = operation.player().getProtocolVersion().getProtocol();
        final DialogProtocolMapping mapping = DialogProtocolMapping.forProtocol(protocol);
        final ProtocolPhase phase = state == ProtocolState.CONFIGURATION
                ? ProtocolPhase.CONFIGURATION : ProtocolPhase.PLAY;
        final ByteBuf packet = Unpooled.buffer();
        boolean handedOff = false;
        try {
            if (operation.kind() == OperationKind.SHOW) {
                MinecraftVarInts.write(packet, mapping.showDialog(phase));
                final CompoundBinaryTag nbt = new DialogNbtCodec().encode(operation.dialog());
                DialogPacketBodies.writeShow(packet, phase, ShowDialogPayload.inline(nbt));
            } else {
                MinecraftVarInts.write(packet, mapping.clearDialog(phase));
                DialogPacketBodies.writeClear(packet);
            }

            final Future<?> writeFuture = connections.write(connection, packet);
            handedOff = true; // MinecraftConnection owns/releases the buffer even when inactive.
            if (writeFuture == null) {
                throw new CancellationException("Player disconnected before packet write");
            }
            writeFuture.addListener(completed -> {
                if (completed.isSuccess()) {
                    operation.result().complete(null);
                    return;
                }
                final Throwable cause = completed.cause() == null
                        ? new IllegalStateException("Dialog packet write was cancelled")
                        : completed.cause();
                operation.result().completeExceptionally(cause);
            });
        } catch (final Throwable failure) {
            operation.result().completeExceptionally(failure);
        } finally {
            if (!handedOff) {
                ReferenceCountUtil.release(packet);
            }
        }
    }

    private void logAsyncFailure(
            final String operation,
            final Player player,
            final Throwable failure) {
        if (failure == null || failure instanceof CancellationException) {
            return;
        }
        logger.warn("Unable to {} dialog for {}", operation, player, unwrap(failure));
    }

    private static Throwable unwrap(final Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    @Override
    public void close() {
        final List<PendingOperation> abandoned = new ArrayList<>();
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            pending.values().forEach(abandoned::addAll);
            pending.clear();
        }
        final ScheduledTask task = cleanupTask;
        if (task != null) {
            task.cancel();
        }
        final CancellationException failure = new CancellationException("Velocidialog shut down");
        abandoned.forEach(operation -> operation.result().completeExceptionally(failure));
        DialogInternals.clearCallbackRegistrar(this);
        callbacks.close();
    }

    private enum OperationKind {
        SHOW,
        CLOSE
    }

    private record PendingOperation(
            OperationKind kind,
            Player player,
            Dialog dialog,
            BooleanSupplier active,
            CompletableFuture<Void> result) {
        static PendingOperation show(
                final Player player,
                final Dialog dialog,
                final BooleanSupplier active) {
            return new PendingOperation(OperationKind.SHOW, player, dialog, active,
                    new CompletableFuture<>());
        }

        static PendingOperation close(final Player player) {
            return new PendingOperation(OperationKind.CLOSE, player, null, () -> true,
                    new CompletableFuture<>());
        }
    }
}
