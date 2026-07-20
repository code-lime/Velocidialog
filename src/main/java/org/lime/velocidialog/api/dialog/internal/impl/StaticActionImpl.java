package org.lime.velocidialog.api.dialog.internal.impl;

import java.util.Objects;
import net.kyori.adventure.text.event.ClickEvent;
import org.lime.velocidialog.api.dialog.action.DialogAction;

public record StaticActionImpl(ClickEvent value) implements DialogAction.StaticAction {

    public StaticActionImpl {
        Objects.requireNonNull(value, "value");
        if (!value.action().readable() && !"show_dialog".equals(value.action().toString())) {
            throw new IllegalArgumentException("action must be readable or show_dialog");
        }
    }
}
