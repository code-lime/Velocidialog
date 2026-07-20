package org.lime.velocidialog.codec;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.text.TranslatableComponent;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.DialogRegistryEntry;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.body.DialogBody;
import org.lime.velocidialog.api.dialog.body.ItemDialogBody;
import org.lime.velocidialog.api.dialog.body.PlainMessageDialogBody;
import org.lime.velocidialog.api.dialog.input.BooleanDialogInput;
import org.lime.velocidialog.api.dialog.input.DialogInput;
import org.lime.velocidialog.api.dialog.input.NumberRangeDialogInput;
import org.lime.velocidialog.api.dialog.input.SingleOptionDialogInput;
import org.lime.velocidialog.api.dialog.input.TextDialogInput;
import org.lime.velocidialog.api.dialog.type.ConfirmationType;
import org.lime.velocidialog.api.dialog.type.DialogListType;
import org.lime.velocidialog.api.dialog.type.DialogType;
import org.lime.velocidialog.api.dialog.type.MultiActionType;
import org.lime.velocidialog.api.dialog.type.NoticeType;
import org.lime.velocidialog.api.dialog.type.ServerLinksType;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.registry.set.RegistryValueSet;

/** Encodes the public immutable dialog model using Mojang's direct dialog codec shape. */
public final class DialogNbtCodec {
    private static final int MAX_DIALOG_RECURSION = 64;

    /** Creates a codec for the Paper-shaped immutable dialog model. */
    public DialogNbtCodec() {
    }

    /** Raw dialogs are returned unchanged; modeled dialogs are encoded recursively. */
    public CompoundBinaryTag encode(final Dialog dialog) {
        return new EncodingSession().encodeDialog(Objects.requireNonNull(dialog, "dialog"));
    }

    private final class EncodingSession {
        private final Map<Dialog, Boolean> activeDialogs = new IdentityHashMap<>();
        private final MinecraftItemNbtEncoder itemEncoder = new MinecraftItemNbtEncoder();
        private final MinecraftComponentNbtEncoder componentEncoder = new MinecraftComponentNbtEncoder(
                this::encodeDialogLike,
                this.itemEncoder
        );

        private CompoundBinaryTag encodeDialogLike(final DialogLike dialogLike) {
            if (!(dialogLike instanceof Dialog dialog)) {
                throw new DialogEncodingException(
                        "Unsupported DialogLike implementation " + dialogLike.getClass().getName()
                );
            }
            return encodeDialog(dialog);
        }

        private CompoundBinaryTag encodeDialog(final Dialog dialog) {
            final CompoundBinaryTag raw = DialogInternals.rawData(dialog);
            if (raw != null) {
                return raw;
            }
            final DialogRegistryEntry entry = DialogInternals.entry(dialog);
            if (entry == null) {
                throw new DialogEncodingException("Dialog has neither modeled entry nor raw data");
            }
            if (this.activeDialogs.size() >= MAX_DIALOG_RECURSION) {
                throw new DialogEncodingException("Dialog recursion is deeper than " + MAX_DIALOG_RECURSION);
            }
            if (this.activeDialogs.put(dialog, Boolean.TRUE) != null) {
                throw new DialogEncodingException("Recursive dialog cycle detected");
            }
            try {
                return encodeEntry(entry);
            } finally {
                this.activeDialogs.remove(dialog);
            }
        }

        private CompoundBinaryTag encodeEntry(final DialogRegistryEntry entry) {
            final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder();
            encodeBase(output, entry.base());
            encodeType(output, entry.type());
            return output.build();
        }

        private void encodeBase(final CompoundBinaryTag.Builder output, final DialogBase base) {
            output.put("title", this.componentEncoder.encode(base.title()));
            if (base.externalTitle() != null) {
                output.put("external_title", this.componentEncoder.encode(base.externalTitle()));
            }
            if (!base.canCloseWithEscape()) {
                output.put("can_close_with_escape", booleanTag(false));
            }
            if (!base.pause()) {
                output.put("pause", booleanTag(false));
            }
            if (base.afterAction() != DialogBase.DialogAfterAction.CLOSE) {
                output.putString("after_action", DialogBase.DialogAfterAction.NAMES.keyOrThrow(
                        base.afterAction()));
            }
            if (!base.body().isEmpty()) {
                if (base.body().size() == 1) {
                    output.put("body", encodeBody(base.body().get(0)));
                } else {
                    final ListBinaryTag.Builder<CompoundBinaryTag> body = ListBinaryTag.builder(
                            BinaryTagTypes.COMPOUND
                    );
                    for (final DialogBody value : base.body()) {
                        body.add(encodeBody(value));
                    }
                    output.put("body", body.build());
                }
            }
            if (!base.inputs().isEmpty()) {
                final ListBinaryTag.Builder<CompoundBinaryTag> inputs = ListBinaryTag.builder(
                        BinaryTagTypes.COMPOUND
                );
                for (final DialogInput input : base.inputs()) {
                    inputs.add(encodeInput(input));
                }
                output.put("inputs", inputs.build());
            }
        }

        private void encodeType(final CompoundBinaryTag.Builder output, final DialogType type) {
            if (type instanceof ConfirmationType confirmation) {
                output.putString("type", "minecraft:confirmation");
                output.put("yes", encodeButton(confirmation.yesButton()));
                output.put("no", encodeButton(confirmation.noButton()));
            } else if (type instanceof NoticeType notice) {
                output.putString("type", "minecraft:notice");
                if (!isDefaultNoticeButton(notice.action())) {
                    output.put("action", encodeButton(notice.action()));
                }
            } else if (type instanceof MultiActionType multi) {
                output.putString("type", "minecraft:multi_action");
                output.put("actions", encodeButtons(multi.actions()));
                putButtonListOptions(output, multi.exitAction(), multi.columns(), 150, false);
            } else if (type instanceof DialogListType list) {
                output.putString("type", "minecraft:dialog_list");
                if (!(list.dialogs() instanceof RegistryValueSet<Dialog> directDialogs)) {
                    throw new DialogEncodingException(
                            "Velocity dialog lists support only anonymous RegistryValueSet values");
                }
                final ListBinaryTag.Builder<CompoundBinaryTag> dialogs = ListBinaryTag.builder(
                        BinaryTagTypes.COMPOUND
                );
                for (final Dialog dialog : directDialogs) {
                    dialogs.add(encodeDialog(dialog));
                }
                output.put("dialogs", dialogs.build());
                putButtonListOptions(output, list.exitAction(), list.columns(), list.buttonWidth(), true);
            } else if (type instanceof ServerLinksType links) {
                output.putString("type", "minecraft:server_links");
                putButtonListOptions(output, links.exitAction(), links.columns(), links.buttonWidth(), true);
            } else {
                throw new DialogEncodingException("Unsupported dialog type " + type.getClass().getName());
            }
        }

        private void putButtonListOptions(
                final CompoundBinaryTag.Builder output,
                final ActionButton exitAction,
                final int columns,
                final int buttonWidth,
                final boolean hasButtonWidth
        ) {
            if (exitAction != null) {
                output.put("exit_action", encodeButton(exitAction));
            }
            if (columns != 2) {
                output.putInt("columns", columns);
            }
            if (hasButtonWidth && buttonWidth != 150) {
                output.putInt("button_width", buttonWidth);
            }
        }

        private ListBinaryTag encodeButtons(final List<ActionButton> values) {
            final ListBinaryTag.Builder<CompoundBinaryTag> buttons = ListBinaryTag.builder(
                    BinaryTagTypes.COMPOUND
            );
            for (final ActionButton button : values) {
                buttons.add(encodeButton(button));
            }
            return buttons.build();
        }

        private CompoundBinaryTag encodeButton(final ActionButton button) {
            final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder()
                    .put("label", this.componentEncoder.encode(button.label()));
            if (button.tooltip() != null) {
                output.put("tooltip", this.componentEncoder.encode(button.tooltip()));
            }
            if (button.width() != 150) {
                output.putInt("width", button.width());
            }
            if (button.action() != null) {
                output.put("action", encodeAction(button.action()));
            }
            return output.build();
        }

        private CompoundBinaryTag encodeAction(final DialogAction action) {
            if (action instanceof DialogAction.StaticAction staticAction) {
                final CompoundBinaryTag click = this.componentEncoder.encodeClickEvent(staticAction.value());
                final String clickType = click.getString("action");
                final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder()
                        .putString("type", "minecraft:" + clickType);
                for (final Map.Entry<String, ? extends BinaryTag> field : click) {
                    if (!"action".equals(field.getKey())) {
                        output.put(field.getKey(), field.getValue());
                    }
                }
                return output.build();
            }
            if (action instanceof DialogAction.CommandTemplateAction command) {
                return CompoundBinaryTag.builder()
                        .putString("type", "minecraft:dynamic/run_command")
                        .putString("template", command.template())
                        .build();
            }
            if (action instanceof DialogAction.CustomClickAction custom) {
                final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder()
                        .putString("type", "minecraft:dynamic/custom")
                        .putString("id", custom.id().asString());
                if (custom.additions() != null) {
                    output.put("additions", DialogInternals.decodeCompound(custom.additions()));
                }
                return output.build();
            }
            throw new DialogEncodingException("Unsupported dialog action " + action.getClass().getName());
        }

        private CompoundBinaryTag encodeBody(final DialogBody body) {
            if (body instanceof PlainMessageDialogBody message) {
                final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder()
                        .putString("type", "minecraft:plain_message")
                        .put("contents", this.componentEncoder.encode(message.contents()));
                if (message.width() != 200) {
                    output.putInt("width", message.width());
                }
                return output.build();
            }
            if (body instanceof ItemDialogBody item) {
                final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder()
                        .putString("type", "minecraft:item")
                        .put("item", this.itemEncoder.encode(item.item()));
                if (item.description() != null) {
                    output.put("description", encodePlainMessage(item.description()));
                }
                if (!item.showDecorations()) {
                    output.put("show_decorations", booleanTag(false));
                }
                if (!item.showTooltip()) {
                    output.put("show_tooltip", booleanTag(false));
                }
                if (item.width() != 16) {
                    output.putInt("width", item.width());
                }
                if (item.height() != 16) {
                    output.putInt("height", item.height());
                }
                return output.build();
            }
            throw new DialogEncodingException("Unsupported dialog body " + body.getClass().getName());
        }

        private CompoundBinaryTag encodePlainMessage(final PlainMessageDialogBody message) {
            final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder()
                    .put("contents", this.componentEncoder.encode(message.contents()));
            if (message.width() != 200) {
                output.putInt("width", message.width());
            }
            return output.build();
        }

        private CompoundBinaryTag encodeInput(final DialogInput input) {
            final CompoundBinaryTag.Builder output = CompoundBinaryTag.builder().putString("key", input.key());
            if (input instanceof BooleanDialogInput bool) {
                output.putString("type", "minecraft:boolean");
                output.put("label", this.componentEncoder.encode(bool.label()));
                if (bool.initial()) {
                    output.put("initial", booleanTag(true));
                }
                if (!"true".equals(bool.onTrue())) {
                    output.putString("on_true", bool.onTrue());
                }
                if (!"false".equals(bool.onFalse())) {
                    output.putString("on_false", bool.onFalse());
                }
            } else if (input instanceof NumberRangeDialogInput range) {
                output.putString("type", "minecraft:number_range");
                if (range.width() != 200) {
                    output.putInt("width", range.width());
                }
                output.put("label", this.componentEncoder.encode(range.label()));
                if (!"options.generic_value".equals(range.labelFormat())) {
                    output.putString("label_format", range.labelFormat());
                }
                output.put("start", FloatBinaryTag.floatBinaryTag(range.start()));
                output.put("end", FloatBinaryTag.floatBinaryTag(range.end()));
                if (range.initial() != null) {
                    output.put("initial", FloatBinaryTag.floatBinaryTag(range.initial()));
                }
                if (range.step() != null) {
                    output.put("step", FloatBinaryTag.floatBinaryTag(range.step()));
                }
            } else if (input instanceof SingleOptionDialogInput single) {
                output.putString("type", "minecraft:single_option");
                if (single.width() != 200) {
                    output.putInt("width", single.width());
                }
                final ListBinaryTag.Builder<CompoundBinaryTag> options = ListBinaryTag.builder(
                        BinaryTagTypes.COMPOUND
                );
                for (final SingleOptionDialogInput.OptionEntry entry : single.entries()) {
                    final CompoundBinaryTag.Builder encoded = CompoundBinaryTag.builder().putString("id", entry.id());
                    if (entry.display() != null) {
                        encoded.put("display", this.componentEncoder.encode(entry.display()));
                    }
                    if (entry.initial()) {
                        encoded.put("initial", booleanTag(true));
                    }
                    options.add(encoded.build());
                }
                output.put("options", options.build());
                output.put("label", this.componentEncoder.encode(single.label()));
                if (!single.labelVisible()) {
                    output.put("label_visible", booleanTag(false));
                }
            } else if (input instanceof TextDialogInput text) {
                output.putString("type", "minecraft:text");
                if (text.width() != 200) {
                    output.putInt("width", text.width());
                }
                output.put("label", this.componentEncoder.encode(text.label()));
                if (!text.labelVisible()) {
                    output.put("label_visible", booleanTag(false));
                }
                if (!text.initial().isEmpty()) {
                    output.putString("initial", text.initial());
                }
                if (text.maxLength() != 32) {
                    output.putInt("max_length", text.maxLength());
                }
                if (text.multiline() != null) {
                    final CompoundBinaryTag.Builder multiline = CompoundBinaryTag.builder();
                    if (text.multiline().maxLines() != null) {
                        multiline.putInt("max_lines", text.multiline().maxLines());
                    }
                    if (text.multiline().height() != null) {
                        multiline.putInt("height", text.multiline().height());
                    }
                    output.put("multiline", multiline.build());
                }
            } else {
                throw new DialogEncodingException("Unsupported dialog input " + input.getClass().getName());
            }
            return output.build();
        }

        private boolean isDefaultNoticeButton(final ActionButton button) {
            if (button.width() != 150
                    || button.tooltip() != null
                    || button.action() != null
                    || !button.label().children().isEmpty()
                    || !button.label().style().isEmpty()) {
                return false;
            }
            return button.label() instanceof TranslatableComponent translatable
                    && "gui.ok".equals(translatable.key())
                    && translatable.arguments().isEmpty()
                    && translatable.fallback() == null;
        }
    }

    private static ByteBinaryTag booleanTag(final boolean value) {
        return ByteBinaryTag.byteBinaryTag(value ? (byte) 1 : (byte) 0);
    }
}
