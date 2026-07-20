package org.lime.velocidialog.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;

class MinecraftComponentNbtEncoderTest {
    @Test
    void collapsesPlainTextToTheDirectStringAlternative() {
        final MinecraftComponentNbtEncoder encoder = encoderWithoutDialogs();
        assertEquals(StringBinaryTag.stringBinaryTag("plain"), encoder.encode(Component.text("plain")));
    }

    @Test
    void encodesContentChildrenStyleClickAndHoverDirectlyToNbt() {
        final Component component = Component.text("root")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)
                .append(Component.translatable("example.child", TranslationArgument.bool(true)))
                .clickEvent(ClickEvent.suggestCommand("/example"))
                .hoverEvent(Component.text("tooltip"))
                .insertion("inserted")
                .font(Key.key("example:font"));

        final CompoundBinaryTag encoded = assertInstanceOf(
                CompoundBinaryTag.class,
                encoderWithoutDialogs().encode(component)
        );
        assertEquals("root", encoded.getString("text"));
        assertEquals("red", encoded.getString("color"));
        assertEquals(ByteBinaryTag.byteBinaryTag((byte) 0), encoded.get("bold"));
        assertEquals("inserted", encoded.getString("insertion"));
        assertEquals("example:font", encoded.getString("font"));

        final ListBinaryTag extra = encoded.getList("extra");
        final CompoundBinaryTag child = assertInstanceOf(CompoundBinaryTag.class, extra.get(0));
        assertEquals("example.child", child.getString("translate"));
        assertEquals(ByteBinaryTag.byteBinaryTag((byte) 1), child.getList("with").get(0));
        final CompoundBinaryTag click = encoded.getCompound("click_event");
        assertEquals("suggest_command", click.getString("action"));
        assertEquals("/example", click.getString("command"));
        final CompoundBinaryTag hover = encoded.getCompound("hover_event");
        assertEquals("show_text", hover.getString("action"));
        assertEquals("tooltip", hover.getString("value"));
    }

    @Test
    void encodesShowDialogRecursivelyWithoutAJsonSerializer() {
        final Dialog nested = Dialog.raw(CompoundBinaryTag.builder()
                .putString("type", "minecraft:notice")
                .putString("title", "Nested")
                .build());
        final MinecraftComponentNbtEncoder encoder = new MinecraftComponentNbtEncoder(dialogLike -> {
            assertEquals(nested, dialogLike);
            return DialogInternals.rawData(nested);
        });

        final CompoundBinaryTag encoded = assertInstanceOf(
                CompoundBinaryTag.class,
                encoder.encode(Component.text("open").clickEvent(ClickEvent.showDialog(nested)))
        );
        final CompoundBinaryTag click = encoded.getCompound("click_event");
        assertEquals("show_dialog", click.getString("action"));
        assertEquals(DialogInternals.rawData(nested), click.getCompound("dialog"));
    }

    @Test
    void preservesOpenFileComponentsUsingTheOfficialCodecShape() {
        final CompoundBinaryTag encoded = assertInstanceOf(
                CompoundBinaryTag.class,
                encoderWithoutDialogs().encode(Component.text("file").clickEvent(ClickEvent.openFile("notes.txt")))
        );
        assertEquals("open_file", encoded.getCompound("click_event").getString("action"));
        assertEquals("notes.txt", encoded.getCompound("click_event").getString("path"));
    }

    @Test
    void encodesAllAdditionalAdventureComponentContentShapes() {
        final MinecraftComponentNbtEncoder encoder = encoderWithoutDialogs();
        assertEquals(
                "key.jump",
                assertInstanceOf(CompoundBinaryTag.class, encoder.encode(Component.keybind("key.jump")))
                        .getString("keybind")
        );
        assertEquals(
                "objective",
                assertInstanceOf(CompoundBinaryTag.class, encoder.encode(Component.score("Player", "objective")))
                        .getCompound("score").getString("objective")
        );
        assertEquals(
                "@a",
                assertInstanceOf(CompoundBinaryTag.class, encoder.encode(
                        Component.selector("@a", Component.text(", "))
                )).getString("selector")
        );
        final CompoundBinaryTag storage = assertInstanceOf(
                CompoundBinaryTag.class,
                encoder.encode(Component.storageNBT("path.value", true, Component.text("/"), Key.key("example:data")))
        );
        assertEquals("path.value", storage.getString("nbt"));
        assertEquals("example:data", storage.getString("storage"));
        assertEquals((byte) 1, storage.getByte("interpret"));
        assertEquals("/", storage.getString("separator"));

        final CompoundBinaryTag sprite = assertInstanceOf(
                CompoundBinaryTag.class,
                encoder.encode(Component.object(ObjectContents.sprite(
                        Key.key("example:atlas"), Key.key("example:sprite")
                )))
        );
        assertEquals("example:atlas", sprite.getString("atlas"));
        assertEquals("example:sprite", sprite.getString("sprite"));
        assertNull(sprite.get("fallback"));
    }

    @Test
    void validatesClickPayloadsLikeTheOfficialMinecraftCodecs() {
        final MinecraftComponentNbtEncoder encoder = encoderWithoutDialogs();

        assertEquals(
                "https://example.com",
                encoder.encodeClickEvent(ClickEvent.openUrl("example.com")).getString("url")
        );
        assertEquals(
                "HTTP://example.com",
                encoder.encodeClickEvent(ClickEvent.openUrl("HTTP://example.com")).getString("url")
        );
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encodeClickEvent(ClickEvent.openUrl("ftp://example.com"))
        );
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encodeClickEvent(ClickEvent.openUrl("https://invalid host"))
        );

        assertEquals(
                "\u043f\u0440\u0438\u0432\u0435\u0442",
                encoder.encodeClickEvent(ClickEvent.runCommand("\u043f\u0440\u0438\u0432\u0435\u0442"))
                        .getString("command")
        );
        assertEquals("", encoder.encodeClickEvent(ClickEvent.suggestCommand("")).getString("command"));
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encodeClickEvent(ClickEvent.runCommand("bad\ncommand"))
        );
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encodeClickEvent(ClickEvent.runCommand("bad\u00a7command"))
        );
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encodeClickEvent(ClickEvent.runCommand("bad\u007fcommand"))
        );

        assertEquals(1, encoder.encodeClickEvent(ClickEvent.changePage(1)).getInt("page"));
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encodeClickEvent(ClickEvent.changePage(0))
        );
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encodeClickEvent(ClickEvent.changePage(-1))
        );
    }

    @Test
    void validatesPlayerHeadNamesLikeMinecraftPlayerNameCodec() {
        final MinecraftComponentNbtEncoder encoder = encoderWithoutDialogs();
        final CompoundBinaryTag emptyName = assertInstanceOf(
                CompoundBinaryTag.class,
                encoder.encode(Component.object(ObjectContents.playerHead("")))
        );
        assertEquals("", emptyName.getCompound("player").getString("name"));
        final CompoundBinaryTag punctuation = assertInstanceOf(
                CompoundBinaryTag.class,
                encoder.encode(Component.object(ObjectContents.playerHead("Player-~")))
        );
        assertEquals("Player-~", punctuation.getCompound("player").getString("name"));

        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encode(Component.object(ObjectContents.playerHead("bad name")))
        );
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encode(Component.object(ObjectContents.playerHead("\u00e9")))
        );
        assertThrows(
                DialogEncodingException.class,
                () -> encoder.encode(Component.object(ObjectContents.playerHead("12345678901234567")))
        );
    }

    @Test
    void encodesHoverEntityUuidAsMinecraftIntArray() {
        final UUID id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        final Component component = Component.text("entity").hoverEvent(HoverEvent.showEntity(
                Key.key("minecraft:pig"), id, Component.text("Pig")
        ));
        final CompoundBinaryTag encoded = assertInstanceOf(
                CompoundBinaryTag.class,
                encoderWithoutDialogs().encode(component)
        );
        final CompoundBinaryTag hover = encoded.getCompound("hover_event");
        assertEquals("show_entity", hover.getString("action"));
        assertEquals("minecraft:pig", hover.getString("id"));
        assertEquals(
                IntArrayBinaryTag.intArrayBinaryTag(new int[]{
                        0x00112233, 0x44556677, 0x8899AABB, 0xCCDDEEFF
                }),
                hover.get("uuid")
        );
        assertEquals("Pig", hover.getString("name"));
    }

    private static MinecraftComponentNbtEncoder encoderWithoutDialogs() {
        return new MinecraftComponentNbtEncoder(dialog -> {
            fail("Unexpected dialog " + dialog);
            throw new AssertionError();
        });
    }
}
