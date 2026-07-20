package com.velocitypowered.api.proxy;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Tiny parent-classloader bridge used by inlined Byte Buddy advice.
 *
 * <p>This class is shipped as bytes in the plugin and defined in Velocity's classloader before
 * any transformation is installed. It must only mention bootstrap/JDK-visible types.</p>
 */
public final class VelocidialogBridge {
    private static volatile BiConsumer<Object, Object> show;
    private static volatile Consumer<Object> close;
    private static volatile BiPredicate<Object, Object> configurationClick;
    private static volatile BiPredicate<Object, Object> playClick;

    private VelocidialogBridge() {
    }

    public static void install(
            final BiConsumer<Object, Object> showHandler,
            final Consumer<Object> closeHandler,
            final BiPredicate<Object, Object> configurationClickHandler,
            final BiPredicate<Object, Object> playClickHandler) {
        show = showHandler;
        close = closeHandler;
        configurationClick = configurationClickHandler;
        playClick = playClickHandler;
    }

    public static void clear() {
        show = null;
        close = null;
        configurationClick = null;
        playClick = null;
    }

    public static void show(final Object player, final Object dialog) {
        final BiConsumer<Object, Object> handler = show;
        if (handler != null) {
            handler.accept(player, dialog);
        }
    }

    public static void close(final Object player) {
        final Consumer<Object> handler = close;
        if (handler != null) {
            handler.accept(player);
        }
    }

    public static boolean configurationClick(final Object player, final Object packet) {
        final BiPredicate<Object, Object> handler = configurationClick;
        return handler != null && handler.test(player, packet);
    }

    public static boolean playClick(final Object player, final Object buffer) {
        final BiPredicate<Object, Object> handler = playClick;
        return handler != null && handler.test(player, buffer);
    }
}

