package org.lime.velocidialog.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.event.DataComponentValue;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;

class MinecraftItemNbtEncoderTest {
    @Test
    void encodesModernDataComponentsAndRemovedComponents() {
        final DataComponentValue.TagSerializable customData = () -> BinaryTagHolder.binaryTagHolder(
                "{answer:42,source:\"test\"}"
        );
        final Keyed stone = () -> Key.key("minecraft:stone");
        final HoverEvent.ShowItem item = HoverEvent.ShowItem.showItem(stone, 3, Map.of(
                Key.key("minecraft:custom_data"), customData,
                Key.key("minecraft:custom_name"), DataComponentValue.removed()
        ));

        final CompoundBinaryTag encoded = new MinecraftItemNbtEncoder().encode(item);
        assertEquals("minecraft:stone", encoded.getString("id"));
        assertEquals(3, encoded.getInt("count"));
        final CompoundBinaryTag components = encoded.getCompound("components");
        assertEquals(42, components.getCompound("minecraft:custom_data").getInt("answer"));
        assertEquals("test", components.getCompound("minecraft:custom_data").getString("source"));
        assertEquals(CompoundBinaryTag.empty(), components.getCompound("!minecraft:custom_name"));
    }

    @Test
    void omitsEmptyDataComponentMap() {
        final CompoundBinaryTag encoded = new MinecraftItemNbtEncoder().encode(
                HoverEvent.ShowItem.showItem(Key.key("minecraft:stone"), 1)
        );
        assertEquals(CompoundBinaryTag.builder()
                .putString("id", "minecraft:stone")
                .putInt("count", 1)
                .build(), encoded);
    }

    @Test
    void matchesMojangItemStackCodecShapeAtCountLimit() {
        final DataComponentValue.TagSerializable damage = () ->
                BinaryTagHolder.binaryTagHolder("7");
        final CompoundBinaryTag encoded = new MinecraftItemNbtEncoder().encode(
                HoverEvent.ShowItem.showItem(Key.key("minecraft:stone"), 99, Map.of(
                        Key.key("minecraft:damage"), damage
                ))
        );

        assertEquals(CompoundBinaryTag.builder()
                .putString("id", "minecraft:stone")
                .putInt("count", 99)
                .put("components", CompoundBinaryTag.builder()
                        .putInt("minecraft:damage", 7)
                        .build())
                .build(), encoded);
    }

    @Test
    void rejectsLegacyNbtAndCountsOutsideTheModernCodecRange() {
        assertThrows(DialogEncodingException.class, () -> new MinecraftItemNbtEncoder().encode(
                HoverEvent.ShowItem.showItem(
                        Key.key("minecraft:stone"), 1, BinaryTagHolder.binaryTagHolder("{legacy:1b}")
                )
        ));
        assertThrows(DialogEncodingException.class, () -> new MinecraftItemNbtEncoder().encode(
                HoverEvent.ShowItem.showItem(Key.key("minecraft:stone"), 0)
        ));
        assertThrows(DialogEncodingException.class, () -> new MinecraftItemNbtEncoder().encode(
                HoverEvent.ShowItem.showItem(Key.key("minecraft:stone"), 100)
        ));
    }

    @Test
    void rejectsNullComponentEntriesWithCodecContext() {
        final Map<Key, DataComponentValue> nullKey = new HashMap<>();
        nullKey.put(null, DataComponentValue.removed());
        final DialogEncodingException keyFailure = assertThrows(
                DialogEncodingException.class,
                () -> new MinecraftItemNbtEncoder().encode(HoverEvent.ShowItem.showItem(
                        Key.key("minecraft:stone"), 1, nullKey
                ))
        );
        assertTrue(keyFailure.getMessage().contains("component id"));

        final Map<Key, DataComponentValue> nullValue = new HashMap<>();
        nullValue.put(Key.key("minecraft:custom_data"), null);
        final DialogEncodingException valueFailure = assertThrows(
                DialogEncodingException.class,
                () -> new MinecraftItemNbtEncoder().encode(HoverEvent.ShowItem.showItem(
                        Key.key("minecraft:stone"), 1, nullValue
                ))
        );
        assertTrue(valueFailure.getMessage().contains("minecraft:custom_data"));
    }

    @Test
    void wrapsTagSerializationAndSnbtFailuresWithComponentId() {
        final DataComponentValue.TagSerializable brokenConverter = () -> {
            throw new IllegalStateException("broken converter");
        };
        final DialogEncodingException converterFailure = assertThrows(
                DialogEncodingException.class,
                () -> encodeCustomData(brokenConverter)
        );
        assertTrue(converterFailure.getMessage().contains("minecraft:custom_data"));
        assertInstanceOf(IllegalStateException.class, converterFailure.getCause());

        final DataComponentValue.TagSerializable malformedSnbt = () ->
                BinaryTagHolder.binaryTagHolder("{unterminated:");
        final DialogEncodingException snbtFailure = assertThrows(
                DialogEncodingException.class,
                () -> encodeCustomData(malformedSnbt)
        );
        assertTrue(snbtFailure.getMessage().contains("minecraft:custom_data"));
        assertNotNull(snbtFailure.getCause());
    }

    private static CompoundBinaryTag encodeCustomData(final DataComponentValue value) {
        return new MinecraftItemNbtEncoder().encode(HoverEvent.ShowItem.showItem(
                Key.key("minecraft:stone"),
                1,
                Map.of(Key.key("minecraft:custom_data"), value)
        ));
    }
}
