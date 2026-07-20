package org.lime.velocidialog.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.scheduler.TaskStatus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.event.ClickCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.protocol.CustomClickPacketBody;
import org.lime.velocidialog.protocol.DialogPacketBodies;
import org.slf4j.LoggerFactory;

class CallbackRegistryTest {
    private final ImmediateScheduler scheduler = new ImmediateScheduler();
    private final ProxyServer proxy = proxy(scheduler);
    private final CallbackRegistry registry = new CallbackRegistry(
            proxy, this, LoggerFactory.getLogger(CallbackRegistryTest.class));

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void consumesOnceAndPreservesPacketBuffer() {
        final Player player = player(UUID.randomUUID(), 771);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> text = new AtomicReference<>();
        final CallbackRegistry.Registration registration = registry.register((response, audience) -> {
            calls.incrementAndGet();
            text.set(response.getText("answer"));
            assertSame(player, audience);
        }, 1, Duration.ofMinutes(1), player, ignored -> { });

        final CompoundBinaryTag response = additions(registration.action())
                .putString("answer", "lime");
        final ByteBuf body = body(CallbackRegistry.CALLBACK_IDENTIFIER, response);
        try {
            final int index = body.readerIndex();
            final int refCount = body.refCnt();
            assertTrue(registry.handleConfiguration(player, new ConfigurationPacket(body)));
            assertEquals(index, body.readerIndex());
            assertEquals(refCount, body.refCnt());
            assertEquals(1, calls.get());
            assertEquals("lime", text.get());

            assertTrue(registry.handleConfiguration(player, new ConfigurationPacket(body)));
            assertEquals(1, calls.get(), "a one-use callback must reject replay");
        } finally {
            body.release();
        }
    }

    @Test
    void forwardsForeignIdentifierAndConsumesMalformedOwnedPayload() {
        final Player player = player(UUID.randomUUID(), 771);
        final ByteBuf foreign = body("other:callback", CompoundBinaryTag.empty());
        final ByteBuf malformed = Unpooled.buffer();
        try {
            DialogPacketBodies.writeCustomClick(malformed,
                    new CustomClickPacketBody(CallbackRegistry.CALLBACK_IDENTIFIER, null));
            malformed.setByte(malformed.writerIndex() - 1, 12); // impossible root type

            assertFalse(registry.handleConfiguration(player, new ConfigurationPacket(foreign)));
            assertTrue(registry.handleConfiguration(player, new ConfigurationPacket(malformed)));
        } finally {
            foreign.release();
            malformed.release();
        }
    }

    @Test
    void oversizedForeignPacketsRemainPassthroughButReservedPacketsAreConsumed() {
        final Player player = player(UUID.randomUUID(), 771);
        final ByteBuf configurationForeign = body("other:callback", CompoundBinaryTag.empty());
        final ByteBuf configurationReserved = body(
                CallbackRegistry.CALLBACK_IDENTIFIER, CompoundBinaryTag.empty());
        final ByteBuf playForeign = Unpooled.buffer();
        final ByteBuf playReserved = Unpooled.buffer();
        try {
            configurationForeign.writeZero(65_537 - configurationForeign.readableBytes());
            configurationReserved.writeZero(65_537 - configurationReserved.readableBytes());

            org.lime.velocidialog.protocol.MinecraftVarInts.write(playForeign, 0x41);
            DialogPacketBodies.writeCustomClick(playForeign,
                    new CustomClickPacketBody("other:callback", CompoundBinaryTag.empty()));
            playForeign.writeZero(65_537 - playForeign.readableBytes());

            org.lime.velocidialog.protocol.MinecraftVarInts.write(playReserved, 0x41);
            DialogPacketBodies.writeCustomClick(playReserved, new CustomClickPacketBody(
                    CallbackRegistry.CALLBACK_IDENTIFIER, CompoundBinaryTag.empty()));
            playReserved.writeZero(65_537 - playReserved.readableBytes());

            assertFalse(registry.handleConfiguration(
                    player, new ConfigurationPacket(configurationForeign)));
            assertTrue(registry.handleConfiguration(
                    player, new ConfigurationPacket(configurationReserved)));
            assertFalse(registry.handlePlay(player, playForeign));
            assertTrue(registry.handlePlay(player, playReserved));
        } finally {
            configurationForeign.release();
            configurationReserved.release();
            playForeign.release();
            playReserved.release();
        }
    }

    @Test
    void unlimitedCallbackCanRunConcurrently() throws Exception {
        final Player player = player(UUID.randomUUID(), 771);
        final AtomicInteger calls = new AtomicInteger();
        final CallbackRegistry.Registration registration = registry.register(
                (response, audience) -> calls.incrementAndGet(),
                ClickCallback.UNLIMITED_USES,
                Duration.ofMinutes(1),
                player,
                ignored -> { });

        final int invocations = 32;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(invocations);
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < invocations; index++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        final ByteBuf body = body(CallbackRegistry.CALLBACK_IDENTIFIER,
                                additions(registration.action()));
                        try {
                            registry.handleConfiguration(player, new ConfigurationPacket(body));
                        } finally {
                            body.release();
                        }
                    } finally {
                        finished.countDown();
                    }
                    return null;
                });
            }
            start.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(invocations, calls.get());
        assertEquals(1, registry.size());
    }

    @Test
    void oneUseCallbackIsClaimedAtomically() throws Exception {
        final Player player = player(UUID.randomUUID(), 771);
        final AtomicInteger calls = new AtomicInteger();
        final CallbackRegistry.Registration registration = registry.register(
                (response, audience) -> calls.incrementAndGet(),
                1,
                Duration.ofMinutes(1),
                player,
                ignored -> { });

        final int invocations = 24;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(invocations);
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < invocations; index++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        final ByteBuf body = body(CallbackRegistry.CALLBACK_IDENTIFIER,
                                additions(registration.action()));
                        try {
                            registry.handleConfiguration(player, new ConfigurationPacket(body));
                        } finally {
                            body.release();
                        }
                    } finally {
                        finished.countDown();
                    }
                    return null;
                });
            }
            start.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
        assertEquals(0, registry.size());
    }

    @Test
    void consumesPlayPacketWithoutChangingItsIndicesOrRefCount() {
        final Player player = player(UUID.randomUUID(), 771);
        final AtomicInteger calls = new AtomicInteger();
        final CallbackRegistry.Registration registration = registry.register(
                (response, audience) -> calls.incrementAndGet(),
                1,
                Duration.ofMinutes(1),
                player,
                ignored -> { });
        final ByteBuf packet = Unpooled.buffer();
        try {
            org.lime.velocidialog.protocol.MinecraftVarInts.write(packet, 0x41);
            DialogPacketBodies.writeCustomClick(packet, new CustomClickPacketBody(
                    CallbackRegistry.CALLBACK_IDENTIFIER,
                    additions(registration.action())));
            final int index = packet.readerIndex();
            final int refCount = packet.refCnt();

            assertTrue(registry.handlePlay(player, packet));
            assertEquals(index, packet.readerIndex());
            assertEquals(refCount, packet.refCnt());
            assertEquals(1, calls.get());
        } finally {
            packet.release();
        }
    }

    @Test
    void disconnectCancelsOnlyCallbacksOwnedByPlayer() {
        final Player first = player(UUID.randomUUID(), 771);
        final Player second = player(UUID.randomUUID(), 771);
        final AtomicReference<Throwable> firstCancellation = new AtomicReference<>();
        final AtomicReference<Throwable> secondCancellation = new AtomicReference<>();
        registry.register((response, audience) -> { }, 1, Duration.ofMinutes(1), first,
                firstCancellation::set);
        registry.register((response, audience) -> { }, 1, Duration.ofMinutes(1), second,
                secondCancellation::set);

        registry.disconnect(first);

        assertEquals(1, registry.size());
        assertTrue(firstCancellation.get() instanceof java.util.concurrent.CancellationException);
        assertEquals(null, secondCancellation.get());
    }

    @Test
    void callbackOwnershipUsesPlayerSessionIdentityInsteadOfUuid() {
        final UUID id = UUID.randomUUID();
        final Player original = player(id, 771);
        final Player replacement = player(id, 771);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<Throwable> cancellation = new AtomicReference<>();
        final CallbackRegistry.Registration registration = registry.register(
                (response, audience) -> calls.incrementAndGet(),
                1,
                Duration.ofMinutes(1),
                original,
                cancellation::set);
        final ByteBuf response = body(
                CallbackRegistry.CALLBACK_IDENTIFIER, additions(registration.action()));
        try {
            assertTrue(registry.handleConfiguration(
                    replacement, new ConfigurationPacket(response)));
        } finally {
            response.release();
        }

        registry.disconnect(replacement);
        assertEquals(0, calls.get());
        assertEquals(1, registry.size());
        assertEquals(null, cancellation.get());

        registry.disconnect(original);
        assertEquals(0, registry.size());
        assertTrue(cancellation.get() instanceof java.util.concurrent.CancellationException);
    }

    @Test
    void closeAndRegisterCannotLeaveCallbackBehind() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int attempt = 0; attempt < 64; attempt++) {
                final CallbackRegistry candidate = new CallbackRegistry(
                        proxy, this, LoggerFactory.getLogger(CallbackRegistryTest.class));
                final CountDownLatch start = new CountDownLatch(1);
                final AtomicInteger cancellations = new AtomicInteger();
                final var registration = executor.submit(() -> {
                    start.await();
                    try {
                        candidate.register(
                                (response, audience) -> { },
                                1,
                                Duration.ofMinutes(1),
                                player(UUID.randomUUID(), 771),
                                ignored -> cancellations.incrementAndGet());
                        return (Throwable) null;
                    } catch (final Throwable failure) {
                        return failure;
                    }
                });
                final var close = executor.submit(() -> {
                    start.await();
                    candidate.close();
                    return null;
                });

                start.countDown();
                final Throwable registrationFailure = registration.get(5, TimeUnit.SECONDS);
                close.get(5, TimeUnit.SECONDS);

                assertTrue(registrationFailure == null
                        || registrationFailure instanceof IllegalStateException);
                assertEquals(0, candidate.size());
                assertEquals(registrationFailure == null ? 1 : 0, cancellations.get());
            }
        }
    }

    @Test
    void publicCallbackRegistrationIsImmediateAndGlobal() {
        final Player first = player(UUID.randomUUID(), 771);
        final Player second = player(UUID.randomUUID(), 771);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<Player> audience = new AtomicReference<>();
        final DialogInternals.CallbackRegistrar registrar = (callback, options) ->
                registry.register(callback, options).action();
        DialogInternals.installCallbackRegistrar(registrar);
        try {
            final DialogAction.CustomClickAction action = DialogAction.customClick(
                    (response, clickedAudience) -> {
                        calls.incrementAndGet();
                        audience.set((Player) clickedAudience);
                    },
                    ClickCallback.Options.builder()
                            .uses(1)
                            .lifetime(Duration.ofMinutes(1))
                            .build());

            assertEquals(1, registry.size(), "public API registration must be immediate");
            registry.disconnect(first);
            assertEquals(1, registry.size(), "owner-null callbacks survive unrelated disconnects");

            final ByteBuf response = body(
                    CallbackRegistry.CALLBACK_IDENTIFIER,
                    additions(action).putString("answer", "global"));
            try {
                assertTrue(registry.handleConfiguration(second, new ConfigurationPacket(response)));
            } finally {
                response.release();
            }

            assertEquals(1, calls.get());
            assertSame(second, audience.get());
            assertEquals(0, registry.size());
        } finally {
            DialogInternals.clearCallbackRegistrar(registrar);
        }
    }

    private static CompoundBinaryTag additions(final DialogAction.CustomClickAction action) {
        return DialogInternals.decodeCompound(Objects.requireNonNull(action.additions()));
    }

    private static ByteBuf body(final String id, final CompoundBinaryTag payload) {
        final ByteBuf body = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(body, new CustomClickPacketBody(id, payload));
        return body;
    }

    private static Player player(final UUID id, final int protocol) {
        return (Player) Proxy.newProxyInstance(
                CallbackRegistryTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getProtocolVersion" -> ProtocolVersion.getProtocolVersion(protocol);
                    case "toString" -> "TestPlayer[" + id + ']';
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static ProxyServer proxy(final Scheduler scheduler) {
        return (ProxyServer) Proxy.newProxyInstance(
                CallbackRegistryTest.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getScheduler" -> scheduler;
                    case "toString" -> "TestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
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
        return 0;
    }

    public record ConfigurationPacket(ByteBuf content) {
    }

    private static final class ImmediateScheduler implements Scheduler {
        @Override
        public TaskBuilder buildTask(final Object plugin, final Runnable runnable) {
            return new Builder(plugin, runnable);
        }

        @Override
        public TaskBuilder buildTask(
                final Object plugin,
                final Consumer<ScheduledTask> consumer) {
            final AtomicReference<ScheduledTask> reference = new AtomicReference<>();
            return new Builder(plugin, () -> consumer.accept(reference.get())) {
                @Override
                public ScheduledTask schedule() {
                    final ScheduledTask task = super.newTask();
                    reference.set(task);
                    super.run();
                    return task;
                }
            };
        }

        @Override
        public Collection<ScheduledTask> tasksByPlugin(final Object plugin) {
            return List.of();
        }

        private static class Builder implements TaskBuilder {
            private final Object plugin;
            private final Runnable runnable;

            Builder(final Object plugin, final Runnable runnable) {
                this.plugin = plugin;
                this.runnable = runnable;
            }

            @Override
            public TaskBuilder delay(final long time, final TimeUnit unit) {
                return this;
            }

            @Override
            public TaskBuilder repeat(final long time, final TimeUnit unit) {
                return this;
            }

            @Override
            public TaskBuilder clearDelay() {
                return this;
            }

            @Override
            public TaskBuilder clearRepeat() {
                return this;
            }

            @Override
            public ScheduledTask schedule() {
                final ScheduledTask task = newTask();
                run();
                return task;
            }

            ScheduledTask newTask() {
                return new ScheduledTask() {
                    @Override
                    public Object plugin() {
                        return plugin;
                    }

                    @Override
                    public TaskStatus status() {
                        return TaskStatus.FINISHED;
                    }

                    @Override
                    public void cancel() {
                    }
                };
            }

            void run() {
                runnable.run();
            }
        }
    }
}
