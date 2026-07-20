package org.lime.velocidialog.api.dialog.internal.impl;

import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.type.ConfirmationType;

public record ConfirmationTypeImpl(
    ActionButton yesButton,
    ActionButton noButton
) implements ConfirmationType {
}
