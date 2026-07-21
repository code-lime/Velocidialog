package org.lime.velocidialog.protocol;

import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

/** Raw body codecs for the three packets that make up the dialog protocol. */
public final class DialogPacketBodies {
    public static final int MAX_CUSTOM_CLICK_NBT_BYTES = 65_536;
    public static final int MAX_INBOUND_CUSTOM_CLICK_NBT_BYTES = 32_768;
    public static final long MAX_CUSTOM_CLICK_NBT_QUOTA = 32_768L;
    public static final int MAX_CUSTOM_CLICK_NBT_DEPTH = 16;
    public static final int MAX_IDENTIFIER_CHARACTERS = 32_767;
    private static final int MAX_IDENTIFIER_BYTES = MAX_IDENTIFIER_CHARACTERS * 3;

    private DialogPacketBodies() {
    }

    /**
     * Writes a show-dialog body.
     *
     * <p>Configuration uses the context-free codec and therefore never carries
     * a holder discriminator. Play uses {@code 0 + NBT} for an inline holder or
     * {@code rawId + 1} for a registry reference.</p>
     */
    public static void writeShow(
            final ByteBuf output,
            final ProtocolPhase phase,
            final ShowDialogPayload payload
    ) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(payload, "payload");

        if (phase == ProtocolPhase.CONFIGURATION) {
            if (!(payload instanceof ShowDialogPayload.Inline inline)) {
                throw new IllegalArgumentException("Configuration show-dialog packets require an inline dialog");
            }
            NbtWireCodec.write(output, inline.dialog());
            return;
        }

        if (payload instanceof ShowDialogPayload.Inline inline) {
            MinecraftVarInts.write(output, 0);
            NbtWireCodec.write(output, inline.dialog());
        } else if (payload instanceof ShowDialogPayload.Reference reference) {
            MinecraftVarInts.write(output, reference.rawRegistryId() + 1);
        } else {
            throw new IllegalArgumentException("Unknown show-dialog payload " + payload.getClass().getName());
        }
    }

    public static ShowDialogPayload readShow(final ByteBuf input, final ProtocolPhase phase) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(phase, "phase");
        if (phase == ProtocolPhase.CONFIGURATION) {
            return ShowDialogPayload.inline(NbtWireCodec.readCompound(input));
        }

        final int holderId = MinecraftVarInts.read(input);
        if (holderId < 0) {
            throw new IllegalArgumentException("Negative dialog holder id " + holderId);
        }
        return holderId == 0
                ? ShowDialogPayload.inline(NbtWireCodec.readCompound(input))
                : ShowDialogPayload.reference(holderId - 1);
    }

    /** A clear-dialog packet has an empty body. */
    public static void writeClear(final ByteBuf output) {
        Objects.requireNonNull(output, "output");
    }

    /** Rejects malformed clear-dialog packets containing trailing data. */
    public static void readClear(final ByteBuf input) {
        Objects.requireNonNull(input, "input");
        if (input.isReadable()) {
            throw new IllegalArgumentException("Clear-dialog packet body must be empty");
        }
    }

    /** Writes Identifier + length-prefixed optional NBT used by custom clicks. */
    public static void writeCustomClick(final ByteBuf output, final CustomClickPacketBody body) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(body, "body");
        writeString(output, body.id());

        final ByteBuf nbt = output.alloc().buffer();
        try {
            if (body.payload() == null) {
                nbt.writeByte(0); // EndTag is FriendlyByteBuf.writeNbt(null).
            } else {
                NbtWireCodec.write(nbt, body.payload());
            }
            final int length = nbt.readableBytes();
            if (length > MAX_CUSTOM_CLICK_NBT_BYTES) {
                throw new IllegalArgumentException("Custom-click NBT is larger than " + MAX_CUSTOM_CLICK_NBT_BYTES + " bytes");
            }
            MinecraftVarInts.write(output, length);
            output.writeBytes(nbt);
        } finally {
            nbt.release();
        }
    }

    /** Reads the callback subset accepted by Paper: absent or compound NBT. */
    public static CustomClickPacketBody readCustomClick(final ByteBuf input) {
        Objects.requireNonNull(input, "input");
        final String id = readString(input);
        final int length = MinecraftVarInts.read(input);
        if (length < 1 || length > MAX_INBOUND_CUSTOM_CLICK_NBT_BYTES || length > input.readableBytes()) {
            throw new IllegalArgumentException("Invalid custom-click NBT length " + length);
        }

        final ByteBuf nbt = input.readSlice(length);
        final int rootType = nbt.getUnsignedByte(nbt.readerIndex());
        final CompoundBinaryTag payload;
        if (rootType == 0) {
            nbt.skipBytes(1);
            payload = null;
        } else if (rootType == 10) {
            NbtStructureScanner.validateCompound(nbt.duplicate(), MAX_CUSTOM_CLICK_NBT_DEPTH);
            payload = NbtWireCodec.readCompound(nbt, MAX_CUSTOM_CLICK_NBT_QUOTA);
            validateNbtDepth(payload, 0);
        } else {
            throw new IllegalArgumentException(
                    "Custom-click callback payload must be a compound or EndTag, got type " + rootType);
        }
        if (nbt.isReadable()) {
            throw new IllegalArgumentException("Trailing data in custom-click NBT payload");
        }
        if (input.isReadable()) {
            throw new IllegalArgumentException("Trailing data in custom-click packet body");
        }
        return new CustomClickPacketBody(id, payload);
    }

    /**
     * Peeks only the namespaced identifier without parsing untrusted NBT or
     * changing the supplied buffer's indices.
     */
    public static String peekCustomClickIdentifier(final ByteBuf input) {
        Objects.requireNonNull(input, "input");
        return readString(input.duplicate());
    }

    /** Compatibility name used by the callback interception layer. */
    public static String readCustomClickIdentifier(final ByteBuf input) {
        return peekCustomClickIdentifier(input);
    }

    private static void writeString(final ByteBuf output, final String value) {
        if (value.length() > MAX_IDENTIFIER_CHARACTERS) {
            throw new IllegalArgumentException("Identifier is too long");
        }
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_IDENTIFIER_BYTES) {
            throw new IllegalArgumentException("Encoded identifier is too long");
        }
        MinecraftVarInts.write(output, bytes.length);
        output.writeBytes(bytes);
    }

    private static String readString(final ByteBuf input) {
        final int byteLength = MinecraftVarInts.read(input);
        if (byteLength < 0 || byteLength > MAX_IDENTIFIER_BYTES || byteLength > input.readableBytes()) {
            throw new IllegalArgumentException("Invalid identifier byte length " + byteLength);
        }
        final byte[] bytes = new byte[byteLength];
        input.readBytes(bytes);
        final String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (final CharacterCodingException exception) {
            throw new IllegalArgumentException("Identifier is not valid UTF-8", exception);
        }
        if (value.length() > MAX_IDENTIFIER_CHARACTERS) {
            throw new IllegalArgumentException("Decoded identifier is too long");
        }
        return value;
    }

    private static void validateNbtDepth(final BinaryTag tag, final int parentDepth) {
        if (tag instanceof CompoundBinaryTag compound) {
            final int depth = parentDepth + 1;
            if (depth > MAX_CUSTOM_CLICK_NBT_DEPTH) {
                throw new IllegalArgumentException(
                        "Custom-click NBT is deeper than " + MAX_CUSTOM_CLICK_NBT_DEPTH
                );
            }
            final Iterable<java.util.Map.Entry<String, ? extends BinaryTag>> entries = compound;
            for (final java.util.Map.Entry<String, ? extends BinaryTag> entry : entries) {
                validateNbtDepth(entry.getValue(), depth);
            }
        } else if (tag instanceof ListBinaryTag list) {
            final int depth = parentDepth + 1;
            if (depth > MAX_CUSTOM_CLICK_NBT_DEPTH) {
                throw new IllegalArgumentException(
                        "Custom-click NBT is deeper than " + MAX_CUSTOM_CLICK_NBT_DEPTH
                );
            }
            final Iterable<BinaryTag> elements = list;
            for (final BinaryTag element : elements) {
                validateNbtDepth(element, depth);
            }
        }
    }
}
