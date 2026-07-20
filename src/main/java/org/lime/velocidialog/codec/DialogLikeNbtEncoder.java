package org.lime.velocidialog.codec;

import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.nbt.CompoundBinaryTag;

/** Recursion hook used for {@code show_dialog} component click events. */
@FunctionalInterface
public interface DialogLikeNbtEncoder {
    CompoundBinaryTag encode(DialogLike dialog);
}
