package org.lime.velocidialog.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import java.io.IOException;
import java.io.InputStream;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;

/** Nameless network-NBT codec used by Minecraft 1.20.2 and newer. */
public final class NbtWireCodec {
    private static final long DEFAULT_READ_QUOTA = 2_097_162L;

    private NbtWireCodec() {
    }

    /** Writes the root type followed immediately by its nameless payload. */
    public static void write(final ByteBuf output, final CompoundBinaryTag tag) {
        try {
            // Do not call BinaryTag#type()/BinaryTagType directly here. BinaryTagType changed
            // from an abstract class in Adventure 4 to an interface in Adventure 5, so bytecode
            // compiled against either shape is not binary-compatible with the other one. The
            // public BinaryTagIO.Writer descriptor is stable and its runtime implementation uses
            // the BinaryTagType shape supplied by that same Adventure version.
            BinaryTagIO.writer().writeNameless(
                    tag, (java.io.OutputStream) new ByteBufOutputStream(output));
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to write network NBT", exception);
        }
    }

    /** Reads a nameless compound with Adventure's bounded reader. */
    public static CompoundBinaryTag readCompound(final ByteBuf input) {
        return readCompound(input, DEFAULT_READ_QUOTA);
    }

    /** Reads a nameless compound with an approximate allocation quota. */
    public static CompoundBinaryTag readCompound(final ByteBuf input, final long quota) {
        try {
            return BinaryTagIO.reader(quota).readNameless((InputStream) new ByteBufInputStream(input));
        } catch (final IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Unable to read network compound NBT", exception);
        }
    }
}
