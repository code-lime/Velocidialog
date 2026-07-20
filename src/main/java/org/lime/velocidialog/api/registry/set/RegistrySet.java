package org.lime.velocidialog.api.registry.set;

import org.jetbrains.annotations.Contract;
import org.lime.velocidialog.api.registry.RegistryKey;

/**
 * Represents a collection tied to a registry.
 * <p>
 * Velocidialog registry sets contain direct values which are anonymous and do not have keys in a registry.
 * They are created via {@link #valueSet(RegistryKey, Iterable)}.
 *
 * @param <T> registry value type
 */
public sealed interface RegistrySet<T> permits RegistryValueSet {

    /**
     * Creates a {@link RegistryValueSet} from anonymous values.
     *
     * @param registryKey the registry key for the type of these values
     * @param values the values
     * @return a new registry set
     * @param <T> the type of the values
     */
    @Contract(value = "_, _ -> new", pure = true)
    static <T> RegistryValueSet<T> valueSet(final RegistryKey<T> registryKey, final Iterable<? extends T> values) {
        return RegistryValueSetImpl.create(registryKey, values);
    }

    /**
     * Get the registry key for this set.
     *
     * @return the registry key
     */
    RegistryKey<T> registryKey();

    /**
     * Get the size of this set.
     *
     * @return the size
     */
    int size();

    /**
     * Checks if the registry set is empty.
     *
     * @return true, if empty
     */
    default boolean isEmpty() {
        return this.size() == 0;
    }
}
