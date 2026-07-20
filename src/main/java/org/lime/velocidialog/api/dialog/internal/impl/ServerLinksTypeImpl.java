package org.lime.velocidialog.api.dialog.internal.impl;

import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;
import org.lime.velocidialog.api.dialog.type.ServerLinksType;

public record ServerLinksTypeImpl(
    @Nullable ActionButton exitAction,
    int columns,
    int buttonWidth
) implements ServerLinksType {

    public ServerLinksTypeImpl {
        DialogValidation.requirePositive(columns, "columns");
        DialogValidation.requireRange(buttonWidth, "buttonWidth", 1, 1024);
    }
}
