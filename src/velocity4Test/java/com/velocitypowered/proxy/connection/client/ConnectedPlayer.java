package com.velocitypowered.proxy.connection.client;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;

/** Velocity 4 test descriptor stand-in. */
public interface ConnectedPlayer extends Player {
    MinecraftConnection getConnection();
}
