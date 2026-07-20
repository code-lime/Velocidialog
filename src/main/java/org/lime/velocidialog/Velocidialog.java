package org.lime.velocidialog;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.configuration.PlayerEnteredConfigurationEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.lime.velocidialog.api.dialog.Dialogs;
import org.lime.velocidialog.inject.VelocityInjector;
import org.lime.velocidialog.runtime.VelocityDialogRuntime;
import org.slf4j.Logger;

/** Velocity bootstrap for native dialogs in CONFIGURATION and PLAY. */
public final class Velocidialog {
    private final Logger logger;
    private final VelocityDialogRuntime runtime;
    private final VelocityInjector injector;

    @Inject
    public Velocidialog(final ProxyServer proxy, final Logger logger) {
        this.logger = logger;
        this.runtime = new VelocityDialogRuntime(proxy, this, logger);
        VelocityInjector installedInjector = null;
        try {
            installedInjector = VelocityInjector.install(
                    runtime::showFromBridge,
                    runtime::closeFromBridge,
                    runtime::handleConfigurationClick,
                    runtime::handlePlayClick);
            Dialogs.installProvider(runtime);
            this.injector = installedInjector;
        } catch (final RuntimeException | Error startupFailure) {
            if (installedInjector != null) {
                try {
                    installedInjector.close();
                } catch (final RuntimeException | Error rollbackFailure) {
                    startupFailure.addSuppressed(rollbackFailure);
                }
            }
            try {
                runtime.close();
            } catch (final RuntimeException | Error rollbackFailure) {
                startupFailure.addSuppressed(rollbackFailure);
            }
            throw startupFailure;
        }
    }

    @Subscribe
    public void onInitialize(final ProxyInitializeEvent event) {
        runtime.start();
        logger.info("Velocidialog initialized: native dialogs are available in CONFIGURATION and PLAY");
    }

    @Subscribe
    public void onEnteredConfiguration(final PlayerEnteredConfigurationEvent event) {
        runtime.enteredConfiguration(event.player());
    }

    @Subscribe
    public void onDisconnect(final DisconnectEvent event) {
        runtime.disconnected(event.getPlayer());
    }

    @Subscribe
    public void onShutdown(final ProxyShutdownEvent event) {
        Dialogs.clearProvider(runtime);
        try {
            injector.close();
        } finally {
            runtime.close();
        }
    }
}
