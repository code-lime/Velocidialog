package org.lime.velocidialog.api.registry.set;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.lime.velocidialog.api.registry.RegistryKey;

@ApiStatus.Internal
record RegistryValueSetImpl<T>(RegistryKey<T> registryKey, List<T> values) implements RegistryValueSet<T> {

    RegistryValueSetImpl {
        values = List.copyOf(values);
    }

    static <T> RegistryValueSet<T> create(final RegistryKey<T> registryKey, final Iterable<? extends T> values) {
        final List<T> copiedValues = new ArrayList<>();
        values.forEach(copiedValues::add);
        return new RegistryValueSetImpl<>(registryKey, copiedValues);
    }
}
