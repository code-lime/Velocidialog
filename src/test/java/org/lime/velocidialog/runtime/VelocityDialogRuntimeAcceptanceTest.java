package org.lime.velocidialog.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.scheduler.TaskStatus;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.DialogResponseView;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.dialog.type.DialogType;
import org.lime.velocidialog.protocol.CustomClickPacketBody;
import org.lime.velocidialog.protocol.DialogPacketBodies;
import org.lime.velocidialog.protocol.DialogProtocolMapping;
import org.lime.velocidialog.protocol.MinecraftVarInts;
import org.slf4j.LoggerFactory;

class VelocityDialogRuntimeAcceptanceTest {
    private static final String CONFIGURATION_SHOW_HEX =
            "120a0800047479706500106d696e6563726166743a6e6f7469636500";
    private static final String CONFIGURATION_CLEAR_HEX = "11";
    private static final String PLAY_INLINE_NOTICE_BODY_HEX =
            "000a0800047479706500106d696e6563726166743a6e6f7469636500";

    private static final CompoundBinaryTag RAW_NOTICE = CompoundBinaryTag.builder()
            .putString("type", "minecraft:notice")
            .build();

    private final List<VelocityDialogRuntime> runtimes = new ArrayList<>();

    @AfterEach
    void closeRuntimes() {
        runtimes.forEach(VelocityDialogRuntime::close);
    }

    @ParameterizedTest
    @ValueSource(ints = {771, 772, 773, 774})
    void writesExactConfigurationShowAndCloseBytes(final int protocol) {
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(protocol, ProtocolState.CONFIGURATION, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());

        runtime.showFromBridge(player.value(), Dialog.raw(RAW_NOTICE));
        runtime.closeFromBridge(player.value());

        assertEquals(2, connection.writes().size());
        assertHex(CONFIGURATION_SHOW_HEX, connection.writes().get(0));
        assertHex(CONFIGURATION_CLEAR_HEX, connection.writes().get(1));
    }

    @ParameterizedTest
    @CsvSource({
            "771, 8501, 8401",
            "772, 8501, 8401",
            "773, 8a01, 8901",
            "774, 8a01, 8901"
    })
    void writesExactPlayShowAndCloseBytes(
            final int protocol,
            final String showPacketId,
            final String clearPacketId) {
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(protocol, ProtocolState.PLAY, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());

        runtime.showFromBridge(player.value(), Dialog.raw(RAW_NOTICE));
        runtime.closeFromBridge(player.value());

        assertEquals(2, connection.writes().size());
        assertHex(showPacketId + PLAY_INLINE_NOTICE_BODY_HEX, connection.writes().get(0));
        assertHex(clearPacketId, connection.writes().get(1));
    }

    @Test
    void usesPlayPacketAfterFinishedUpdateBeforeClientAcknowledgement() {
        final QueuedExecutor eventLoop = new QueuedExecutor();
        final MinecraftConnection connection = new MinecraftConnection(eventLoop);
        final TestPlayer player = player(771, ProtocolState.CONFIGURATION, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());

        runtime.showFromBridge(player.value(), Dialog.raw(RAW_NOTICE));
        assertTrue(connection.writes().isEmpty());

        // Official Velocity switches the outbound encoder to PLAY immediately after writing
        // FinishedUpdate, while Player#getProtocolState remains CONFIGURATION until client ACK.
        connection.setOutboundState(ProtocolState.PLAY);
        eventLoop.runAll();

        assertEquals(1, connection.writes().size());
        assertHex("8501" + PLAY_INLINE_NOTICE_BODY_HEX, connection.writes().getFirst());
    }

    @Test
    void usesConfigurationPacketAfterStartUpdateBeforeClientAcknowledgement() {
        final QueuedExecutor eventLoop = new QueuedExecutor();
        final MinecraftConnection connection = new MinecraftConnection(eventLoop);
        final TestPlayer player = player(771, ProtocolState.PLAY, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());

        runtime.showFromBridge(player.value(), Dialog.raw(RAW_NOTICE));
        assertTrue(connection.writes().isEmpty());

        // StartUpdate is already ahead of this raw packet on the wire. Velocity's connection
        // state remains PLAY until the acknowledgement, but the client expects CONFIG packets.
        connection.setOutboundState(ProtocolState.CONFIGURATION);
        eventLoop.runAll();

        assertEquals(1, connection.writes().size());
        assertHex(CONFIGURATION_SHOW_HEX, connection.writes().getFirst());
    }

    @Test
    void queuesLoginOperationUntilConfigurationIsEntered() {
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.LOGIN, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());

        runtime.showFromBridge(player.value(), Dialog.raw(RAW_NOTICE));
        assertTrue(connection.writes().isEmpty());

        player.state().set(ProtocolState.CONFIGURATION);
        connection.setOutboundState(ProtocolState.CONFIGURATION);
        runtime.enteredConfiguration(player.value());

        assertEquals(1, connection.writes().size());
        assertHex(CONFIGURATION_SHOW_HEX, connection.writes().getFirst());
    }

    @Test
    void disconnectBeforeQueuedLoginDispatchCannotLeaveCallbackOrPendingWrite() {
        final QueuedExecutor eventLoop = new QueuedExecutor();
        final MinecraftConnection connection = new MinecraftConnection(eventLoop);
        final TestPlayer player = player(771, ProtocolState.LOGIN, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());

        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                VelocityDialogRuntimeAcceptanceTest::noticeWithAction,
                Duration.ofSeconds(30)
        ).toCompletableFuture();
        assertFalse(response.isDone());

        connection.setClosed(true);
        runtime.disconnected(player.value());
        assertThrows(CancellationException.class, response::join);
        eventLoop.runAll();

        connection.setClosed(false);
        player.state().set(ProtocolState.CONFIGURATION);
        connection.setOutboundState(ProtocolState.CONFIGURATION);
        runtime.enteredConfiguration(player.value());
        eventLoop.runAll();

        assertTrue(connection.writes().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {770, 777})
    void unsupportedProtocolIsSafeNoOp(final int protocol) {
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(protocol, ProtocolState.CONFIGURATION, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());

        runtime.showFromBridge(player.value(), Dialog.raw(RAW_NOTICE));
        runtime.closeFromBridge(player.value());

        assertTrue(connection.writes().isEmpty());
    }

    @Test
    void showAndAwaitCompletesFromOwnedPlayResponse() {
        final ControlledScheduler scheduler = new ControlledScheduler();
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.PLAY, connection);
        final VelocityDialogRuntime runtime = runtime(scheduler);
        final AtomicReference<DialogAction.CustomClickAction> action = new AtomicReference<>();

        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                callbackAction -> {
                    action.set(callbackAction);
                    return noticeWithAction(callbackAction);
                },
                Duration.ofSeconds(30)
        ).toCompletableFuture();

        assertFalse(response.isDone());
        assertEquals(1, connection.writes().size());
        assertNotNull(action.get());
        final CompoundBinaryTag payload = additions(action.get()).putString("answer", "lime");
        final ByteBuf packet = playCustomClick(player.value().getProtocolVersion(), action.get(), payload);
        try {
            final int readerIndex = packet.readerIndex();
            final int referenceCount = packet.refCnt();
            assertTrue(runtime.handlePlayClick(player.value(), packet));
            assertEquals(readerIndex, packet.readerIndex());
            assertEquals(referenceCount, packet.refCnt());
        } finally {
            packet.release();
        }

        final DialogResponseView view = response.join();
        assertEquals("lime", view.getText("answer"));
        assertEquals("lime", DialogInternals.decodeCompound(view.payload()).getString("answer"));
        assertTrue(scheduler.delayedTasks().stream().allMatch(ControlledTask::cancelled));
        assertCallbackUnregistered(runtime, scheduler, player, action.get());
    }

    @Test
    void receivedClickWinsAgainstTimeoutWhileCallbackTaskIsQueued() {
        final ControlledScheduler scheduler = new ControlledScheduler(true);
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.CONFIGURATION, connection);
        final VelocityDialogRuntime runtime = runtime(scheduler);
        final AtomicReference<DialogAction.CustomClickAction> action = new AtomicReference<>();

        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                callback -> {
                    action.set(callback);
                    return noticeWithAction(callback);
                },
                Duration.ofSeconds(1)
        ).toCompletableFuture();
        final ByteBuf body = DialogPacketBodiesTestSupport.customClickBody(
                action.get(), additions(action.get()).putString("answer", "before-deadline"));
        try {
            assertTrue(runtime.handleConfigurationClick(
                    new ConfigurationSessionHandler(player.value()), new ConfigurationPacket(body)));
        } finally {
            body.release();
        }

        assertFalse(response.isDone());
        scheduler.runDelayedTasks();
        assertFalse(response.isDone(), "a claimed click must be protected from its timeout task");
        scheduler.runImmediateTasks();
        assertEquals("before-deadline", response.join().getText("answer"));
    }

    @Test
    void shutdownCancelsClaimedButNotYetExecutedResponse() {
        final ControlledScheduler scheduler = new ControlledScheduler(true);
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.CONFIGURATION, connection);
        final VelocityDialogRuntime runtime = runtime(scheduler);
        final AtomicReference<DialogAction.CustomClickAction> action = new AtomicReference<>();
        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                callback -> {
                    action.set(callback);
                    return noticeWithAction(callback);
                },
                Duration.ofSeconds(30)
        ).toCompletableFuture();
        final ByteBuf body = DialogPacketBodiesTestSupport.customClickBody(
                action.get(), additions(action.get()));
        try {
            assertTrue(runtime.handleConfigurationClick(
                    player.value(), new ConfigurationPacket(body)));
        } finally {
            body.release();
        }

        runtime.close();
        assertThrows(CancellationException.class, response::join);
        scheduler.runImmediateTasks();
        assertThrows(CancellationException.class, response::join);
    }

    @Test
    void disconnectCancelsClaimedButNotYetExecutedResponse() {
        final ControlledScheduler scheduler = new ControlledScheduler(true);
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.CONFIGURATION, connection);
        final VelocityDialogRuntime runtime = runtime(scheduler);
        final AtomicReference<DialogAction.CustomClickAction> action = new AtomicReference<>();
        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                callback -> {
                    action.set(callback);
                    return noticeWithAction(callback);
                },
                Duration.ofSeconds(30)
        ).toCompletableFuture();
        final ByteBuf body = DialogPacketBodiesTestSupport.customClickBody(
                action.get(), additions(action.get()));
        try {
            assertTrue(runtime.handleConfigurationClick(
                    player.value(), new ConfigurationPacket(body)));
        } finally {
            body.release();
        }

        runtime.disconnected(player.value());
        assertThrows(CancellationException.class, response::join);
        scheduler.runImmediateTasks();
        assertThrows(CancellationException.class, response::join);
    }

    @Test
    void asynchronousPacketWriteFailureFailsAwaitingStage() {
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.CONFIGURATION, connection);
        final VelocityDialogRuntime runtime = runtime(new ControlledScheduler());
        connection.failNextWrite(new IOException("simulated channel failure"));

        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                VelocityDialogRuntimeAcceptanceTest::noticeWithAction,
                Duration.ofSeconds(30)
        ).toCompletableFuture();

        final CompletionException failure = assertThrows(CompletionException.class, response::join);
        assertTrue(failure.getCause() instanceof IOException);
    }

    @Test
    void showAndAwaitTimesOutWithoutSleeping() {
        final ControlledScheduler scheduler = new ControlledScheduler();
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.PLAY, connection);
        final VelocityDialogRuntime runtime = runtime(scheduler);
        final AtomicReference<DialogAction.CustomClickAction> action = new AtomicReference<>();

        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                callback -> {
                    action.set(callback);
                    return noticeWithAction(callback);
                },
                Duration.ofSeconds(1)
        ).toCompletableFuture();
        assertFalse(response.isDone());

        scheduler.runDelayedTasks();

        final CompletionException failure = assertThrows(CompletionException.class, response::join);
        assertTrue(failure.getCause() instanceof TimeoutException);
        assertCallbackUnregistered(runtime, scheduler, player, action.get());
    }

    @Test
    void showAndAwaitIsCancelledOnDisconnect() {
        final ControlledScheduler scheduler = new ControlledScheduler();
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.PLAY, connection);
        final VelocityDialogRuntime runtime = runtime(scheduler);
        final AtomicReference<DialogAction.CustomClickAction> action = new AtomicReference<>();

        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                callback -> {
                    action.set(callback);
                    return noticeWithAction(callback);
                },
                Duration.ofSeconds(30)
        ).toCompletableFuture();
        assertFalse(response.isDone());

        runtime.disconnected(player.value());

        assertThrows(CancellationException.class, response::join);
        assertTrue(scheduler.delayedTasks().stream().allMatch(ControlledTask::cancelled));
        assertCallbackUnregistered(runtime, scheduler, player, action.get());
    }

    @ParameterizedTest
    @EnumSource(ExternalCompletion.class)
    void externalCompletionAlwaysUnregistersOwnedCallback(
            final ExternalCompletion completion
    ) {
        final ControlledScheduler scheduler = new ControlledScheduler();
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final TestPlayer player = player(771, ProtocolState.PLAY, connection);
        final VelocityDialogRuntime runtime = runtime(scheduler);
        final AtomicReference<DialogAction.CustomClickAction> action = new AtomicReference<>();

        final CompletableFuture<DialogResponseView> response = runtime.showAndAwait(
                player.value(),
                callback -> {
                    action.set(callback);
                    return noticeWithAction(callback);
                },
                Duration.ofSeconds(30)
        ).toCompletableFuture();

        completion.complete(response);
        assertTrue(response.isDone());
        assertTrue(scheduler.delayedTasks().stream().allMatch(ControlledTask::cancelled));
        final int scheduledBeforeReplay = scheduler.taskCount();

        final ByteBuf packet = playCustomClick(
                player.value().getProtocolVersion(), action.get(), additions(action.get()));
        try {
            assertTrue(runtime.handlePlayClick(player.value(), packet),
                    "the reserved callback identifier remains owned by Velocidialog");
            assertEquals(scheduledBeforeReplay, scheduler.taskCount(),
                    "an unregistered callback must not schedule user code");
        } finally {
            packet.release();
        }
    }

    private VelocityDialogRuntime runtime(final Scheduler scheduler) {
        final VelocityDialogRuntime runtime = new VelocityDialogRuntime(
                proxy(scheduler),
                this,
                LoggerFactory.getLogger(VelocityDialogRuntimeAcceptanceTest.class)
        );
        runtimes.add(runtime);
        return runtime;
    }

    private static Dialog noticeWithAction(final DialogAction.CustomClickAction action) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Proxy setup")).build())
                .type(DialogType.notice(ActionButton.builder(Component.text("Continue"))
                        .action(action)
                        .build())));
    }

    private static CompoundBinaryTag additions(final DialogAction.CustomClickAction action) {
        return DialogInternals.decodeCompound(Objects.requireNonNull(action.additions()));
    }

    private static ByteBuf playCustomClick(
            final ProtocolVersion protocol,
            final DialogAction.CustomClickAction action,
            final CompoundBinaryTag payload
    ) {
        final ByteBuf packet = Unpooled.buffer();
        MinecraftVarInts.write(packet, DialogProtocolMapping.forProtocol(
                protocol.getProtocol()).playCustomClick());
        DialogPacketBodies.writeCustomClick(packet, new CustomClickPacketBody(
                action.id().asString(), payload));
        return packet;
    }

    private static void assertCallbackUnregistered(
            final VelocityDialogRuntime runtime,
            final ControlledScheduler scheduler,
            final TestPlayer player,
            final DialogAction.CustomClickAction action
    ) {
        final int scheduledBeforeReplay = scheduler.taskCount();
        final ByteBuf replay = playCustomClick(
                player.value().getProtocolVersion(), action, additions(action));
        try {
            assertTrue(runtime.handlePlayClick(player.value(), replay));
            assertEquals(scheduledBeforeReplay, scheduler.taskCount(),
                    "an unregistered callback must not schedule user code");
        } finally {
            replay.release();
        }
    }

    private static void assertHex(final String expected, final byte[] actual) {
        assertEquals(expected, ByteBufUtil.hexDump(actual));
    }

    private static TestPlayer player(
            final int protocol,
            final ProtocolState initialState,
            final MinecraftConnection connection
    ) {
        final UUID id = UUID.randomUUID();
        final AtomicReference<ProtocolState> state = new AtomicReference<>(initialState);
        connection.setOutboundState(initialState);
        final ConnectedPlayer player = (ConnectedPlayer) Proxy.newProxyInstance(
                VelocityDialogRuntimeAcceptanceTest.class.getClassLoader(),
                new Class<?>[]{ConnectedPlayer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getConnection" -> connection;
                    case "getUniqueId" -> id;
                    case "getProtocolVersion" -> ProtocolVersion.getProtocolVersion(protocol);
                    case "getProtocolState" -> state.get();
                    case "getUsername" -> "RuntimePlayer";
                    case "toString" -> "RuntimePlayer[" + id + ']';
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
        return new TestPlayer(player, state);
    }

    private static ProxyServer proxy(final Scheduler scheduler) {
        return (ProxyServer) Proxy.newProxyInstance(
                VelocityDialogRuntimeAcceptanceTest.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getScheduler" -> scheduler;
                    case "toString" -> "RuntimeProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private record TestPlayer(
            ConnectedPlayer value,
            AtomicReference<ProtocolState> state
    ) {
    }

    private enum ExternalCompletion {
        NORMAL {
            @Override
            void complete(final CompletableFuture<DialogResponseView> response) {
                response.complete(null);
            }
        },
        EXCEPTIONAL {
            @Override
            void complete(final CompletableFuture<DialogResponseView> response) {
                response.completeExceptionally(new IOException("completed by caller"));
            }
        },
        CANCELLED {
            @Override
            void complete(final CompletableFuture<DialogResponseView> response) {
                response.cancel(false);
            }
        };

        abstract void complete(CompletableFuture<DialogResponseView> response);
    }

    public record ConfigurationPacket(ByteBuf content) {
    }

    private static final class ConfigurationSessionHandler {
        private final Object player;

        private ConfigurationSessionHandler(final Object player) {
            this.player = player;
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(final Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    private static final class ControlledScheduler implements Scheduler {
        private final List<ControlledTask> tasks = new ArrayList<>();
        private final List<ControlledTask> delayedTasks = new ArrayList<>();
        private final List<ControlledTask> immediateTasks = new ArrayList<>();
        private final boolean deferImmediate;

        ControlledScheduler() {
            this(false);
        }

        ControlledScheduler(final boolean deferImmediate) {
            this.deferImmediate = deferImmediate;
        }

        @Override
        public TaskBuilder buildTask(final Object plugin, final Runnable runnable) {
            return new ControlledTaskBuilder(this, plugin, runnable, null);
        }

        @Override
        public TaskBuilder buildTask(
                final Object plugin,
                final Consumer<ScheduledTask> consumer
        ) {
            return new ControlledTaskBuilder(this, plugin, null, consumer);
        }

        @Override
        public Collection<ScheduledTask> tasksByPlugin(final Object plugin) {
            return tasks.stream()
                    .filter(task -> task.plugin() == plugin)
                    .map(task -> (ScheduledTask) task)
                    .toList();
        }

        void add(final ControlledTask task, final boolean delayed) {
            tasks.add(task);
            if (delayed) {
                delayedTasks.add(task);
            } else if (deferImmediate) {
                immediateTasks.add(task);
            } else {
                task.runNow();
            }
        }

        List<ControlledTask> delayedTasks() {
            return List.copyOf(delayedTasks);
        }

        int taskCount() {
            return tasks.size();
        }

        void runDelayedTasks() {
            List.copyOf(delayedTasks).forEach(ControlledTask::runNow);
        }

        void runImmediateTasks() {
            List.copyOf(immediateTasks).forEach(ControlledTask::runNow);
        }
    }

    private static final class ControlledTaskBuilder implements Scheduler.TaskBuilder {
        private final ControlledScheduler scheduler;
        private final Object plugin;
        private final Runnable runnable;
        private final Consumer<ScheduledTask> consumer;
        private long delayNanos;
        private boolean repeating;

        ControlledTaskBuilder(
                final ControlledScheduler scheduler,
                final Object plugin,
                final Runnable runnable,
                final Consumer<ScheduledTask> consumer
        ) {
            this.scheduler = scheduler;
            this.plugin = plugin;
            this.runnable = runnable;
            this.consumer = consumer;
        }

        @Override
        public Scheduler.TaskBuilder delay(final long time, final TimeUnit unit) {
            delayNanos = unit.toNanos(time);
            return this;
        }

        @Override
        public Scheduler.TaskBuilder repeat(final long time, final TimeUnit unit) {
            repeating = true;
            return this;
        }

        @Override
        public Scheduler.TaskBuilder clearDelay() {
            delayNanos = 0L;
            return this;
        }

        @Override
        public Scheduler.TaskBuilder clearRepeat() {
            repeating = false;
            return this;
        }

        @Override
        public ScheduledTask schedule() {
            final ControlledTask task = new ControlledTask(plugin, repeating);
            task.action(runnable != null ? runnable : () -> consumer.accept(task));
            scheduler.add(task, delayNanos > 0L);
            return task;
        }
    }

    private static final class ControlledTask implements ScheduledTask {
        private final Object plugin;
        private final boolean repeating;
        private Runnable action;
        private TaskStatus status = TaskStatus.SCHEDULED;

        ControlledTask(final Object plugin, final boolean repeating) {
            this.plugin = plugin;
            this.repeating = repeating;
        }

        void action(final Runnable action) {
            this.action = action;
        }

        void runNow() {
            if (status == TaskStatus.CANCELLED || (!repeating && status == TaskStatus.FINISHED)) {
                return;
            }
            action.run();
            if (!repeating && status != TaskStatus.CANCELLED) {
                status = TaskStatus.FINISHED;
            }
        }

        boolean cancelled() {
            return status == TaskStatus.CANCELLED;
        }

        @Override
        public Object plugin() {
            return plugin;
        }

        @Override
        public TaskStatus status() {
            return status;
        }

        @Override
        public void cancel() {
            status = TaskStatus.CANCELLED;
        }
    }
}
