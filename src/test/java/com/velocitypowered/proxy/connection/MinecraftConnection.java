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
import java.util.Objects;
import java.util.concurrent.Executor;

/** Test descriptor-compatible stand-in that captures and owns outbound buffers. */
public final class MinecraftConnection {
    private final Executor eventLoop;
    private final MinecraftEncoder encoder = new MinecraftEncoder(StateRegistry.CONFIG);
    private final EmbeddedChannel channel = new EmbeddedChannel(encoder);
    private final List<byte[]> writes = new ArrayList<>();
    private boolean closed;
    private Throwable nextWriteFailure;

    public MinecraftConnection(final Executor eventLoop) {
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
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

    public void setClosed(final boolean closed) {
        this.closed = closed;
    }

    /**
     * Mirrors MinecraftConnection ownership: the message is released even when the write is rejected.
     */
    public Future<?> write(final Object message) {
        try {
            if (closed) {
                return null;
            }
            if (!(message instanceof ByteBuf buffer)) {
                throw new IllegalArgumentException("Expected ByteBuf, got " + message);
            }
            writes.add(ByteBufUtil.getBytes(buffer, buffer.readerIndex(), buffer.readableBytes(), false));
            final Throwable failure = nextWriteFailure;
            nextWriteFailure = null;
            return failure == null
                    ? ImmediateEventExecutor.INSTANCE.newSucceededFuture(null)
                    : ImmediateEventExecutor.INSTANCE.newFailedFuture(failure);
        } finally {
            ReferenceCountUtil.release(message);
        }
    }

    public void failNextWrite(final Throwable failure) {
        this.nextWriteFailure = Objects.requireNonNull(failure, "failure");
    }

    public List<byte[]> writes() {
        return List.copyOf(writes);
    }
}
