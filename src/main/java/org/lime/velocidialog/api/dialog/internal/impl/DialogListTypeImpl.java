package org.lime.velocidialog.api.dialog.internal.impl;

import org.checkerframework.checker.index.qual.Positive;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;
import org.lime.velocidialog.api.dialog.type.DialogListType;
import org.lime.velocidialog.api.registry.set.RegistrySet;

public record DialogListTypeImpl(
    RegistrySet<Dialog> dialogs,
    @Nullable ActionButton exitAction,
    int columns,
    int buttonWidth
) implements DialogListType {

    public static final class BuilderImpl implements DialogListType.Builder {

        private final RegistrySet<Dialog> dialogs;
        private @Nullable ActionButton exitAction;
        private int columns = 2;
        private int buttonWidth = 150;

        public BuilderImpl(final RegistrySet<Dialog> dialogs) {
            this.dialogs = dialogs;
        }

        @Override
        public DialogListType.Builder exitAction(final @Nullable ActionButton exitAction) {
            this.exitAction = exitAction;
            return this;
        }

        @Override
        public DialogListType.Builder columns(final @Positive int columns) {
            this.columns = DialogValidation.requirePositive(columns, "columns");
            return this;
        }

        @Override
        public DialogListType.Builder buttonWidth(final @Range(from = 1, to = 1024) int buttonWidth) {
            this.buttonWidth = DialogValidation.requireRange(buttonWidth, "buttonWidth", 1, 1024);
            return this;
        }

        @Override
        public DialogListType build() {
            return new DialogListTypeImpl(this.dialogs, this.exitAction, this.columns, this.buttonWidth);
        }
    }
}
