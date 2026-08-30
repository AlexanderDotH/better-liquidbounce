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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class LabRuntime {
    private final JavaPlugin plugin;
    private LabSession session;
    private EvidenceWriter evidence;
    private long labTick;

    LabRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void openEvidence(Path directory) throws IOException {
        evidence = EvidenceWriter.open(directory);
    }

    void close() {
        cleanupSession("plugin_disable");
        if (evidence != null) {
            evidence.close();
        }
    }

    boolean hasSession() {
        return session != null;
    }

    LabSession currentSession() {
        return session;
    }

    void activate(LabSession session) {
        this.session = session;
    }

    void recordHeartbeat() {
        labTick++;
        LabSession current = session;
        if (current == null) {
            return;
        }
        Player attacker = Bukkit.getPlayer(current.attackerId());
        Player target = Bukkit.getPlayer(current.targetId());
        if (attacker != null) {
            record("authoritative_position", observedPlayerFields(attacker));
        }
        if (target != null) {
            record("authoritative_position", observedPlayerFields(target));
        }
    }

    void recordTargetState(String event) {
        LabSession current = session;
        if (current == null) {
            return;
        }
        Player target = Bukkit.getPlayer(current.targetId());
        if (target == null) {
            return;
        }
        Map<String, Object> fields = observedPlayerFields(target);
        fields.put("health", target.getHealth());
        fields.put("dead", target.isDead());
        record(event, fields);
    }

    boolean isObserved(Player player) {
        LabSession current = session;
        return current != null && (player.getUniqueId().equals(current.attackerId())
            || player.getUniqueId().equals(current.targetId()));
    }

    Map<String, Object> observedPlayerFields(Player player) {
        Map<String, Object> fields = session == null ? new LinkedHashMap<>() : session.identityFields();
        fields.put("playerId", player.getUniqueId());
        fields.put("playerName", player.getName());
        fields.put("position", Position.of(player.getLocation()));
        fields.put("onGround", ((Entity) player).isOnGround());
        return fields;
    }

    void cleanupSession(String reason) {
        LabSession current = session;
        session = null;
        if (current == null) {
            return;
        }
        current.restore();
        Map<String, Object> fields = current.identityFields();
        fields.put("reason", reason);
        record("cleanup", fields);
    }

    void record(String event, Map<String, ?> fields) {
        if (evidence == null) {
            return;
        }
        try {
            evidence.write(event, labTick, fields);
        } catch (IOException exception) {
            plugin.getLogger().severe("Disabling evidence after write failure: " + exception.getMessage());
            evidence.close();
            evidence = null;
        }
    }
}
