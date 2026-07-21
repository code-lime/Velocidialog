package org.lime.velocidialog.runtime;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.network.ProtocolState;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import io.netty.util.concurrent.Future;
import org.lime.velocidialog.inject.InjectionException;

/** Checked reflective access to the small portion of Velocity internals needed for raw writes. */
final class VelocityConnectionAccess {
    private static final String CONNECTED_PLAYER =
            "com.velocitypowered.proxy.connection.client.ConnectedPlayer";
    private static final String MINECRAFT_CONNECTION =
            "com.velocitypowered.proxy.connection.MinecraftConnection";
    private static final String MINECRAFT_ENCODER =
            "com.velocitypowered.proxy.protocol.netty.MinecraftEncoder";

    private final Class<?> connectedPlayer;
    private final Method getConnection;
    private final Method eventLoop;
    private final Method write;
    private final Method isClosed;
    private final Method getChannel;
    private final Class<? extends ChannelHandler> minecraftEncoder;
    private final Field encoderState;

    private VelocityConnectionAccess(
            final Class<?> connectedPlayer,
            final Method getConnection,
            final Method eventLoop,
            final Method write,
            final Method isClosed,
            final Method getChannel,
            final Class<? extends ChannelHandler> minecraftEncoder,
            final Field encoderState) {
        this.connectedPlayer = connectedPlayer;
        this.getConnection = getConnection;
        this.eventLoop = eventLoop;
        this.write = write;
        this.isClosed = isClosed;
        this.getChannel = getChannel;
        this.minecraftEncoder = minecraftEncoder;
        this.encoderState = encoderState;
    }

    static VelocityConnectionAccess discover() {
        final ClassLoader loader = Player.class.getClassLoader();
        try {
            final Class<?> playerType = Class.forName(CONNECTED_PLAYER, false, loader);
            final Class<?> connectionType = Class.forName(MINECRAFT_CONNECTION, false, loader);
            final Class<?> encoderType = Class.forName(MINECRAFT_ENCODER, false, loader);
            final Method getConnection = playerType.getMethod("getConnection");
            if (getConnection.getReturnType() != connectionType) {
                throw new InjectionException("ConnectedPlayer#getConnection descriptor changed");
            }
            final Method eventLoop = connectionType.getMethod("eventLoop");
            final Method write = connectionType.getMethod("write", Object.class);
            final Method isClosed = connectionType.getMethod("isClosed");
            final Method getChannel = connectionType.getMethod("getChannel");
            final Field encoderState = encoderType.getDeclaredField("state");
            if (!Executor.class.isAssignableFrom(eventLoop.getReturnType())
                    || !Future.class.isAssignableFrom(write.getReturnType())
                    || isClosed.getReturnType() != boolean.class
                    || !Channel.class.isAssignableFrom(getChannel.getReturnType())
                    || !ChannelHandler.class.isAssignableFrom(encoderType)
                    || !encoderState.getType().isEnum()) {
                throw new InjectionException(
                        "MinecraftConnection or MinecraftEncoder descriptors changed");
            }
            if (!encoderState.trySetAccessible()) {
                throw new InjectionException("MinecraftEncoder.state is inaccessible");
            }
            @SuppressWarnings("unchecked")
            final Class<? extends ChannelHandler> checkedEncoder =
                    (Class<? extends ChannelHandler>) encoderType;
            return new VelocityConnectionAccess(
                    playerType, getConnection, eventLoop, write, isClosed,
                    getChannel, checkedEncoder, encoderState);
        } catch (final ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            throw new InjectionException(
                    "Unsupported Velocity internals: raw connection methods are unavailable", error);
        }
    }

    boolean supports(final Object player) {
        return connectedPlayer.isInstance(player);
    }

    Object connection(final Player player) {
        if (!supports(player)) {
            throw new IllegalArgumentException("Player is not Velocity's ConnectedPlayer: "
                    + ((Object) player).getClass().getName());
        }
        return invoke(getConnection, player);
    }

    Executor eventLoop(final Object connection) {
        return (Executor) invoke(eventLoop, connection);
    }

    boolean isClosed(final Object connection) {
        return (boolean) invoke(isClosed, connection);
    }

    /**
     * Returns the state used for the next clientbound packet.
     *
     * <p>Velocity deliberately switches {@code MinecraftEncoder.state} before it switches the
     * connection/session-handler state during both reconfiguration transitions. Raw buffers bypass
     * the encoder, but must still use the packet id for this outbound state.</p>
     */
    ProtocolState outboundProtocolState(final Object connection) {
        final Channel channel = (Channel) invoke(getChannel, connection);
        final ChannelHandler encoder = channel.pipeline().get(minecraftEncoder);
        if (encoder == null) {
            throw new InjectionException("Velocity's MinecraftEncoder is absent from the player pipeline");
        }
        final Object state;
        try {
            state = encoderState.get(encoder);
        } catch (final IllegalAccessException error) {
            throw new InjectionException("Velocity's MinecraftEncoder.state became inaccessible", error);
        }
        if (!(state instanceof Enum<?> enumState)) {
            throw new InjectionException("Velocity's MinecraftEncoder.state is not an enum value");
        }
        return switch (enumState.name()) {
            case "HANDSHAKE" -> ProtocolState.HANDSHAKE;
            case "STATUS" -> ProtocolState.STATUS;
            case "LOGIN" -> ProtocolState.LOGIN;
            case "CONFIG" -> ProtocolState.CONFIGURATION;
            case "PLAY" -> ProtocolState.PLAY;
            default -> throw new InjectionException(
                    "Unknown Velocity outbound protocol state " + enumState.name());
        };
    }

    /** Returns Velocity's ChannelFuture, or null when the connection became inactive. */
    Future<?> write(final Object connection, final Object message) {
        return (Future<?>) invoke(write, connection, message);
    }

    private static Object invoke(final Method method, final Object receiver, final Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (final IllegalAccessException error) {
            throw new IllegalStateException("Velocity internal method became inaccessible: " + method, error);
        } catch (final InvocationTargetException error) {
            final Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException("Velocity internal method failed: " + method, cause);
        }
    }
}
