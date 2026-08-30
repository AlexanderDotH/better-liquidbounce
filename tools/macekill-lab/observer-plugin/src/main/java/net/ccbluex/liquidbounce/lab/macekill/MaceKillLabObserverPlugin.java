/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.lab.macekill;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.Map;

public final class MaceKillLabObserverPlugin extends JavaPlugin {
    private LabRuntime runtime;
    private LabCommandHandler commands;
    private BukkitTask heartbeat;

    @Override
    public void onEnable() {
        runtime = new LabRuntime(this);
        commands = new LabCommandHandler(this, runtime);
        getServer().getPluginManager().registerEvents(new LabEventListener(this, runtime), this);
        try {
            runtime.openEvidence(getDataFolder().toPath().resolve("evidence"));
        } catch (IOException exception) {
            getLogger().severe("Cannot open lab evidence: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        heartbeat = getServer().getScheduler().runTaskTimer(this, runtime::recordHeartbeat, 1L, 1L);
        runtime.record("plugin_enabled", Map.of("profile", LabProfile.ID, "paper", getServer().getVersion()));
    }

    @Override
    public void onDisable() {
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        if (runtime != null) {
            runtime.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return commands.handle(sender, args);
    }
}
