package org.lime.velocidialog.inject;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.ExceptionMethod;
import net.bytebuddy.implementation.StubMethod;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.dialog.DialogLike;
import org.junit.jupiter.api.Test;

class VelocityInjectorIntegrationTest {
    private static final String CONFIG_HANDLER =
            "com.velocitypowered.proxy.connection.client.ClientConfigSessionHandler";
    private static final String CONFIG_PACKET =
            "com.velocitypowered.proxy.protocol.packet.ServerboundCustomClickActionPacket";
    private static final String PLAY_HANDLER =
            "com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler";

    @Test
    void installsOnVelocityEntryPointsAndRestoresOriginalBodies() throws Exception {
        final ClassLoader loader = Player.class.getClassLoader();
        final Class<?> packetType = definePacket(loader);
        final Class<?> configType = defineConfigHandler(loader);
        final Class<?> playType = definePlayHandler(loader);
        final Object configHandler = configType.getConstructor().newInstance();
        final Object playHandler = playType.getConstructor().newInstance();
        assertTrue(Player.class.getMethod("showDialog", DialogLike.class).getDeclaringClass()
                == Player.class || Player.class.getMethod("showDialog", DialogLike.class)
                .getDeclaringClass() == Audience.class);
        assertTrue(Player.class.getMethod("closeDialog").getDeclaringClass()
                == Player.class || Player.class.getMethod("closeDialog").getDeclaringClass()
                == Audience.class);
        final Player player = concretePlayer(loader);
        configType.getField("player").set(configHandler, player);
        playType.getField("player").set(playHandler, player);

        final Object packet = packetType.getConstructor().newInstance();
        final ByteBuf buffer = Unpooled.buffer(0);
        final DialogLike dialog = (DialogLike) Proxy.newProxyInstance(
                loader, new Class<?>[] {DialogLike.class}, VelocityInjectorIntegrationTest::defaultInvocation);
        final Audience nonPlayerAudience = (Audience) Proxy.newProxyInstance(
                loader,
                new Class<?>[] {Audience.class},
                (proxy, method, arguments) -> method.isDefault()
                        ? InvocationHandler.invokeDefault(proxy, method, arguments)
                        : defaultValue(method.getReturnType()));
        final AtomicReference<Object> shownPlayer = new AtomicReference<>();
        final AtomicReference<Object> shownDialog = new AtomicReference<>();
        final AtomicReference<Object> closedPlayer = new AtomicReference<>();
        final AtomicInteger showCalls = new AtomicInteger();
        final AtomicInteger closeCalls = new AtomicInteger();
        final AtomicInteger configurationClicks = new AtomicInteger();
        final AtomicInteger playClicks = new AtomicInteger();

        final VelocityInjector injector = VelocityInjector.install(
                (actualPlayer, actualDialog) -> {
                    showCalls.incrementAndGet();
                    shownPlayer.set(actualPlayer);
                    shownDialog.set(actualDialog);
                },
                actualPlayer -> {
                    closeCalls.incrementAndGet();
                    closedPlayer.set(actualPlayer);
                },
                (actualPlayer, actualPacket) -> {
                    assertSame(configHandler, actualPlayer);
                    assertSame(packet, actualPacket);
                    configurationClicks.incrementAndGet();
                    return true;
                },
                (actualPlayer, actualBuffer) -> {
                    assertSame(player, actualPlayer);
                    assertSame(buffer, actualBuffer);
                    playClicks.incrementAndGet();
                    return true;
                });

        try {
            player.showDialog(dialog);
            player.closeDialog();
            final Audience audience = player;
            audience.showDialog(dialog);
            audience.closeDialog();
            final ForwardingAudience forwarding = () -> List.of(player);
            forwarding.showDialog(dialog);
            forwarding.closeDialog();
            nonPlayerAudience.showDialog(dialog);
            nonPlayerAudience.closeDialog();
            assertSame(player, shownPlayer.get());
            assertSame(dialog, shownDialog.get());
            assertSame(player, closedPlayer.get());
            assertEquals(3, showCalls.get());
            assertEquals(3, closeCalls.get());

            final Method configHandle = packetType.getMethod("handle", Object.class);
            assertTrue((boolean) configHandle.invoke(packet, configHandler));
            playType.getMethod("handleUnknown", ByteBuf.class).invoke(playHandler, buffer);
            assertEquals(1, configurationClicks.get());
            assertEquals(1, playClicks.get());
        } finally {
            injector.close();
            buffer.release();
        }

        player.showDialog(dialog);
        player.closeDialog();
        assertEquals(3, showCalls.get());
        assertEquals(3, closeCalls.get());
        assertEquals(1, configurationClicks.get());
        assertEquals(1, playClicks.get());
        assertOriginalAssertion(packetType.getMethod("handle", Object.class), packet, configHandler);
        assertOriginalAssertion(
                playType.getMethod("handleUnknown", ByteBuf.class), playHandler, Unpooled.EMPTY_BUFFER);
    }

    private static Class<?> definePacket(final ClassLoader loader) {
        return new ByteBuddy()
                .subclass(Object.class)
                .name(CONFIG_PACKET)
                .defineMethod("handle", boolean.class, Visibility.PUBLIC)
                .withParameters(Object.class)
                .intercept(ExceptionMethod.throwing(AssertionError.class))
                .make()
                .load(loader, ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
    }

    private static Class<?> defineConfigHandler(final ClassLoader loader) {
        return new ByteBuddy()
                .subclass(Object.class)
                .name(CONFIG_HANDLER)
                .defineField("player", Object.class, Visibility.PUBLIC)
                .make()
                .load(loader, ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
    }

    private static Class<?> definePlayHandler(final ClassLoader loader) {
        return new ByteBuddy()
                .subclass(Object.class)
                .name(PLAY_HANDLER)
                .defineField("player", Object.class, Visibility.PUBLIC)
                .defineMethod("handleUnknown", void.class, Visibility.PUBLIC)
                .withParameters(ByteBuf.class)
                .intercept(ExceptionMethod.throwing(AssertionError.class))
                .make()
                .load(loader, ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
    }

    private static Player concretePlayer(final ClassLoader loader) throws ReflectiveOperationException {
        return (Player) new ByteBuddy()
                .subclass(Object.class)
                .implement(Player.class)
                .method(isAbstract())
                .intercept(StubMethod.INSTANCE)
                .make()
                .load(loader, ClassLoadingStrategy.Default.INJECTION)
                .getLoaded()
                .getConstructor()
                .newInstance();
    }

    private static Object defaultInvocation(
            final Object proxy, final Method method, final Object[] arguments) {
        return defaultValue(method.getReturnType());
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return 0;
    }

    private static void assertOriginalAssertion(
            final Method method, final Object target, final Object argument) {
        final InvocationTargetException invocation = assertThrows(
                InvocationTargetException.class, () -> method.invoke(target, argument));
        assertTrue(invocation.getCause() instanceof AssertionError);
    }
}
