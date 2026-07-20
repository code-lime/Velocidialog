package org.lime.velocidialog.api.dialog.internal.impl;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;

public record ActionButtonImpl(
    Component label,
    @Nullable Component tooltip,
    int width,
    @Nullable DialogAction action
) implements ActionButton {

    public static final class BuilderImpl implements ActionButton.Builder {

        private final Component label;
        private @Nullable Component tooltip;
        private int width = 150;
        private @Nullable DialogAction action;

        public BuilderImpl(final Component label) {
            this.label = label;
        }

        @Override
        public ActionButton.Builder tooltip(final @Nullable Component tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        @Override
        public ActionButton.Builder width(final @Range(from = 1, to = 1024) int width) {
            this.width = DialogValidation.requireRange(width, "width", 1, 1024);
            return this;
        }

        @Override
        public ActionButton.Builder action(final @Nullable DialogAction action) {
            this.action = action;
            return this;
        }

        @Override
        public ActionButton build() {
            return new ActionButtonImpl(this.label, this.tooltip, this.width, this.action);
        }
    }
}
