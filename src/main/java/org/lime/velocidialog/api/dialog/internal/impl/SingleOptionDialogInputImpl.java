package org.lime.velocidialog.api.dialog.internal.impl;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.input.SingleOptionDialogInput;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;

public record SingleOptionDialogInputImpl(
    String key,
    int width,
    List<SingleOptionDialogInput.OptionEntry> entries,
    Component label,
    boolean labelVisible
) implements SingleOptionDialogInput {

    public SingleOptionDialogInputImpl {
        entries = List.copyOf(entries);
    }

    public record SingleOptionEntryImpl(
        String id,
        @Nullable Component display,
        boolean initial
    ) implements OptionEntry {
    }

    public static final class BuilderImpl implements SingleOptionDialogInput.Builder {

        private final String key;
        private int width = 200;
        private final List<OptionEntry> entries;
        private final Component label;
        private boolean labelVisible = true;

        public BuilderImpl(final String key, final List<OptionEntry> entries, final Component label) {
            this.key = DialogValidation.requireInputKey(key);
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("entries must not be empty");
            }
            if (entries.stream().filter(OptionEntry::initial).count() > 1) {
                throw new IllegalArgumentException("only 1 option can be initially selected");
            }

            this.entries = entries;
            this.label = label;
        }

        @Override
        public BuilderImpl width(final int width) {
            this.width = DialogValidation.requireRange(width, "width", 1, 1024);
            return this;
        }

        @Override
        public BuilderImpl labelVisible(final boolean labelVisible) {
            this.labelVisible = labelVisible;
            return this;
        }

        @Override
        public SingleOptionDialogInput build() {
            return new SingleOptionDialogInputImpl(
                this.key,
                this.width,
                this.entries,
                this.label,
                this.labelVisible
            );
        }
    }
}
