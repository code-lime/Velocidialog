package org.lime.velocidialog.protocol;

import java.util.Objects;
import net.kyori.adventure.nbt.CompoundBinaryTag;

/** Decoded serverbound custom-click body. A null payload represents the NBT end tag. */
public record CustomClickPacketBody(String id, CompoundBinaryTag payload) {
    public CustomClickPacketBody {
        Objects.requireNonNull(id, "id");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
    }
}
