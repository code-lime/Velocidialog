package com.velocitypowered.proxy.protocol.netty;

import com.velocitypowered.proxy.protocol.StateRegistry;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import java.util.Objects;

/** Descriptor/state stand-in for Velocity's outbound MinecraftEncoder. */
public final class MinecraftEncoder extends ChannelOutboundHandlerAdapter {
    @SuppressWarnings("FieldMayBeFinal") // Production Velocity mutates this exact field.
    private StateRegistry state;

    public MinecraftEncoder(final StateRegistry state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public void setState(final StateRegistry state) {
        this.state = Objects.requireNonNull(state, "state");
    }
}
