package org.lime.velocidialog.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import io.netty.buffer.ByteBufUtil;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.runtime.VelocityDialogRuntime;
import org.slf4j.Logger;

class Velocity4RuntimeCompatibilityTest {
    private static final String CONFIG_BODY =
            "120a0800047479706500106d696e6563726166743a6e6f7469636500";
    private static final String PLAY_BODY =
            "000a0800047479706500106d696e6563726166743a6e6f7469636500";
    private static final CompoundBinaryTag NOTICE = CompoundBinaryTag.builder()
            .putString("type", "minecraft:notice")
            .build();

    @ParameterizedTest
    @CsvSource({"775, 8c01, 8b01", "776, 8c01, 8b01"})
    void sendsProtocol26DialogsWithVelocity4Adventure5(
            final int protocol,
            final String playShowId,
            final String playClearId) {
        final MinecraftConnection connection = new MinecraftConnection(Runnable::run);
        final AtomicReference<ProtocolState> state = new AtomicReference<>(ProtocolState.CONFIGURATION);
        final ConnectedPlayer player = player(protocol, state, connection);
        assertEquals(protocol, player.getProtocolVersion().getProtocol(),
                "Velocity 4 must expose the requested protocol version");
        final AtomicReference<Throwable> asynchronousFailure = new AtomicReference<>();
        final VelocityDialogRuntime runtime = new VelocityDialogRuntime(
                proxy(), this, logger(asynchronousFailure));
        try {
            runtime.showFromBridge(player, Dialog.raw(NOTICE));
            runtime.closeFromBridge(player);
            state.set(ProtocolState.PLAY);
            connection.setOutboundState(ProtocolState.PLAY);
            runtime.showFromBridge(player, Dialog.raw(NOTICE));
            runtime.closeFromBridge(player);

            assertNull(asynchronousFailure.get(), "Runtime rejected a Velocity 4 dialog write");
            assertEquals(CONFIG_BODY, ByteBufUtil.hexDump(connection.writes().get(0)));
            assertEquals("11", ByteBufUtil.hexDump(connection.writes().get(1)));
            assertEquals(playShowId + PLAY_BODY,
                    ByteBufUtil.hexDump(connection.writes().get(2)));
            assertEquals(playClearId, ByteBufUtil.hexDump(connection.writes().get(3)));
        } finally {
            runtime.close();
        }
    }

    private static ConnectedPlayer player(
            final int protocol,
            final AtomicReference<ProtocolState> state,
            final MinecraftConnection connection) {
        final UUID id = UUID.randomUUID();
        return (ConnectedPlayer) Proxy.newProxyInstance(
                Velocity4RuntimeCompatibilityTest.class.getClassLoader(),
                new Class<?>[]{ConnectedPlayer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getConnection" -> connection;
                    case "getUniqueId" -> id;
                    case "getProtocolVersion" -> ProtocolVersion.getProtocolVersion(protocol);
                    case "getProtocolState" -> state.get();
                    case "getUsername" -> "Velocity4Player";
                    case "toString" -> "Velocity4Player[" + id + ']';
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static ProxyServer proxy() {
        return (ProxyServer) Proxy.newProxyInstance(
                Velocity4RuntimeCompatibilityTest.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toString" -> "Velocity4Proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Logger logger(final AtomicReference<Throwable> failure) {
        return (Logger) Proxy.newProxyInstance(
                Velocity4RuntimeCompatibilityTest.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("warn") && arguments != null) {
                        for (final Object argument : arguments) {
                            captureThrowable(argument, failure);
                        }
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static void captureThrowable(
            final Object value,
            final AtomicReference<Throwable> failure) {
        if (value instanceof Throwable throwable) {
            failure.compareAndSet(null, throwable);
        } else if (value instanceof Object[] values) {
            for (final Object element : values) {
                captureThrowable(element, failure);
            }
        }
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
}
