package org.lime.velocidialog.runtime;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.event.ClickCallback;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.DialogResponseView;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.action.DialogActionCallback;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.protocol.CustomClickPacketBody;
import org.lime.velocidialog.protocol.DialogPacketBodies;
import org.lime.velocidialog.protocol.DialogProtocolMapping;
import org.lime.velocidialog.protocol.MinecraftVarInts;
import org.slf4j.Logger;

/** Thread-safe, Paper-style lifecycle for dialog custom-click callbacks. */
final class CallbackRegistry implements AutoCloseable {
    static final String CALLBACK_IDENTIFIER = "velocidialog:dialog_click_callback";
    static final String ID_KEY = "id";
    private static final int MAX_PACKET_BYTES = 65_536;

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private final ConcurrentMap<UUID, StoredCallback> callbacks = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private volatile boolean closed;

    CallbackRegistry(final ProxyServer proxy, final Object plugin, final Logger logger) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    Registration register(
            final DialogActionCallback callback,
            final int maxUses,
            final Duration lifetime,
            final @Nullable Player owner,
            final @Nullable Consumer<Throwable> cancellation) {
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(lifetime, "lifetime");
        if (maxUses != ClickCallback.UNLIMITED_USES && maxUses <= 0) {
            throw new IllegalArgumentException("maxUses must be -1 or positive");
        }
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }

        final UUID id = UUID.randomUUID();
        final StoredCallback stored = new StoredCallback(
                callback,
                maxUses,
                lifetime,
                owner,
                cancellation == null ? ignored -> { } : cancellation);
        synchronized (lifecycleLock) {
            if (closed) {
                throw new IllegalStateException("Velocidialog callback registry is closed");
            }
            callbacks.put(id, stored);
        }
        return new Registration(id, callbackAction(id));
    }

    Registration register(
            final DialogActionCallback callback,
            final ClickCallback.Options options) {
        Objects.requireNonNull(options, "options");
        return register(callback, options.uses(), options.lifetime(), null, null);
    }

    private static DialogAction.CustomClickAction callbackAction(final UUID id) {
        final CompoundBinaryTag additions = CompoundBinaryTag.builder()
                .putIntArray(ID_KEY, uuidToIntArray(id))
                .build();
        return DialogAction.customClick(
                Key.key(CALLBACK_IDENTIFIER),
                DialogInternals.encodeCompound(additions));
    }

    void remove(final UUID id, final Throwable reason) {
        final StoredCallback removed = callbacks.remove(id);
        if (removed != null) {
            removed.cancel(reason);
        }
    }

    boolean handleConfiguration(final Object playerOrSessionHandler, final Object packet) {
        final Player player = configurationPlayer(playerOrSessionHandler);
        if (player == null) {
            return false;
        }
        if (!DialogProtocolMapping.supports(player.getProtocolVersion().getProtocol())) {
            return false;
        }
        final ByteBuf content;
        try {
            final Method method = packet.getClass().getMethod("content");
            content = (ByteBuf) method.invoke(packet);
        } catch (final NoSuchMethodException | IllegalAccessException
                       | InvocationTargetException | ClassCastException error) {
            logger.error("Velocity CONFIG custom-click packet no longer exposes its raw content", error);
            return false;
        }
        // The deferred CONFIG packet content starts after its one-byte packet id.
        return handleBody(player, content.duplicate(), (long) content.readableBytes() + 1L);
    }

    private Player configurationPlayer(final Object playerOrSessionHandler) {
        if (playerOrSessionHandler instanceof Player player) {
            return player;
        }
        if (playerOrSessionHandler == null) {
            return null;
        }
        try {
            final Field field = playerOrSessionHandler.getClass().getDeclaredField("player");
            if (!field.trySetAccessible()) {
                logger.error("Velocity CONFIG session handler player field is not accessible");
                return null;
            }
            final Object value = field.get(playerOrSessionHandler);
            if (value instanceof Player player) {
                return player;
            }
            logger.error("Velocity CONFIG session handler player field has an unexpected value");
        } catch (final NoSuchFieldException | IllegalAccessException error) {
            logger.error("Velocity CONFIG session handler no longer exposes its player", error);
        }
        return null;
    }

    boolean handlePlay(final Object playerObject, final Object bufferObject) {
        if (!(playerObject instanceof Player player) || !(bufferObject instanceof ByteBuf buffer)) {
            return false;
        }
        final int protocol = player.getProtocolVersion().getProtocol();
        if (!DialogProtocolMapping.supports(protocol)) {
            return false;
        }

        final ByteBuf duplicate = buffer.duplicate();
        final int packetId;
        try {
            packetId = MinecraftVarInts.read(duplicate);
        } catch (final RuntimeException malformedId) {
            return false;
        }
        if (packetId != DialogProtocolMapping.forProtocol(protocol).playCustomClick()) {
            return false;
        }
        return handleBody(player, duplicate, buffer.readableBytes());
    }

    private boolean handleBody(
            final Player player,
            final ByteBuf body,
            final long packetBytes) {
        final String identifier;
        try {
            identifier = DialogPacketBodies.readCustomClickIdentifier(body.duplicate());
        } catch (final RuntimeException malformedIdentifier) {
            return false;
        }
        if (!CALLBACK_IDENTIFIER.equals(identifier)) {
            return false;
        }

        // Once the reserved key is known, malformed, expired and replayed requests are consumed.
        if (packetBytes > MAX_PACKET_BYTES) {
            logger.debug("Discarding oversized Velocidialog callback from {}", player);
            return true;
        }

        final CustomClickPacketBody decoded;
        try {
            decoded = DialogPacketBodies.readCustomClick(body);
        } catch (final RuntimeException malformedPayload) {
            logger.debug("Discarding malformed Velocidialog callback from {}", player,
                    malformedPayload);
            return true;
        }
        if (decoded.payload() == null) {
            return true;
        }

        final UUID id = readUuid(decoded.payload());
        if (id == null) {
            return true;
        }

        final AtomicReference<StoredCallback> claimed = new AtomicReference<>();
        final AtomicReference<StoredCallback> expired = new AtomicReference<>();
        callbacks.computeIfPresent(id, (ignored, callback) -> {
            if (callback.owner() != null && callback.owner() != player) {
                return callback;
            }
            if (callback.expired() && !callback.hasInFlightUses()) {
                expired.set(callback);
                return null;
            }
            if (!callback.tryTakeUse()) {
                return callback;
            }
            callback.beginInFlightUse();
            claimed.set(callback);
            // Keep claimed callbacks addressable until their scheduler tasks finish. This lets
            // disconnect/shutdown cancel them and prevents a deadline from racing a received packet.
            return callback;
        });

        final StoredCallback expiredCallback = expired.get();
        if (expiredCallback != null) {
            expiredCallback.cancel(new TimeoutException("Dialog callback expired"));
        }

        final StoredCallback callback = claimed.get();
        if (callback == null) {
            return true;
        }

        final DialogResponseView response = DialogInternals.responseView(decoded.payload());
        try {
            proxy.getScheduler().buildTask(plugin, () -> {
                try {
                    if (!closed && !callback.cancelled()) {
                        callback.callback().accept(response, player);
                    }
                } catch (final Throwable error) {
                    logger.error("Unhandled exception in a Velocidialog callback", error);
                } finally {
                    finishClaim(id, callback);
                }
            }).schedule();
        } catch (final RuntimeException schedulingFailure) {
            logger.error("Unable to schedule a Velocidialog callback", schedulingFailure);
            final boolean finalClaim = callback.finishInFlightUse()
                    && !callback.hasRemainingUses();
            if (finalClaim && callbacks.remove(id, callback)) {
                callback.cancel(schedulingFailure);
            }
        }
        return true;
    }

    /** Expires an idle callback, but never lets a deadline overtake an already received click. */
    boolean expire(final UUID id, final Throwable reason) {
        final AtomicReference<StoredCallback> removed = new AtomicReference<>();
        callbacks.computeIfPresent(id, (ignored, callback) -> {
            if (callback.hasInFlightUses()) {
                return callback;
            }
            removed.set(callback);
            return null;
        });
        final StoredCallback callback = removed.get();
        if (callback == null) {
            return false;
        }
        callback.cancel(reason);
        return true;
    }

    void disconnect(final Player player) {
        final CancellationException reason = new CancellationException(
                "Player disconnected while waiting for a dialog response");
        final List<StoredCallback> removed = new ArrayList<>();
        synchronized (lifecycleLock) {
            callbacks.forEach((id, callback) -> {
                if (callback.owner() == player && callbacks.remove(id, callback)) {
                    removed.add(callback);
                }
            });
        }
        removed.forEach(callback -> callback.cancel(reason));
    }

    void purgeExpired() {
        final TimeoutException reason = new TimeoutException("Dialog callback expired");
        callbacks.forEach((id, callback) -> {
            if (callback.expired()) {
                expire(id, reason);
            }
        });
    }

    int size() {
        return callbacks.size();
    }

    @Override
    public void close() {
        final CancellationException reason = new CancellationException("Velocidialog shut down");
        final List<StoredCallback> removed;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            removed = new ArrayList<>(callbacks.values());
            callbacks.clear();
        }
        removed.forEach(callback -> callback.cancel(reason));
    }

    private static int[] uuidToIntArray(final UUID id) {
        return new int[]{
                (int) (id.getMostSignificantBits() >>> 32),
                (int) id.getMostSignificantBits(),
                (int) (id.getLeastSignificantBits() >>> 32),
                (int) id.getLeastSignificantBits()
        };
    }

    private static UUID readUuid(final CompoundBinaryTag payload) {
        final int[] value = payload.getIntArray(ID_KEY, null);
        if (value == null || value.length != 4) {
            return null;
        }
        final long most = ((long) value[0] << 32) | (value[1] & 0xffffffffL);
        final long least = ((long) value[2] << 32) | (value[3] & 0xffffffffL);
        return new UUID(most, least);
    }

    private void finishClaim(final UUID id, final StoredCallback callback) {
        if (!callback.finishInFlightUse()) {
            return;
        }
        if (!callback.hasRemainingUses() || callback.expired()) {
            callbacks.remove(id, callback);
        }
    }

    record Registration(UUID id, DialogAction.CustomClickAction action) {
        Registration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(action, "action");
        }
    }

    private static final class StoredCallback {
        private final DialogActionCallback callback;
        private final AtomicInteger remainingUses;
        private final AtomicInteger inFlightUses = new AtomicInteger();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final long startedAt = System.nanoTime();
        private final long lifetimeNanos;
        private final @Nullable Player owner;
        private final Consumer<Throwable> cancellation;

        StoredCallback(
                final DialogActionCallback callback,
                final int remainingUses,
                final Duration lifetime,
                final @Nullable Player owner,
                final Consumer<Throwable> cancellation) {
            this.callback = callback;
            this.remainingUses = new AtomicInteger(remainingUses);
            this.lifetimeNanos = saturatedNanos(lifetime);
            this.owner = owner;
            this.cancellation = cancellation;
        }

        DialogActionCallback callback() {
            return callback;
        }

        @Nullable Player owner() {
            return owner;
        }

        boolean tryTakeUse() {
            if (expired()) {
                return false;
            }
            if (remainingUses.get() == ClickCallback.UNLIMITED_USES) {
                return true;
            }
            while (true) {
                final int current = remainingUses.get();
                if (current <= 0) {
                    return false;
                }
                if (remainingUses.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        boolean hasRemainingUses() {
            final int uses = remainingUses.get();
            return uses == ClickCallback.UNLIMITED_USES || uses > 0;
        }

        void beginInFlightUse() {
            inFlightUses.incrementAndGet();
        }

        /** @return whether the completed use was the last in-flight scheduler task */
        boolean finishInFlightUse() {
            final int remaining = inFlightUses.decrementAndGet();
            if (remaining < 0) {
                throw new IllegalStateException("Dialog callback in-flight use underflow");
            }
            return remaining == 0;
        }

        boolean hasInFlightUses() {
            return inFlightUses.get() != 0;
        }

        boolean cancelled() {
            return cancelled.get();
        }

        boolean expired() {
            return lifetimeNanos != Long.MAX_VALUE
                    && System.nanoTime() - startedAt >= lifetimeNanos;
        }

        void cancel(final Throwable reason) {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            try {
                cancellation.accept(reason);
            } catch (final RuntimeException ignored) {
                // Cancellation must not make registry cleanup fail.
            }
        }

        private static long saturatedNanos(final Duration duration) {
            try {
                return duration.toNanos();
            } catch (final ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }
    }
}
