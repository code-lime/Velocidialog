package org.lime.velocidialog.protocol;

import io.netty.buffer.ByteBuf;

/** Minimal Minecraft VarInt codec used by raw packet bodies. */
public final class MinecraftVarInts {
    private MinecraftVarInts() {
    }

    public static void write(final ByteBuf output, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            output.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }

    public static int read(final ByteBuf input) {
        int value = 0;
        int position = 0;
        byte current;
        do {
            if (!input.isReadable()) {
                throw new IllegalArgumentException("Truncated VarInt");
            }
            current = input.readByte();
            value |= (current & 0x7F) << position;
            if (position == 28 && (current & 0xF0) != 0) {
                throw new IllegalArgumentException("VarInt is wider than 32 bits");
            }
            position += 7;
        } while ((current & 0x80) != 0);
        return value;
    }
}
