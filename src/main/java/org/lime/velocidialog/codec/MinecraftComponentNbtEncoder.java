package org.lime.velocidialog.codec;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.text.BlockNBTComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.EntityNBTComponent;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.StorageNBTComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.object.SpriteObjectContents;

/**
 * Direct Adventure component to Minecraft codec-NBT encoder.
 *
 * <p>This intentionally does not route through Gson. In particular, it can
 * recursively encode {@code show_dialog}, which Adventure's generic JSON
 * serializer cannot read or write without a platform dialog implementation.</p>
 */
public final class MinecraftComponentNbtEncoder {
    private static final int MAX_DEPTH = 128;

    private final DialogLikeNbtEncoder dialogEncoder;
    private final MinecraftItemNbtEncoder itemEncoder;

    public MinecraftComponentNbtEncoder(final DialogLikeNbtEncoder dialogEncoder) {
        this(dialogEncoder, new MinecraftItemNbtEncoder());
    }

    public MinecraftComponentNbtEncoder(
            final DialogLikeNbtEncoder dialogEncoder,
            final MinecraftItemNbtEncoder itemEncoder
    ) {
        this.dialogEncoder = Objects.requireNonNull(dialogEncoder, "dialogEncoder");
        this.itemEncoder = Objects.requireNonNull(itemEncoder, "itemEncoder");
    }

    public BinaryTag encode(final Component component) {
        return encode(Objects.requireNonNull(component, "component"), 0);
    }

    private BinaryTag encode(final Component component, final int depth) {
        checkDepth(depth);
        if (component instanceof TextComponent text
                && text.children().isEmpty()
                && text.style().isEmpty()) {
            return StringBinaryTag.stringBinaryTag(text.content());
        }

        final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder();
        encodeContents(output, component, depth);
        encodeChildren(output, component.children(), depth);
        encodeStyle(output, component.style(), depth);
        return output.build();
    }

    private void encodeContents(
            final CompoundBinaryTag.Builder output,
            final Component component,
            final int depth
    ) {
        if (component instanceof TextComponent text) {
            output.putString("text", text.content());
        } else if (component instanceof TranslatableComponent translatable) {
            output.putString("translate", translatable.key());
            if (translatable.fallback() != null) {
                output.putString("fallback", translatable.fallback());
            }
            if (!translatable.arguments().isEmpty()) {
                final ListBinaryTag.Builder<BinaryTag> arguments = ListBinaryTag.heterogeneousListBinaryTag(
                        translatable.arguments().size()
                );
                for (final TranslationArgument argument : translatable.arguments()) {
                    arguments.add(encodeTranslationArgument(argument, depth + 1));
                }
                output.put("with", arguments.build());
            }
        } else if (component instanceof KeybindComponent keybind) {
            output.putString("keybind", keybind.keybind());
        } else if (component instanceof ScoreComponent score) {
            output.put("score", CompoundBinaryTag.builder()
                    .putString("name", score.name())
                    .putString("objective", score.objective())
                    .build());
        } else if (component instanceof SelectorComponent selector) {
            output.putString("selector", selector.pattern());
            if (selector.separator() != null) {
                output.put("separator", encode(selector.separator(), depth + 1));
            }
        // NBTComponent has two generic parameters in Adventure 4 and one in
        // Adventure 5. Its erased JVM contract is unchanged, so keep this use
        // raw to produce one binary that links on both Velocity generations.
        } else if (component instanceof NBTComponent nbt) {
            output.putString("nbt", nbt.nbtPath());
            if (nbt.interpret()) {
                output.put("interpret", booleanTag(true));
            }
            if (nbt.separator() != null) {
                output.put("separator", encode(nbt.separator(), depth + 1));
            }
            if (nbt instanceof EntityNBTComponent entity) {
                output.putString("entity", entity.selector());
            } else if (nbt instanceof BlockNBTComponent block) {
                output.putString("block", block.pos().asString());
            } else if (nbt instanceof StorageNBTComponent storage) {
                output.putString("storage", storage.storage().asString());
            } else {
                throw new DialogEncodingException(
                        "Unknown NBT component " + ((Object) component).getClass().getName());
            }
        } else if (component instanceof ObjectComponent object) {
            encodeObject(output, object.contents());
            final Component fallback = ObjectComponentFallbackAccess.fallback(object);
            if (fallback != null) {
                output.put("fallback", encode(fallback, depth + 1));
            }
        } else {
            throw new DialogEncodingException(
                    "Unsupported component type " + ((Object) component).getClass().getName());
        }
    }

    private BinaryTag encodeTranslationArgument(final TranslationArgument argument, final int depth) {
        final Object value = argument.value();
        if (value instanceof Component component) {
            return encode(component, depth);
        }
        if (value instanceof ComponentLike componentLike) {
            return encode(componentLike.asComponent(), depth);
        }
        if (value instanceof Boolean bool) {
            return booleanTag(bool);
        }
        if (value instanceof Byte number) {
            return ByteBinaryTag.byteBinaryTag(number);
        }
        if (value instanceof Short number) {
            return ShortBinaryTag.shortBinaryTag(number);
        }
        if (value instanceof Integer number) {
            return IntBinaryTag.intBinaryTag(number);
        }
        if (value instanceof Long number) {
            return LongBinaryTag.longBinaryTag(number);
        }
        if (value instanceof Float number) {
            return FloatBinaryTag.floatBinaryTag(number);
        }
        if (value instanceof Double number) {
            return DoubleBinaryTag.doubleBinaryTag(number);
        }
        if (value instanceof Number number) {
            return DoubleBinaryTag.doubleBinaryTag(number.doubleValue());
        }
        if (value instanceof String string) {
            return StringBinaryTag.stringBinaryTag(string);
        }
        throw new DialogEncodingException("Unsupported translation argument " + value);
    }

    private void encodeChildren(
            final CompoundBinaryTag.Builder output,
            final List<Component> children,
            final int depth
    ) {
        if (children.isEmpty()) {
            return;
        }
        final ListBinaryTag.Builder<BinaryTag> tags = ListBinaryTag.heterogeneousListBinaryTag(children.size());
        for (final Component child : children) {
            tags.add(encode(child, depth + 1));
        }
        output.put("extra", tags.build());
    }

    private void encodeStyle(final CompoundBinaryTag.Builder output, final Style style, final int depth) {
        final TextColor color = style.color();
        if (color != null) {
            output.putString(
                    "color",
                    color instanceof NamedTextColor named
                            ? NamedTextColor.NAMES.keyOrThrow(named)
                            : color.asHexString()
            );
        }
        final ShadowColor shadow = style.shadowColor();
        if (shadow != null) {
            output.putInt("shadow_color", shadow.value());
        }
        putDecoration(output, "bold", style, TextDecoration.BOLD);
        putDecoration(output, "italic", style, TextDecoration.ITALIC);
        putDecoration(output, "underlined", style, TextDecoration.UNDERLINED);
        putDecoration(output, "strikethrough", style, TextDecoration.STRIKETHROUGH);
        putDecoration(output, "obfuscated", style, TextDecoration.OBFUSCATED);

        if (style.clickEvent() != null) {
            output.put("click_event", encodeClickEvent(style.clickEvent()));
        }
        if (style.hoverEvent() != null) {
            output.put("hover_event", encodeHover(style.hoverEvent(), depth + 1));
        }
        if (style.insertion() != null) {
            output.putString("insertion", style.insertion());
        }
        if (style.font() != null) {
            output.putString("font", style.font().asString());
        }
    }

    /** Encodes the typed click-event map used inside a component style. */
    @SuppressWarnings("rawtypes")
    public CompoundBinaryTag encodeClickEvent(final ClickEvent click) {
        final String action = click.action().toString();
        final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder().putString("action", action);
        final ClickEvent.Payload payload = click.payload();
        switch (action) {
            case "open_url" -> {
                String url = ((ClickEvent.Payload.Text) payload).value();
                if (!url.contains("://")) {
                    url = "https://" + url;
                }
                output.putString("url", requireUntrustedUri(url));
            }
            case "open_file" -> output.putString(
                    "path",
                    ((ClickEvent.Payload.Text) payload).value()
            );
            case "run_command", "suggest_command" -> output.putString(
                    "command",
                    requireChatString(((ClickEvent.Payload.Text) payload).value())
            );
            case "change_page" -> output.putInt(
                    "page",
                    requirePositivePage(((ClickEvent.Payload.Int) payload).integer())
            );
            case "copy_to_clipboard" -> output.putString(
                    "value",
                    ((ClickEvent.Payload.Text) payload).value()
            );
            case "show_dialog" -> {
                final DialogLike dialog = ((ClickEvent.Payload.Dialog) payload).dialog();
                output.put("dialog", this.dialogEncoder.encode(dialog));
            }
            case "custom" -> {
                output.putString("id", ((Keyed) payload).key().asString());
                final net.kyori.adventure.nbt.api.BinaryTagHolder nbt =
                        ((ClickEvent.Payload.Custom) payload).nbt();
                if (nbt != null) {
                    output.put("payload", parseSnbt(nbt.string(), "custom click payload"));
                }
            }
            default -> throw new DialogEncodingException("Unsupported click action " + action);
        }
        return output.build();
    }

    private CompoundBinaryTag encodeHover(final HoverEvent<?> hover, final int depth) {
        final String action = hover.action().toString();
        final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder().putString("action", action);
        switch (action) {
            case "show_text" -> output.put("value", encode((Component) hover.value(), depth));
            case "show_item" -> output.put(this.itemEncoder.encode((HoverEvent.ShowItem) hover.value()));
            case "show_entity" -> {
                final HoverEvent.ShowEntity entity = (HoverEvent.ShowEntity) hover.value();
                output.putString("id", entity.type().asString());
                output.put("uuid", uuidTag(entity.id()));
                if (entity.name() != null) {
                    output.put("name", encode(entity.name(), depth));
                }
            }
            default -> throw new DialogEncodingException("Unsupported hover action " + action);
        }
        return output.build();
    }

    private static void encodeObject(final CompoundBinaryTag.Builder output, final ObjectContents contents) {
        if (contents instanceof SpriteObjectContents sprite) {
            if (!((Object) sprite.atlas()).equals(SpriteObjectContents.DEFAULT_ATLAS)) {
                output.putString("atlas", sprite.atlas().asString());
            }
            output.putString("sprite", sprite.sprite().asString());
            return;
        }
        if (contents instanceof PlayerHeadObjectContents playerHead) {
            final CompoundBinaryTag.Builder player = CompoundBinaryTag.builder();
            if (playerHead.name() != null) {
                player.putString("name", requirePlayerName(playerHead.name()));
            }
            if (playerHead.id() != null) {
                player.put("id", uuidTag(playerHead.id()));
            }
            if (!playerHead.profileProperties().isEmpty()) {
                final ListBinaryTag.Builder<CompoundBinaryTag> properties = ListBinaryTag.builder(
                        net.kyori.adventure.nbt.BinaryTagTypes.COMPOUND
                );
                for (final PlayerHeadObjectContents.ProfileProperty property : playerHead.profileProperties()) {
                    final CompoundBinaryTag.Builder encoded = CompoundBinaryTag.builder()
                            .putString("name", property.name())
                            .putString("value", property.value());
                    if (property.signature() != null) {
                        encoded.putString("signature", property.signature());
                    }
                    properties.add(encoded.build());
                }
                player.put("properties", properties.build());
            }
            if (playerHead.texture() != null) {
                player.putString("texture", playerHead.texture().asString());
            }
            output.put("player", player.build());
            if (!playerHead.hat()) {
                output.put("hat", booleanTag(false));
            }
            return;
        }
        throw new DialogEncodingException(
                "Unsupported object contents " + ((Object) contents).getClass().getName());
    }

    private static String requireUntrustedUri(final String value) {
        final URI uri;
        try {
            uri = new URI(value);
        } catch (final URISyntaxException exception) {
            throw new DialogEncodingException("Invalid open_url URI " + value, exception);
        }
        final String scheme = uri.getScheme();
        if (scheme == null) {
            throw new DialogEncodingException("open_url URI is missing a scheme: " + value);
        }
        final String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            throw new DialogEncodingException("Unsupported open_url URI scheme " + scheme);
        }
        return uri.toString();
    }

    private static String requireChatString(final String value) {
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '\u00a7' || character < ' ' || character == '\u007f') {
                throw new DialogEncodingException(
                        "Command contains a character rejected by Minecraft CHAT_STRING at index " + index
                );
            }
        }
        return value;
    }

    private static int requirePositivePage(final int page) {
        if (page < 1) {
            throw new DialogEncodingException("change_page page must be positive: " + page);
        }
        return page;
    }

    private static String requirePlayerName(final String name) {
        if (name.length() > 16) {
            throw new DialogEncodingException("Player-head name is longer than 16 characters");
        }
        for (int index = 0; index < name.length(); index++) {
            final char character = name.charAt(index);
            if (character <= ' ' || character >= '\u007f') {
                throw new DialogEncodingException(
                        "Player-head name contains an invalid character at index " + index
                );
            }
        }
        return name;
    }

    private static void putDecoration(
            final CompoundBinaryTag.Builder output,
            final String field,
            final Style style,
            final TextDecoration decoration
    ) {
        final TextDecoration.State state = style.decoration(decoration);
        if (state != TextDecoration.State.NOT_SET) {
            output.put(field, booleanTag(state == TextDecoration.State.TRUE));
        }
    }

    private static IntArrayBinaryTag uuidTag(final UUID uuid) {
        return IntArrayBinaryTag.intArrayBinaryTag(new int[]{
                (int) (uuid.getMostSignificantBits() >>> 32),
                (int) uuid.getMostSignificantBits(),
                (int) (uuid.getLeastSignificantBits() >>> 32),
                (int) uuid.getLeastSignificantBits()
        });
    }

    private static ByteBinaryTag booleanTag(final boolean value) {
        return ByteBinaryTag.byteBinaryTag(value ? (byte) 1 : (byte) 0);
    }

    private static BinaryTag parseSnbt(final String snbt, final String description) {
        try {
            return TagStringIO.tagStringIO().asTag(snbt);
        } catch (final IOException exception) {
            throw new DialogEncodingException("Invalid SNBT in " + description, exception);
        }
    }

    private static void checkDepth(final int depth) {
        if (depth > MAX_DEPTH) {
            throw new DialogEncodingException("Component nesting is deeper than " + MAX_DEPTH);
        }
    }
}
