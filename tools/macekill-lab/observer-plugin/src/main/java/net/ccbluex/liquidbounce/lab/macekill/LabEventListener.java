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

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;

final class LabEventListener implements Listener {
    private final JavaPlugin plugin;
    private final LabRuntime runtime;

    LabEventListener(JavaPlugin plugin, LabRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!runtime.isObserved(event.getPlayer()) || Objects.equals(event.getFrom(), event.getTo())) {
            return;
        }
        Map<String, Object> fields = runtime.observedPlayerFields(event.getPlayer());
        fields.put("from", Position.of(event.getFrom()));
        fields.put("to", Position.of(event.getTo()));
        fields.put("cancelled", event.isCancelled());
        runtime.record("move", fields);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!runtime.isObserved(event.getPlayer())) {
            return;
        }
        Map<String, Object> fields = runtime.observedPlayerFields(event.getPlayer());
        fields.put("from", Position.of(event.getFrom()));
        fields.put("to", Position.of(event.getTo()));
        fields.put("cause", event.getCause().name());
        fields.put("cancelled", event.isCancelled());
        runtime.record("server_teleport", fields);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        LabSession session = runtime.currentSession();
        if (session == null || !event.getEntity().getUniqueId().equals(session.targetId())) {
            return;
        }
        Map<String, Object> fields = session.identityFields();
        fields.put("damager", entityIdentity(event.getDamager()));
        fields.put("cause", event.getCause().name());
        fields.put("damage", event.getDamage());
        fields.put("finalDamage", event.getFinalDamage());
        fields.put("cancelled", event.isCancelled());
        fields.put("targetPosition", Position.of(event.getEntity().getLocation()));
        runtime.record("damage", fields);
        plugin.getServer().getScheduler().runTask(plugin, () -> runtime.recordTargetState("damage_applied"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        LabSession session = runtime.currentSession();
        if (session == null || !event.getPlayer().getUniqueId().equals(session.targetId())) {
            return;
        }
        Map<String, Object> fields = session.identityFields();
        fields.put("position", Position.of(event.getPlayer().getLocation()));
        fields.put("message", String.valueOf(event.deathMessage()));
        runtime.record("death", fields);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (runtime.isObserved(event.getPlayer())) {
            runtime.record("disconnect", runtime.observedPlayerFields(event.getPlayer()));
        }
    }

    private static Map<String, Object> entityIdentity(Entity entity) {
        return Map.of("id", entity.getUniqueId(), "type", entity.getType().name(), "name", entity.getName());
    }
}
