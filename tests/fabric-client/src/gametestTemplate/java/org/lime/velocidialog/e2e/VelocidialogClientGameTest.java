package org.lime.velocidialog.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
//#switch PROPERTIES.versionMinecraft
//#caseof 1.21.6;1.21.8
//#default
//OF//import net.minecraft.client.input.KeyEvent;
//#endswitch
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Exercises CONFIGURATION and PLAY dialogs through a real Mojang client. */
@SuppressWarnings("UnstableApiUsage")
public final class VelocidialogClientGameTest implements FabricClientGameTest {
    private static final int CLIENT_START_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final int NETWORK_TIMEOUT_TICKS = 20 * 60 * 2;
    private static final int UI_TIMEOUT_TICKS = 20 * 60;
    private static final int DIALOG_OBSERVATION_TICKS = 20 * 2;
    private static final String SUBMIT = "VD-E2E-SUBMIT";

    @Override
    public void runTest(final ClientGameTestContext context) {
        final int protocol = Integer.parseInt(requiredEnvironment("VELOCIDIALOG_E2E_PROTOCOL"));
        final String proxyHost = environment("VELOCIDIALOG_E2E_PROXY_HOST", "127.0.0.1");
        final int proxyPort = Integer.parseInt(environment("VELOCIDIALOG_E2E_PROXY_PORT", "25565"));
        final int backendPort = Integer.parseInt(environment(
                "VELOCIDIALOG_E2E_BACKEND_PORT", "25566"));
        final Path results = Path.of(requiredEnvironment(
                "VELOCIDIALOG_E2E_RESULTS")).toAbsolutePath().normalize();
        final Properties serverProperties = new Properties();
        serverProperties.setProperty("server-ip", "127.0.0.1");
        serverProperties.setProperty("server-port", Integer.toString(backendPort));
        serverProperties.setProperty("online-mode", "false");
        serverProperties.setProperty("enforce-secure-profile", "false");
        serverProperties.setProperty("level-name", "velocidialog-e2e-" + protocol);

        try (TestDedicatedServerContext ignored =
                     context.worldBuilder().createServer(serverProperties)) {
            connect(context, proxyHost, proxyPort);

            awaitDialog(context, results, protocol, "VD-E2E-CONFIG-" + protocol,
                    CLIENT_START_TIMEOUT_TICKS);
            context.takeScreenshot("velocidialog-config-" + protocol);
            context.waitTicks(DIALOG_OBSERVATION_TICKS);
            fillAndSubmit(context, "config-" + protocol);

            context.waitFor(client -> client.level != null || failureExists(results, protocol),
                    NETWORK_TIMEOUT_TICKS);
            assertNoFailure(results, protocol);
            writeMarker(results.resolve("client-play-ready-" + protocol), "ready");

            awaitDialog(context, results, protocol, "VD-E2E-PLAY-" + protocol,
                    UI_TIMEOUT_TICKS);
            context.takeScreenshot("velocidialog-play-" + protocol);
            context.waitTicks(DIALOG_OBSERVATION_TICKS);
            fillAndSubmit(context, "play-" + protocol);
            writeMarker(results.resolve("client-play-submit-ready-" + protocol), "ready");
            context.waitFor(client -> isWaitingForResponse(client)
                            || failureExists(results, protocol),
                    UI_TIMEOUT_TICKS);
            assertNoFailure(results, protocol);
            writeMarker(results.resolve("client-close-show-ready-" + protocol), "ready");

            awaitDialog(context, results, protocol, "VD-E2E-CLOSE-" + protocol,
                    UI_TIMEOUT_TICKS);
            context.takeScreenshot("velocidialog-close-" + protocol);
            writeMarker(results.resolve("client-close-ready-" + protocol), "ready");
            context.waitFor(client -> Files.isRegularFile(results.resolve(
                    "close-" + protocol + ".ok")) || failureExists(results, protocol),
                    UI_TIMEOUT_TICKS);
            assertNoFailure(results, protocol);
            context.waitFor(client -> !hasDialogTitle(
                            client, "VD-E2E-CLOSE-" + protocol)
                            || failureExists(results, protocol),
                    UI_TIMEOUT_TICKS);
            assertNoFailure(results, protocol);

            context.waitFor(client -> Files.isRegularFile(results.resolve(
                    "play-" + protocol + ".ok")), UI_TIMEOUT_TICKS);

            context.runOnClient(client -> client.disconnect(new TitleScreen(), false));
            context.waitFor(client -> client.getConnection() == null, NETWORK_TIMEOUT_TICKS);
        }
    }

    private static void connect(
            final ClientGameTestContext context,
            final String host,
            final int port
    ) {
        final String address = host + ':' + port;
        context.runOnClient(client -> {
            final ServerData server = new ServerData(
                    "Velocidialog E2E", address, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), client,
                    ServerAddress.parseString(address), server, false, null);
        });
    }

    private static void awaitDialog(
            final ClientGameTestContext context,
            final Path results,
            final int protocol,
            final String title,
            final int timeoutTicks
    ) {
        try {
            context.waitFor(
                    client -> failureExists(results, protocol) || hasDialogTitle(client, title),
                    timeoutTicks);
        } catch (final AssertionError timeout) {
            final String screen = context.computeOnClient(client -> describeScreen(currentScreen(client)));
            context.takeScreenshot("velocidialog-missing-dialog-" + protocol);
            throw new AssertionError(
                    "Expected native dialog " + title + ", but current screen is " + screen,
                    timeout);
        }
        assertNoFailure(results, protocol);
        final boolean visible = context.computeOnClient(client -> hasDialogTitle(client, title));
        if (!visible) {
            throw new AssertionError("Expected native dialog " + title);
        }
    }

    private static boolean hasDialogTitle(final Minecraft client, final String title) {
        final Screen screen = currentScreen(client);
        return screen != null
                && screen.getClass().getName().contains(".gui.screens.dialog.")
                && title.equals(screen.getTitle().getString());
    }

    private static String describeScreen(final Screen screen) {
        return screen == null ? "<none>"
                : screen.getClass().getName() + "[title=" + screen.getTitle().getString() + ']';
    }

    private static boolean isWaitingForResponse(final Minecraft client) {
        final Screen screen = currentScreen(client);
        return screen != null
                && screen.getClass().getSimpleName().equals("WaitingForResponseScreen");
    }

    private static void fillAndSubmit(
            final ClientGameTestContext context,
            final String value
    ) {
        context.runOnClient(client -> {
            final Screen screen = currentScreen(client);
            if (screen == null) {
                throw new AssertionError("Dialog screen disappeared before interaction");
            }
            final List<GuiEventListener> descendants = new ArrayList<>();
            collectChildren(screen, descendants);
            final EditBox input = descendants.stream()
                    .filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Dialog text input was not found"));
            final Button submit = descendants.stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> SUBMIT.equals(button.getMessage().getString()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Dialog submit button was not found"));
            input.setValue(value);
            //#switch PROPERTIES.versionMinecraft
            //#caseof 1.21.6;1.21.8
            submit.onPress();
            //#default
            //OF//submit.onPress(new KeyEvent(257, 0, 0));
            //#endswitch
        });
    }

    private static Screen currentScreen(final Minecraft client) {
        //#switch PROPERTIES.versionMinecraft
        //#caseofregex ^(?:1\.21\.|26\.1\.2$)
        return client.screen;
        //#default
        //OF//return client.gui.screen();
        //#endswitch
    }

    private static void collectChildren(
            final GuiEventListener root,
            final List<GuiEventListener> output
    ) {
        if (!(root instanceof ContainerEventHandler container)) {
            return;
        }
        for (GuiEventListener child : container.children()) {
            output.add(child);
            collectChildren(child, output);
        }
    }

    private static boolean failureExists(final Path results, final int protocol) {
        return Files.isRegularFile(results.resolve(protocol + ".failure"));
    }

    private static void assertNoFailure(final Path results, final int protocol) {
        final Path failure = results.resolve(protocol + ".failure");
        if (!Files.isRegularFile(failure)) {
            return;
        }
        try {
            throw new AssertionError(Files.readString(failure));
        } catch (final IOException error) {
            throw new AssertionError("Velocity test driver failed", error);
        }
    }

    private static String requiredEnvironment(final String name) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }

    private static String environment(final String name, final String fallback) {
        final String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void writeMarker(final Path marker, final String value) {
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, value + System.lineSeparator());
        } catch (final IOException error) {
            throw new AssertionError("Unable to write Client Game Test marker " + marker, error);
        }
    }

}
