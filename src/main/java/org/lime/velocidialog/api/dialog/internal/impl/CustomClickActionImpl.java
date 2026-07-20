package org.lime.velocidialog.api.dialog.internal.impl;

import java.io.IOException;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.action.DialogAction;

public record CustomClickActionImpl(
    Key id,
    @Nullable BinaryTagHolder additions
) implements DialogAction.CustomClickAction {

    public CustomClickActionImpl {
        Objects.requireNonNull(id, "id");
        if (additions != null) {
            try {
                final BinaryTag tag = TagStringIO.tagStringIO().asTag(additions.string());
                if (!(tag instanceof CompoundBinaryTag)) {
                    throw new IllegalArgumentException("Additions must be a compound tag");
                }
            } catch (final IOException error) {
                throw new RuntimeException(error);
            }
        }
    }
}
