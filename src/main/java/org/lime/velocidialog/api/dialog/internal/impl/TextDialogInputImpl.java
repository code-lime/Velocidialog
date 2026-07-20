package org.lime.velocidialog.api.dialog.internal.impl;

import net.kyori.adventure.text.Component;
import org.checkerframework.checker.index.qual.Positive;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.input.TextDialogInput;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;

public record TextDialogInputImpl(
    String key,
    int width,
    Component label,
    boolean labelVisible,
    String initial,
    int maxLength,
    TextDialogInput.@Nullable MultilineOptions multiline
) implements TextDialogInput {

    public record MultilineOptionsImpl(
        @Nullable Integer maxLines,
        @Nullable Integer height
    ) implements MultilineOptions {

        public MultilineOptionsImpl {
            if (maxLines != null) {
                DialogValidation.requirePositive(maxLines, "maxLines");
            }
            if (height != null) {
                DialogValidation.requireRange(height, "height", 1, 512);
            }
        }
    }

    public static final class BuilderImpl implements TextDialogInput.Builder {

        private final String key;
        private int width = 200;
        private final Component label;
        private boolean labelVisible = true;
        private String initial = "";
        private int maxLength = 32;
        private TextDialogInput.@Nullable MultilineOptions multiline = null;

        public BuilderImpl(final String key, final Component label) {
            this.key = DialogValidation.requireInputKey(key);
            this.label = label;
        }

        @Override
        public TextDialogInput.Builder width(final @Range(from = 1, to = 1024) int width) {
            this.width = DialogValidation.requireRange(width, "width", 1, 1024);
            return this;
        }

        @Override
        public TextDialogInput.Builder labelVisible(final boolean labelVisible) {
            this.labelVisible = labelVisible;
            return this;
        }

        @Override
        public TextDialogInput.Builder initial(final String initial) {
            this.initial = initial;
            return this;
        }

        @Override
        public TextDialogInput.Builder maxLength(final @Positive int maxLength) {
            this.maxLength = DialogValidation.requirePositive(maxLength, "maxLength");
            return this;
        }

        @Override
        public TextDialogInput.Builder multiline(final TextDialogInput.@Nullable MultilineOptions multiline) {
            this.multiline = multiline;
            return this;
        }

        @Override
        public TextDialogInput build() {
            if (this.initial.length() > this.maxLength) {
                throw new IllegalStateException(
                    "The initial value must be less than or equal to the maximum length."
                );
            }
            return new TextDialogInputImpl(
                this.key,
                this.width,
                this.label,
                this.labelVisible,
                this.initial,
                this.maxLength,
                this.multiline
            );
        }
    }
}
