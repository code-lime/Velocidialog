package org.lime.velocidialog.api.dialog.type;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.lime.velocidialog.api.dialog.ActionButton;

/**
 * Represents a notice dialog.
 * @see DialogType#notice(ActionButton)
 */
@ApiStatus.NonExtendable
public non-sealed interface NoticeType extends DialogType {

    /**
     * Returns the action button associated with this notice type.
     *
     * @return the action button
     */
    @Contract(pure = true)
    ActionButton action();
}
