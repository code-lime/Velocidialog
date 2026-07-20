package org.lime.velocidialog.codec;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;

/**
 * Reflective Adventure 5 bridge for {@code ObjectComponent.fallback()}.
 *
 * <p>The plugin is compiled against Adventure 4.26, where the method does not
 * exist. Looking it up by name keeps the main artifact free of an Adventure
 * 5-only method reference while still preserving the 26.1 fallback field when
 * Adventure 5 is supplied by Velocity.</p>
 */
final class ObjectComponentFallbackAccess {
    private static final Reader RUNTIME_READER = readerFor(ObjectComponent.class);

    private ObjectComponentFallbackAccess() {
    }

    static Component fallback(final ObjectComponent component) {
        return RUNTIME_READER.read(component);
    }

    static Reader readerFor(final Class<?> componentType) {
        final Method fallback;
        try {
            fallback = componentType.getMethod("fallback");
        } catch (final NoSuchMethodException ignored) {
            return component -> null;
        }

        return component -> {
            final Object value;
            try {
                value = fallback.invoke(component);
            } catch (final IllegalAccessException exception) {
                throw new DialogEncodingException("Cannot access ObjectComponent fallback", exception);
            } catch (final InvocationTargetException exception) {
                throw new DialogEncodingException(
                        "ObjectComponent fallback accessor failed",
                        exception.getCause()
                );
            }
            if (value == null || value instanceof Component) {
                return (Component) value;
            }
            throw new DialogEncodingException(
                    "ObjectComponent fallback has unexpected type " + value.getClass().getName()
            );
        };
    }

    @FunctionalInterface
    interface Reader {
        Component read(Object component);
    }
}
