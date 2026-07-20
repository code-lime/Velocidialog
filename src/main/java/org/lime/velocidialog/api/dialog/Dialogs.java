package org.lime.velocidialog.api.dialog;

import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus;
import org.lime.velocidialog.api.dialog.action.DialogAction;

/** Runtime operations that need the installed Velocidialog proxy plugin. */
public final class Dialogs {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static volatile Provider provider;

    private Dialogs() {
    }

    /**
     * Shows a one-shot submit dialog and completes with its untrusted response.
     *
     * <p>When called from a {@code PlayerConfigurationEvent} listener, return
     * {@code EventTask.resumeWhenComplete(stage.toCompletableFuture())} from the listener. The
     * event task, rather than this method, is what keeps Velocity in CONFIGURATION.</p>
     */
    public static CompletionStage<DialogResponseView> showAndAwait(
            final Player player,
            final Function<DialogAction.CustomClickAction, Dialog> factory,
            final Duration timeout) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        final Provider current = provider;
        if (current == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Velocidialog is not initialized on this proxy"));
        }
        return current.showAndAwait(player, factory, timeout);
    }

    /** Uses the default timeout of 30 seconds. */
    public static CompletionStage<DialogResponseView> showAndAwait(
            final Player player,
            final Function<DialogAction.CustomClickAction, Dialog> factory) {
        return showAndAwait(player, factory, DEFAULT_TIMEOUT);
    }

    @ApiStatus.Internal
    public static void installProvider(final Provider newProvider) {
        provider = Objects.requireNonNull(newProvider, "newProvider");
    }

    @ApiStatus.Internal
    public static void clearProvider(final Provider oldProvider) {
        if (provider == oldProvider) {
            provider = null;
        }
    }

    @ApiStatus.Internal
    @FunctionalInterface
    public interface Provider {
        CompletionStage<DialogResponseView> showAndAwait(
                Player player,
                Function<DialogAction.CustomClickAction, Dialog> factory,
                Duration timeout);
    }
}
