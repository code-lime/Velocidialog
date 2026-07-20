package org.lime.velocidialog.api.dialog.internal.impl;

import java.util.Objects;
import net.kyori.adventure.text.event.HoverEvent;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.body.ItemDialogBody;
import org.lime.velocidialog.api.dialog.body.PlainMessageDialogBody;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;
import org.lime.velocidialog.codec.MinecraftItemNbtEncoder;

public record ItemDialogBodyImpl(
    HoverEvent.ShowItem item,
    @Nullable PlainMessageDialogBody description,
    boolean showDecorations,
    boolean showTooltip,
    int width,
    int height
) implements ItemDialogBody {

    public ItemDialogBodyImpl {
        validateItem(item);
        DialogValidation.requireRange(width, "width", 1, 256);
        DialogValidation.requireRange(height, "height", 1, 256);
    }

    private static void validateItem(final HoverEvent.ShowItem item) {
        Objects.requireNonNull(item, "item");
        // This is the Velocity equivalent of Paper's ItemStack.validateStrict call. Besides
        // enforcing the 1..99 count range, it rejects legacy NBT and data components that
        // cannot be represented by the 1.21.6+ ItemStack network codec.
        new MinecraftItemNbtEncoder().encode(item);
    }

    public static final class BuilderImpl implements ItemDialogBody.Builder {

        private final HoverEvent.ShowItem item;
        private @Nullable PlainMessageDialogBody description;
        private boolean showDecorations = true;
        private boolean showTooltip = true;
        private int width = 16;
        private int height = 16;

        public BuilderImpl(final HoverEvent.ShowItem item) {
            validateItem(item);
            this.item = item;
        }

        @Override
        public ItemDialogBody.Builder description(final @Nullable PlainMessageDialogBody description) {
            this.description = description;
            return this;
        }

        @Override
        public ItemDialogBody.Builder showDecorations(final boolean showDecorations) {
            this.showDecorations = showDecorations;
            return this;
        }

        @Override
        public ItemDialogBody.Builder showTooltip(final boolean showTooltip) {
            this.showTooltip = showTooltip;
            return this;
        }

        @Override
        public ItemDialogBody.Builder width(final @Range(from = 1, to = 256) int width) {
            this.width = DialogValidation.requireRange(width, "width", 1, 256);
            return this;
        }

        @Override
        public ItemDialogBody.Builder height(final @Range(from = 1, to = 256) int height) {
            this.height = DialogValidation.requireRange(height, "height", 1, 256);
            return this;
        }

        @Override
        public ItemDialogBody build() {
            return new ItemDialogBodyImpl(
                this.item,
                this.description,
                this.showDecorations,
                this.showTooltip,
                this.width,
                this.height
            );
        }
    }
}
