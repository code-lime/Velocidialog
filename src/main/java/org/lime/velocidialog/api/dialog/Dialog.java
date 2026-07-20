package org.lime.velocidialog.api.dialog;

import java.util.function.Consumer;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.registry.RegistryBuilderFactory;

/**
 * Represents a dialog. Can be created during normal proxy operation via {@link #create(Consumer)}.
 */
@ApiStatus.NonExtendable
public interface Dialog extends DialogLike {

    /**
     * Creates a new dialog using the provided builder.
     *
     * @param value the builder to use for creating the dialog
     * @return a new dialog instance
     */
    @ApiStatus.Experimental
    static Dialog create(final Consumer<RegistryBuilderFactory<Dialog, ? extends DialogRegistryEntry.Builder>> value) {
        return DialogInternals.create(value);
    }

    /**
     * Creates a dialog from already encoded direct protocol data.
     *
     * @param data the root compound accepted by the Minecraft direct dialog codec
     * @return a new dialog instance
     */
    @Contract(value = "_ -> new", pure = true)
    static Dialog raw(final CompoundBinaryTag data) {
        return DialogInternals.raw(data);
    }
}
