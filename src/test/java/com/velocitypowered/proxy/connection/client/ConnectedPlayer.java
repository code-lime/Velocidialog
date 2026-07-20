package com.velocitypowered.proxy.connection.client;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;

/** Test descriptor-compatible stand-in for Velocity's internal ConnectedPlayer class. */
public interface ConnectedPlayer extends Player {
    MinecraftConnection getConnection();
}
