package org.lime.velocidialog.api.dialog.internal.impl;

import net.kyori.adventure.text.Component;
import org.lime.velocidialog.api.dialog.body.PlainMessageDialogBody;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;

public record PlainMessageBodyImpl(Component contents, int width) implements PlainMessageDialogBody {

    public PlainMessageBodyImpl {
        DialogValidation.requireRange(width, "width", 1, 1024);
    }

    public PlainMessageBodyImpl(final Component contents) {
        this(contents, 200);
    }
}
