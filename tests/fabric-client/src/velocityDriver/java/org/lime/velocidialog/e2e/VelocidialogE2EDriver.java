package org.lime.velocidialog.e2e;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.event.player.configuration.PlayerFinishedConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.Component;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.DialogBase.DialogAfterAction;
import org.lime.velocidialog.api.dialog.DialogResponseView;
import org.lime.velocidialog.api.dialog.Dialogs;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.input.DialogInput;
import org.lime.velocidialog.api.dialog.type.DialogType;
import org.slf4j.Logger;

/** Velocity-side controller for the real-client dialog round trip. Never published. */
public final class VelocidialogE2EDriver {
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CLIENT_PLAY_READY_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration CLIENT_PLAY_READY_POLL_INTERVAL = Duration.ofMillis(50);
    private static final Duration CLIENT_CLOSE_READY_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration CLIENT_CLOSE_READY_POLL_INTERVAL = Duration.ofMillis(50);
    private static final String INPUT_KEY = "value";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path results;

    @Inject
    public VelocidialogE2EDriver(final ProxyServer proxy, final Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        this.results = Path.of(System.getProperty(
                "velocidialog.e2e.results", "e2e-results")).toAbsolutePath().normalize();
    }

    @Subscribe
    public EventTask configure(final PlayerConfigurationEvent event) {
        final Player player = event.player();
        final int protocol = player.getProtocolVersion().getProtocol();
        final var response = Dialogs.showAndAwait(
                player,
                action -> form("VD-E2E-CONFIG-" + protocol, "VD-E2E-SUBMIT", action),
                RESPONSE_TIMEOUT
        ).thenAccept(view -> validateAndRecord(protocol, "config", view))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        recordFailure(protocol, unwrap(failure));
                    }
                })
                .toCompletableFuture();
        return EventTask.resumeWhenComplete(response);
    }

    @Subscribe
    public void finishedConfiguration(final PlayerFinishedConfigurationEvent event) {
        final Player player = event.player();
        final int protocol = player.getProtocolVersion().getProtocol();
        final Path ready = results.resolve("client-play-ready-" + protocol);
        final long deadline = System.nanoTime() + CLIENT_PLAY_READY_TIMEOUT.toNanos();
        proxy.getScheduler().buildTask(this, task -> {
            if (!Files.isRegularFile(ready)) {
                if (System.nanoTime() < deadline) {
                    return;
                }
                task.cancel();
                final IllegalStateException failure = new IllegalStateException(
                        "Client did not signal PLAY readiness within "
                                + CLIENT_PLAY_READY_TIMEOUT);
                recordFailure(protocol, failure);
                player.disconnect(Component.text(failure.getMessage()));
                return;
            }
            task.cancel();
            showPlayDialog(player, protocol);
        }).repeat(CLIENT_PLAY_READY_POLL_INTERVAL).schedule();
    }

    private void showPlayDialog(final Player player, final int protocol) {
        Dialogs.showAndAwait(
                player,
                action -> form("VD-E2E-PLAY-" + protocol, "VD-E2E-SUBMIT", action),
                RESPONSE_TIMEOUT
        ).whenComplete((view, failure) -> {
            if (failure != null) {
                recordFailure(protocol, unwrap(failure));
                return;
            }
            try {
                validateAndRecord(protocol, "play", view);
                closePlayWhenClientReady(player, protocol);
            } catch (final RuntimeException validationFailure) {
                recordFailure(protocol, validationFailure);
                player.disconnect(Component.text(validationFailure.getMessage()));
            }
        });
    }

    private void closePlayWhenClientReady(final Player player, final int protocol) {
        final Path ready = results.resolve("client-play-submit-ready-" + protocol);
        final long deadline = System.nanoTime() + CLIENT_CLOSE_READY_TIMEOUT.toNanos();
        proxy.getScheduler().buildTask(this, task -> {
            if (!Files.isRegularFile(ready)) {
                if (System.nanoTime() < deadline) {
                    return;
                }
                task.cancel();
                final IllegalStateException failure = new IllegalStateException(
                        "Client did not finish the PLAY submit within "
                                + CLIENT_CLOSE_READY_TIMEOUT);
                recordFailure(protocol, failure);
                player.disconnect(Component.text(failure.getMessage()));
                return;
            }
            task.cancel();
            showCloseWhenClientReady(player, protocol);
        }).repeat(CLIENT_CLOSE_READY_POLL_INTERVAL).schedule();
    }

    private void showCloseWhenClientReady(final Player player, final int protocol) {
        final Path ready = results.resolve("client-close-show-ready-" + protocol);
        final long deadline = System.nanoTime() + CLIENT_CLOSE_READY_TIMEOUT.toNanos();
        proxy.getScheduler().buildTask(this, task -> {
            if (!Files.isRegularFile(ready)) {
                if (System.nanoTime() < deadline) {
                    return;
                }
                task.cancel();
                final IllegalStateException failure = new IllegalStateException(
                        "Client did not signal readiness for the CLOSE dialog within "
                                + CLIENT_CLOSE_READY_TIMEOUT);
                recordFailure(protocol, failure);
                player.disconnect(Component.text(failure.getMessage()));
                return;
            }
            task.cancel();
            showCloseDialog(player, protocol);
        }).repeat(CLIENT_CLOSE_READY_POLL_INTERVAL).schedule();
    }

    private void showCloseDialog(final Player player, final int protocol) {
        try {
            player.showDialog(notice("VD-E2E-CLOSE-" + protocol));
            closeWhenClientReady(player, protocol);
        } catch (final RuntimeException failure) {
            recordFailure(protocol, failure);
        }
    }

    private void closeWhenClientReady(final Player player, final int protocol) {
        final Path ready = results.resolve("client-close-ready-" + protocol);
        final long deadline = System.nanoTime() + CLIENT_CLOSE_READY_TIMEOUT.toNanos();
        proxy.getScheduler().buildTask(this, task -> {
            if (!Files.isRegularFile(ready)) {
                if (System.nanoTime() < deadline) {
                    return;
                }
                task.cancel();
                final IllegalStateException failure = new IllegalStateException(
                        "Client did not observe the CLOSE dialog within "
                                + CLIENT_CLOSE_READY_TIMEOUT);
                recordFailure(protocol, failure);
                player.disconnect(Component.text(failure.getMessage()));
                return;
            }
            task.cancel();
            player.closeDialog();
            write(results.resolve("close-" + protocol + ".ok"),
                    "closed" + System.lineSeparator());
        }).repeat(CLIENT_CLOSE_READY_POLL_INTERVAL).schedule();
    }

    private static Dialog form(
            final String title,
            final String buttonLabel,
            final DialogAction.CustomClickAction action
    ) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(false)
                        .afterAction(DialogAfterAction.WAIT_FOR_RESPONSE)
                        .inputs(List.of(DialogInput.text(INPUT_KEY, Component.text("Value"))
                                .maxLength(64)
                                .build()))
                        .build())
                .type(DialogType.notice(ActionButton.builder(Component.text(buttonLabel))
                        .action(action)
                        .build())));
    }

    private static Dialog notice(final String title) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(false)
                        .build())
                .type(DialogType.notice()));
    }

    private void validateAndRecord(
            final int protocol,
            final String phase,
            final DialogResponseView response
    ) {
        final String expected = expectedValue(phase, protocol);
        final String actual = response.getText(INPUT_KEY);
        if (!expected.equals(actual)) {
            final IllegalStateException failure = new IllegalStateException(
                    "Expected " + INPUT_KEY + "=" + expected + " but received " + actual);
            throw failure;
        }
        write(results.resolve(phase + '-' + protocol + ".ok"), expected + System.lineSeparator());
        logger.info("Velocidialog E2E {} callback succeeded for protocol {}", phase, protocol);
    }

    private void recordFailure(final int protocol, final Throwable failure) {
        write(results.resolve(protocol + ".failure"), failure.toString() + System.lineSeparator());
        logger.error("Velocidialog E2E failed for protocol {}", protocol, failure);
    }

    private static void write(final Path file, final String value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, value, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (final IOException error) {
            throw new IllegalStateException("Unable to write E2E result " + file, error);
        }
    }

    private static String expectedValue(final String phase, final int protocol) {
        return phase + '-' + protocol;
    }

    private static Throwable unwrap(final Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }
}
