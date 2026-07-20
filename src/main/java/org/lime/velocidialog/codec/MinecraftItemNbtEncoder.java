package org.lime.velocidialog.codec;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.text.event.DataComponentValue;
import net.kyori.adventure.text.event.DataComponentValueConverterRegistry;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.nbt.api.BinaryTagHolder;

/** Encodes Adventure's modern show-item representation as ItemStack codec NBT. */
public final class MinecraftItemNbtEncoder {
    private static final TagStringIO TAG_STRING_IO = TagStringIO.tagStringIO();

    /**
     * Encodes the base ItemStack codec map used by dialog item bodies and hover events.
     *
     * <p>Legacy pre-1.20.5 item NBT cannot be losslessly converted to data
     * components without a data fixer and is rejected rather than silently lost.</p>
     */
    public CompoundBinaryTag encode(final HoverEvent.ShowItem item) {
        if (item == null) {
            throw new DialogEncodingException("ShowItem must not be null");
        }
        if (item.count() < 1 || item.count() > 99) {
            throw new DialogEncodingException("Item count must be in range 1..99, got " + item.count());
        }
        if (item.nbt() != null) {
            throw new DialogEncodingException(
                    "Legacy ShowItem NBT is not supported by the 1.21.6+ item codec; use data components"
            );
        }

        final CompoundBinaryTag.Builder result = CompoundBinaryTag.builder()
                .putString("id", encodeKey(item.item(), "Item id"))
                .putInt("count", item.count());

        final Map<Key, DataComponentValue> values = item.dataComponents();
        if (!values.isEmpty()) {
            final CompoundBinaryTag.Builder components = CompoundBinaryTag.builder(values.size());
            for (final Map.Entry<Key, DataComponentValue> entry : values.entrySet()) {
                encodeDataComponent(components, entry.getKey(), entry.getValue());
            }
            result.put("components", components.build());
        }
        return result.build();
    }

    private static void encodeDataComponent(
            final CompoundBinaryTag.Builder output,
            final Key key,
            final DataComponentValue value
    ) {
        final String componentId = encodeKey(key, "Item data component id");
        if (value == null) {
            throw new DialogEncodingException(
                    "Item data component " + componentId + " has a null value"
            );
        }
        if (value instanceof DataComponentValue.Removed) {
            output.put('!' + componentId, CompoundBinaryTag.empty());
            return;
        }

        final BinaryTag tag;
        try {
            final DataComponentValue.TagSerializable serializable =
                    DataComponentValueConverterRegistry.convert(
                            DataComponentValue.TagSerializable.class,
                            key,
                            value
                    );
            final BinaryTagHolder holder = Objects.requireNonNull(
                    serializable.asBinaryTag(),
                    "TagSerializable returned a null BinaryTagHolder"
            );
            final String snbt = Objects.requireNonNull(
                    holder.string(),
                    "BinaryTagHolder returned a null string"
            );
            tag = TAG_STRING_IO.asTag(snbt);
        } catch (final IOException | RuntimeException exception) {
            throw new DialogEncodingException(
                    "Unable to serialize item data component " + componentId,
                    exception
            );
        }
        if (tag.type() == BinaryTagTypes.END) {
            throw new DialogEncodingException(
                    "Item data component " + componentId + " serialized to an invalid EndTag"
            );
        }
        output.put(componentId, tag);
    }

    private static String encodeKey(final Key key, final String description) {
        if (key == null) {
            throw new DialogEncodingException(description + " must not be null");
        }

        final String encoded;
        try {
            encoded = key.asString();
        } catch (final RuntimeException exception) {
            throw new DialogEncodingException(description + " cannot be serialized", exception);
        }
        if (!Key.parseable(encoded) || !Key.key(encoded).asString().equals(encoded)) {
            throw new DialogEncodingException(description + " is not a valid resource location: " + encoded);
        }
        return encoded;
    }
}
