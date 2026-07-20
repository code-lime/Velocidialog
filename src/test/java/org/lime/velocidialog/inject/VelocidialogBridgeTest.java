package org.lime.velocidialog.inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class VelocidialogBridgeTest {
    private static final String BRIDGE = "com.velocitypowered.api.proxy.VelocidialogBridge";

    @Test
    void bridgeLoadsWithoutPluginOrVelocityClassesAndAcceptsChildCallbacks() throws Exception {
        final byte[] bytes = bridgeBytes();
        final IsolatedLoader loader = new IsolatedLoader();
        final Class<?> bridge = loader.defineBridge(bytes);

        assertSame(loader, bridge.getClassLoader());
        for (final Field field : bridge.getDeclaredFields()) {
            assertNull(field.getType().getClassLoader(), field::toString);
        }

        final AtomicReference<Object> shownPlayer = new AtomicReference<>();
        final AtomicReference<Object> shownDialog = new AtomicReference<>();
        final AtomicInteger closes = new AtomicInteger();
        final Object player = new Object();
        final Object dialog = new Object();
        final BiConsumer<Object, Object> show = (actualPlayer, actualDialog) -> {
            shownPlayer.set(actualPlayer);
            shownDialog.set(actualDialog);
        };
        final Consumer<Object> close = ignored -> closes.incrementAndGet();
        final BiPredicate<Object, Object> configuration = (ignoredPlayer, packet) -> packet == dialog;
        final BiPredicate<Object, Object> play = (ignoredPlayer, buffer) -> buffer == player;

        bridge.getMethod("install", BiConsumer.class, Consumer.class, BiPredicate.class, BiPredicate.class)
                .invoke(null, show, close, configuration, play);
        bridge.getMethod("show", Object.class, Object.class).invoke(null, player, dialog);
        bridge.getMethod("close", Object.class).invoke(null, player);

        assertSame(player, shownPlayer.get());
        assertSame(dialog, shownDialog.get());
        assertEquals(1, closes.get());
        assertTrue((boolean) bridge.getMethod("configurationClick", Object.class, Object.class)
                .invoke(null, player, dialog));
        assertTrue((boolean) bridge.getMethod("playClick", Object.class, Object.class)
                .invoke(null, player, player));

        bridge.getMethod("clear").invoke(null);
        bridge.getMethod("show", Object.class, Object.class).invoke(null, new Object(), new Object());
        bridge.getMethod("close", Object.class).invoke(null, player);
        assertSame(player, shownPlayer.get());
        assertSame(dialog, shownDialog.get());
        assertEquals(1, closes.get());
        assertFalse((boolean) bridge.getMethod("configurationClick", Object.class, Object.class)
                .invoke(null, player, dialog));
        assertFalse((boolean) bridge.getMethod("playClick", Object.class, Object.class)
                .invoke(null, player, player));
    }

    private static byte[] bridgeBytes() throws IOException {
        final String resource = '/' + BRIDGE.replace('.', '/') + ".class";
        try (InputStream input = VelocidialogBridgeTest.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private static final class IsolatedLoader extends ClassLoader {
        private IsolatedLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        private Class<?> defineBridge(final byte[] bytes) {
            return defineClass(BRIDGE, bytes, 0, bytes.length);
        }
    }
}
