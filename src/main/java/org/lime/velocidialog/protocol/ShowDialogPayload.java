package org.lime.velocidialog.protocol;

import java.util.Objects;
import net.kyori.adventure.nbt.CompoundBinaryTag;

/** The holder payload carried by a clientbound show-dialog packet. */
public sealed interface ShowDialogPayload permits ShowDialogPayload.Inline, ShowDialogPayload.Reference {
    /** An inline direct dialog. */
    record Inline(CompoundBinaryTag dialog) implements ShowDialogPayload {
        public Inline {
            Objects.requireNonNull(dialog, "dialog");
        }
    }

    /** A raw-id reference into the synchronized dialog registry. */
    record Reference(int rawRegistryId) implements ShowDialogPayload {
        public Reference {
            if (rawRegistryId < 0 || rawRegistryId == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("rawRegistryId must be in range 0.." + (Integer.MAX_VALUE - 1));
            }
        }
    }

    static Inline inline(final CompoundBinaryTag dialog) {
        return new Inline(dialog);
    }

    static Reference reference(final int rawRegistryId) {
        return new Reference(rawRegistryId);
    }
}
