package org.lime.velocidialog.api.registry;

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record RegistryKeyImpl<T>(Key key) implements RegistryKey<T> {

    @Override
    public boolean equals(final @Nullable Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    static <T> RegistryKey<T> create(final String key) {
        return new RegistryKeyImpl<>(Key.key(Key.MINECRAFT_NAMESPACE, key));
    }
}
