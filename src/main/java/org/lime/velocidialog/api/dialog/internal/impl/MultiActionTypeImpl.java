package org.lime.velocidialog.api.dialog.internal.impl;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;
import org.lime.velocidialog.api.dialog.type.MultiActionType;

public record MultiActionTypeImpl(
    List<ActionButton> actions,
    @Nullable ActionButton exitAction,
    int columns
) implements MultiActionType {

    public MultiActionTypeImpl {
        actions = List.copyOf(actions);
    }

    public static final class BuilderImpl implements MultiActionType.Builder {

        private final List<ActionButton> actions;
        private @Nullable ActionButton exitAction;
        private int columns = 2;

        public BuilderImpl(final List<ActionButton> actions) {
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("actions cannot be empty");
            }
            this.actions = actions;
        }

        @Override
        public MultiActionType.Builder exitAction(final @Nullable ActionButton exitAction) {
            this.exitAction = exitAction;
            return this;
        }

        @Override
        public MultiActionType.Builder columns(final int columns) {
            this.columns = DialogValidation.requirePositive(columns, "columns");
            return this;
        }

        @Override
        public MultiActionType build() {
            return new MultiActionTypeImpl(this.actions, this.exitAction, this.columns);
        }
    }
}
