package com.velocitypowered.proxy.connection;

import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.netty.MinecraftEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Velocity 4 test descriptor stand-in that owns outbound buffers. */
public final class MinecraftConnection {
    private final Executor eventLoop;
    private final MinecraftEncoder encoder = new MinecraftEncoder(StateRegistry.CONFIG);
    private final EmbeddedChannel channel = new EmbeddedChannel(encoder);
    private final List<byte[]> writes = new ArrayList<>();
    private boolean closed;

    public MinecraftConnection(final Executor eventLoop) {
        this.eventLoop = eventLoop;
    }

    public Executor eventLoop() {
        return eventLoop;
    }

    public boolean isClosed() {
        return closed;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setOutboundState(final ProtocolState state) {
        encoder.setState(switch (state) {
            case HANDSHAKE -> StateRegistry.HANDSHAKE;
            case STATUS -> StateRegistry.STATUS;
            case LOGIN -> StateRegistry.LOGIN;
            case CONFIGURATION -> StateRegistry.CONFIG;
            case PLAY -> StateRegistry.PLAY;
        });
    }

    public Future<?> write(final Object message) {
        try {
            if (closed) {
                return null;
            }
            final ByteBuf buffer = (ByteBuf) message;
            writes.add(ByteBufUtil.getBytes(
                    buffer, buffer.readerIndex(), buffer.readableBytes(), false));
            return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
        } finally {
            ReferenceCountUtil.release(message);
        }
    }

    public List<byte[]> writes() {
        return List.copyOf(writes);
    }
}
