package org.lime.velocidialog.api.dialog.internal.impl;

import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.type.NoticeType;

import static net.kyori.adventure.text.Component.translatable;

public record NoticeTypeImpl(ActionButton action) implements NoticeType {

    public static final ActionButton DEFAULT_ACTION = ActionButton.builder(translatable("gui.ok")).build();

    public NoticeTypeImpl() {
        this(DEFAULT_ACTION);
    }
}
