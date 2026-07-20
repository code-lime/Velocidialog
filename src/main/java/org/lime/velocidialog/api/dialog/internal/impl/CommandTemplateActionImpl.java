package org.lime.velocidialog.api.dialog.internal.impl;

import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.internal.DialogValidation;

public record CommandTemplateActionImpl(String template) implements DialogAction.CommandTemplateAction {

    public CommandTemplateActionImpl {
        template = DialogValidation.requireCommandTemplate(template);
    }
}
