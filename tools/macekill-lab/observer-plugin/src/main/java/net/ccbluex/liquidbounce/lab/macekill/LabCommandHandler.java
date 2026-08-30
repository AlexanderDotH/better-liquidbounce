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

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class LabCommandHandler {
    private final JavaPlugin plugin;
    private final LabRuntime runtime;

    LabCommandHandler(JavaPlugin plugin, LabRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /macekilllab <arm|mark|status|cleanup>");
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "arm" -> arm(sender, args);
            case "mark" -> mark(sender, args);
            case "status" -> status(sender);
            case "cleanup" -> cleanup(sender);
            default -> false;
        };
    }

    private boolean arm(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /macekilllab arm <attacker> <target>");
            return true;
        }
        if (runtime.hasSession()) {
            sender.sendMessage("A lab session is already armed; clean it up first.");
            return true;
        }
        Player attacker = Bukkit.getPlayerExact(args[1]);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (attacker == null || target == null || attacker == target) {
            sender.sendMessage("Attacker and target must be different online players.");
            return true;
        }

        try {
            runtime.activate(LabSession.arm(attacker, target));
        } catch (RuntimeException exception) {
            sender.sendMessage("Failed to arm the lab: " + exception.getMessage());
            plugin.getLogger().warning("Failed to arm lab: " + exception);
            return true;
        }
        runtime.record("armed", runtime.currentSession().identityFields());
        sender.sendMessage("MaceKill lab armed with profile " + LabProfile.ID + ".");
        return true;
    }

    private boolean mark(CommandSender sender, String[] args) {
        LabSession session = runtime.currentSession();
        if (session == null) {
            sender.sendMessage("No lab session is armed.");
            return true;
        }
        if (args.length != 2 || args[1].length() > 128) {
            sender.sendMessage("Usage: /macekilllab mark <client-session-id> (max 128 characters)");
            return true;
        }
        session.mark(args[1]);
        runtime.record("client_mark", session.identityFields());
        sender.sendMessage("Client session marker recorded.");
        return true;
    }

    private boolean status(CommandSender sender) {
        LabSession session = runtime.currentSession();
        if (session == null) {
            sender.sendMessage("MaceKill lab is idle.");
            return true;
        }
        sender.sendMessage("Armed: attacker=" + session.attackerName() + ", target=" + session.targetName()
            + ", clientSession=" + session.clientSessionId());
        return true;
    }

    private boolean cleanup(CommandSender sender) {
        if (!runtime.hasSession()) {
            sender.sendMessage("MaceKill lab is already idle.");
            return true;
        }
        runtime.cleanupSession("command_cleanup");
        sender.sendMessage("MaceKill lab blocks and target state restored.");
        return true;
    }
}
