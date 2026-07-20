package org.lime.velocidialog.api.dialog.action;

import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.lime.velocidialog.api.dialog.DialogResponseView;

/**
 * A callback for a dialog action.
 */
@FunctionalInterface
public interface DialogActionCallback {

    /**
     * Handles a dialog action.
     *
     * @param response the response to the action
     * @param audience the audience to send the response to
     */
    @ApiStatus.OverrideOnly
    void accept(DialogResponseView response, Audience audience);
}
