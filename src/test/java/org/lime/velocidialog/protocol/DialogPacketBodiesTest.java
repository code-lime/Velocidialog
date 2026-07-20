package org.lime.velocidialog.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import org.junit.jupiter.api.Test;

class DialogPacketBodiesTest {
    @Test
    void networkNbtIsNamelessAndRoundTrips() {
        final CompoundBinaryTag tag = CompoundBinaryTag.builder().putByte("x", (byte) 1).build();
        final ByteBuf encoded = Unpooled.buffer();
        NbtWireCodec.write(encoded, tag);

        final byte[] actual = new byte[encoded.readableBytes()];
        encoded.getBytes(encoded.readerIndex(), actual);
        assertArrayEquals(new byte[]{10, 1, 0, 1, 'x', 1, 0}, actual);
        assertEquals(tag, NbtWireCodec.readCompound(encoded));
        assertFalse(encoded.isReadable());
    }

    @Test
    void configurationShowUsesDirectNbtWithoutHolderDiscriminator() {
        final CompoundBinaryTag dialog = CompoundBinaryTag.builder().putString("type", "minecraft:notice").build();
        final ByteBuf encoded = Unpooled.buffer();
        DialogPacketBodies.writeShow(encoded, ProtocolPhase.CONFIGURATION, ShowDialogPayload.inline(dialog));

        assertEquals(10, encoded.getUnsignedByte(encoded.readerIndex()));
        assertEquals(ShowDialogPayload.inline(dialog), DialogPacketBodies.readShow(encoded, ProtocolPhase.CONFIGURATION));
        assertFalse(encoded.isReadable());
        assertThrows(IllegalArgumentException.class, () -> DialogPacketBodies.writeShow(
                Unpooled.buffer(), ProtocolPhase.CONFIGURATION, ShowDialogPayload.reference(0)
        ));
    }

    @Test
    void playShowUsesMinecraftHolderEncoding() {
        final CompoundBinaryTag dialog = CompoundBinaryTag.builder().putString("type", "minecraft:notice").build();
        final ByteBuf inline = Unpooled.buffer();
        DialogPacketBodies.writeShow(inline, ProtocolPhase.PLAY, ShowDialogPayload.inline(dialog));
        assertEquals(0, inline.getUnsignedByte(inline.readerIndex()));
        assertEquals(10, inline.getUnsignedByte(inline.readerIndex() + 1));
        assertEquals(ShowDialogPayload.inline(dialog), DialogPacketBodies.readShow(inline, ProtocolPhase.PLAY));

        final ByteBuf reference = Unpooled.buffer();
        DialogPacketBodies.writeShow(reference, ProtocolPhase.PLAY, ShowDialogPayload.reference(300));
        assertEquals(301, MinecraftVarInts.read(reference.duplicate()));
        assertEquals(ShowDialogPayload.reference(300), DialogPacketBodies.readShow(reference, ProtocolPhase.PLAY));
        assertFalse(reference.isReadable());
    }

    @Test
    void clearBodyMustBeEmpty() {
        final ByteBuf empty = Unpooled.buffer();
        DialogPacketBodies.writeClear(empty);
        DialogPacketBodies.readClear(empty);
        assertThrows(IllegalArgumentException.class, () -> DialogPacketBodies.readClear(
                Unpooled.wrappedBuffer(new byte[]{1})
        ));
    }

    @Test
    void customClickRoundTripsAndIdentifierPeekDoesNotConsume() {
        final CompoundBinaryTag payload = CompoundBinaryTag.builder()
                .putString("name", "value")
                .putInt("number", 42)
                .build();
        final ByteBuf encoded = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(encoded, new CustomClickPacketBody("velocidialog:callback", payload));
        final int originalIndex = encoded.readerIndex();

        assertEquals("velocidialog:callback", DialogPacketBodies.peekCustomClickIdentifier(encoded));
        assertEquals(originalIndex, encoded.readerIndex());
        assertEquals(
                new CustomClickPacketBody("velocidialog:callback", payload),
                DialogPacketBodies.readCustomClick(encoded)
        );
        assertFalse(encoded.isReadable());
    }

    @Test
    void lengthPrefixedEndTagRepresentsAbsentCustomClickPayload() {
        final ByteBuf encoded = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(encoded, new CustomClickPacketBody("example:none", null));
        final ByteBuf body = encoded.duplicate();
        DialogPacketBodies.peekCustomClickIdentifier(body);
        final int identifierLength = MinecraftVarInts.read(body);
        body.skipBytes(identifierLength);
        assertEquals(1, MinecraftVarInts.read(body));
        assertEquals(0, body.readUnsignedByte());
        assertNull(DialogPacketBodies.readCustomClick(encoded).payload());
    }

    @Test
    void structuralScannerAcceptsEveryStandardNbtPayloadKind() {
        final CompoundBinaryTag payload = CompoundBinaryTag.builder()
                .putByte("byte", (byte) 1)
                .putShort("short", (short) 2)
                .putInt("int", 3)
                .putLong("long", 4L)
                .putFloat("float", 5.0f)
                .putDouble("double", 6.0d)
                .putByteArray("bytes", new byte[]{7, 8})
                .putString("string", "nine")
                .put("list", ListBinaryTag.builder(BinaryTagTypes.STRING)
                        .add(StringBinaryTag.stringBinaryTag("ten"))
                        .build())
                .put("compound", CompoundBinaryTag.builder().putInt("eleven", 11).build())
                .putIntArray("ints", new int[]{12, 13})
                .putLongArray("longs", new long[]{14L, 15L})
                .build();
        final ByteBuf encoded = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(encoded, new CustomClickPacketBody("example:all", payload));
        assertEquals(payload, DialogPacketBodies.readCustomClick(encoded).payload());
    }

    @Test
    void inboundCustomClickRejectsMalformedUtf8LengthsAndTrailingData() {
        final ByteBuf malformedUtf8 = Unpooled.buffer();
        MinecraftVarInts.write(malformedUtf8, 2);
        malformedUtf8.writeByte(0xC3).writeByte(0x28);
        assertThrows(
                IllegalArgumentException.class,
                () -> DialogPacketBodies.peekCustomClickIdentifier(malformedUtf8)
        );

        final ByteBuf oversizedNbt = encodedIdentifier("example:large");
        MinecraftVarInts.write(oversizedNbt, DialogPacketBodies.MAX_INBOUND_CUSTOM_CLICK_NBT_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> DialogPacketBodies.readCustomClick(oversizedNbt));

        final ByteBuf trailing = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(trailing, new CustomClickPacketBody("example:trailing", null));
        trailing.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> DialogPacketBodies.readCustomClick(trailing));
    }

    @Test
    void inboundCustomClickEnforcesSixteenContainerLevels() {
        final CompoundBinaryTag accepted = nestedCompound(16);
        final ByteBuf acceptedBytes = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(acceptedBytes, new CustomClickPacketBody("example:accepted", accepted));
        assertEquals(accepted, DialogPacketBodies.readCustomClick(acceptedBytes).payload());

        final ByteBuf rejectedBytes = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(
                rejectedBytes,
                new CustomClickPacketBody("example:rejected", nestedCompound(17))
        );
        assertThrows(IllegalArgumentException.class, () -> DialogPacketBodies.readCustomClick(rejectedBytes));
    }

    @Test
    void rawDepthScannerRejectsExtremelyDeepNbtBeforeRecursiveParsing() {
        final int levels = 4_000;
        final ByteBuf rawNbt = Unpooled.buffer(1 + (levels - 1) * 3 + levels);
        rawNbt.writeByte(10); // Nameless root compound.
        for (int level = 1; level < levels; level++) {
            rawNbt.writeByte(10).writeShort(0); // Empty-name child compound entry.
        }
        for (int level = 0; level < levels; level++) {
            rawNbt.writeByte(0); // Close every compound.
        }

        final ByteBuf packet = encodedIdentifier("example:deep");
        MinecraftVarInts.write(packet, rawNbt.readableBytes());
        packet.writeBytes(rawNbt);
        assertThrows(IllegalArgumentException.class, () -> DialogPacketBodies.readCustomClick(packet));
    }

    @Test
    void malformedVarIntsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftVarInts.read(Unpooled.buffer()));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVarInts.read(
                Unpooled.wrappedBuffer(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x1F})
        ));
    }

    private static ByteBuf encodedIdentifier(final String identifier) {
        final byte[] bytes = identifier.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final ByteBuf output = Unpooled.buffer();
        MinecraftVarInts.write(output, bytes.length);
        output.writeBytes(bytes);
        return output;
    }

    private static CompoundBinaryTag nestedCompound(final int levels) {
        BinaryTag value = StringBinaryTag.stringBinaryTag("leaf");
        for (int level = 0; level < levels; level++) {
            value = CompoundBinaryTag.builder().put("child", value).build();
        }
        return assertInstanceOf(CompoundBinaryTag.class, value);
    }
}
