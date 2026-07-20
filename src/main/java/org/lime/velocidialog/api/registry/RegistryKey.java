package org.lime.velocidialog.api.registry;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.key.Keyed;
import org.jspecify.annotations.NullMarked;
import org.lime.velocidialog.api.dialog.Dialog;

import static org.lime.velocidialog.api.registry.RegistryKeyImpl.create;

/**
 * Identifier for a specific registry. For use with
 * {@link TypedKey} and the registry modification API.
 *
 * @param <T> the value type
 */
@SuppressWarnings("unused")
@NullMarked
public sealed interface RegistryKey<T> extends Keyed permits RegistryKeyImpl {

    /**
     * Direct-value registry for dialogs.
     */
    RegistryKey<Dialog> DIALOG = create("dialog");

    /**
     * Constructs a new {@link TypedKey} for this registry given the typed key's key.
     *
     * @param key the key of the typed key.
     * @return the constructed typed key.
     */
    default TypedKey<T> typedKey(final Key key) {
        return TypedKey.create(this, key);
    }

    /**
     * Constructs a new {@link TypedKey} for this registry given the typed key's key.
     *
     * @param key the string representation of the key that will be passed to {@link Key#key(String)}.
     * @return the constructed typed key.
     */
    default TypedKey<T> typedKey(@KeyPattern final String key) {
        return TypedKey.create(this, key);
    }
}
