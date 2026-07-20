package org.lime.velocidialog.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class ObjectComponentFallbackAccessTest {
    @Test
    void readsAdventureFiveStyleFallbackWithoutAStaticMethodReference() {
        final ObjectComponentFallbackAccess.Reader reader =
                ObjectComponentFallbackAccess.readerFor(WithFallback.class);
        assertEquals(Component.text("fallback"), reader.read(new WithFallback()));
    }

    @Test
    void returnsNullWhenRunningOnAnApiWithoutFallback() {
        final ObjectComponentFallbackAccess.Reader reader =
                ObjectComponentFallbackAccess.readerFor(WithoutFallback.class);
        assertNull(reader.read(new WithoutFallback()));
    }

    @Test
    void rejectsAnUnexpectedFallbackReturnType() {
        final ObjectComponentFallbackAccess.Reader reader =
                ObjectComponentFallbackAccess.readerFor(InvalidFallback.class);
        assertThrows(DialogEncodingException.class, () -> reader.read(new InvalidFallback()));
    }

    public static final class WithFallback {
        public Component fallback() {
            return Component.text("fallback");
        }
    }

    public static final class WithoutFallback {
    }

    public static final class InvalidFallback {
        public String fallback() {
            return "not a component";
        }
    }
}
