package org.lime.velocidialog.api.dialog.internal.impl;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.input.NumberRangeDialogInput;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;

public record NumberRangeDialogInputImpl(
    String key,
    int width,
    Component label,
    String labelFormat,
    float start,
    float end,
    @Nullable Float initial,
    @Nullable Float step
) implements NumberRangeDialogInput {

    public static final class BuilderImpl implements NumberRangeDialogInput.Builder {

        private final String key;
        private final Component label;
        private final float start;
        private final float end;
        private int width = 200;
        private String labelFormat = "options.generic_value";
        private @Nullable Float initial = null;
        private @Nullable Float step = null;

        public BuilderImpl(final String key, final Component label, final float start, final float end) {
            this.key = DialogValidation.requireInputKey(key);
            this.label = label;
            this.start = start;
            this.end = end;
        }

        @Override
        public BuilderImpl width(final @Range(from = 1, to = 1024) int width) {
            this.width = DialogValidation.requireRange(width, "width", 1, 1024);
            return this;
        }

        @Override
        public BuilderImpl labelFormat(final String labelFormat) {
            this.labelFormat = labelFormat;
            return this;
        }

        @Override
        public BuilderImpl initial(final @Nullable Float initial) {
            this.initial = initial == null
                ? null
                : DialogValidation.requireRange(initial, "initial", this.start, this.end);
            return this;
        }

        @Override
        public BuilderImpl step(final @Nullable Float step) {
            this.step = step == null ? null : DialogValidation.requirePositive(step, "step");
            return this;
        }

        @Override
        public NumberRangeDialogInput build() {
            return new NumberRangeDialogInputImpl(
                this.key,
                this.width,
                this.label,
                this.labelFormat,
                this.start,
                this.end,
                this.initial,
                this.step
            );
        }
    }
}
