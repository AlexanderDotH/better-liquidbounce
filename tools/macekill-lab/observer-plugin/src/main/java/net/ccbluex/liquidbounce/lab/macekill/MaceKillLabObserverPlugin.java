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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MaceKillLabObserverPlugin extends JavaPlugin implements Listener {
    private static final double LAB_TARGET_HEALTH = 200.0;
    private static final String PROFILE_ID = "paper-26.2-build-112-unvalidated";

    private LabSession session;
    private EvidenceWriter evidence;
    private BukkitTask heartbeat;
    private long labTick;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        try {
            evidence = EvidenceWriter.open(getDataFolder().toPath().resolve("evidence"));
        } catch (IOException exception) {
            getLogger().severe("Cannot open lab evidence: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        heartbeat = getServer().getScheduler().runTaskTimer(this, this::recordHeartbeat, 1L, 1L);
        record("plugin_enabled", Map.of("profile", PROFILE_ID, "paper", getServer().getVersion()));
    }

    @Override
    public void onDisable() {
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        cleanupSession("plugin_disable");
        if (evidence != null) {
            evidence.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
        if (session != null) {
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
            session = LabSession.arm(attacker, target);
        } catch (RuntimeException exception) {
            sender.sendMessage("Failed to arm the lab: " + exception.getMessage());
            getLogger().warning("Failed to arm lab: " + exception);
            return true;
        }
        record("armed", session.identityFields());
        sender.sendMessage("MaceKill lab armed with profile " + PROFILE_ID + ".");
        return true;
    }

    private boolean mark(CommandSender sender, String[] args) {
        if (session == null) {
            sender.sendMessage("No lab session is armed.");
            return true;
        }
        if (args.length != 2 || args[1].length() > 128) {
            sender.sendMessage("Usage: /macekilllab mark <client-session-id> (max 128 characters)");
            return true;
        }
        session.clientSessionId = args[1];
        record("client_mark", session.identityFields());
        sender.sendMessage("Client session marker recorded.");
        return true;
    }

    private boolean status(CommandSender sender) {
        if (session == null) {
            sender.sendMessage("MaceKill lab is idle.");
            return true;
        }
        sender.sendMessage("Armed: attacker=" + session.attackerName + ", target=" + session.targetName
            + ", clientSession=" + session.clientSessionId);
        return true;
    }

    private boolean cleanup(CommandSender sender) {
        if (session == null) {
            sender.sendMessage("MaceKill lab is already idle.");
            return true;
        }
        cleanupSession("command_cleanup");
        sender.sendMessage("MaceKill lab blocks and target state restored.");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!isObserved(event.getPlayer()) || Objects.equals(event.getFrom(), event.getTo())) {
            return;
        }
        Map<String, Object> fields = observedPlayerFields(event.getPlayer());
        fields.put("from", Position.of(event.getFrom()));
        fields.put("to", Position.of(event.getTo()));
        fields.put("cancelled", event.isCancelled());
        record("move", fields);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isObserved(event.getPlayer())) {
            return;
        }
        Map<String, Object> fields = observedPlayerFields(event.getPlayer());
        fields.put("from", Position.of(event.getFrom()));
        fields.put("to", Position.of(event.getTo()));
        fields.put("cause", event.getCause().name());
        fields.put("cancelled", event.isCancelled());
        record("server_teleport", fields);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        LabSession current = session;
        if (current == null || !event.getEntity().getUniqueId().equals(current.targetId)) {
            return;
        }
        Map<String, Object> fields = current.identityFields();
        fields.put("damager", entityIdentity(event.getDamager()));
        fields.put("cause", event.getCause().name());
        fields.put("damage", event.getDamage());
        fields.put("finalDamage", event.getFinalDamage());
        fields.put("cancelled", event.isCancelled());
        fields.put("targetPosition", Position.of(event.getEntity().getLocation()));
        record("damage", fields);
        getServer().getScheduler().runTask(this, () -> recordTargetState("damage_applied"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        LabSession current = session;
        if (current == null || !event.getPlayer().getUniqueId().equals(current.targetId)) {
            return;
        }
        Map<String, Object> fields = current.identityFields();
        fields.put("position", Position.of(event.getPlayer().getLocation()));
        fields.put("message", String.valueOf(event.deathMessage()));
        record("death", fields);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!isObserved(event.getPlayer())) {
            return;
        }
        record("disconnect", observedPlayerFields(event.getPlayer()));
    }

    private void recordHeartbeat() {
        labTick++;
        LabSession current = session;
        if (current == null) {
            return;
        }
        Player attacker = Bukkit.getPlayer(current.attackerId);
        Player target = Bukkit.getPlayer(current.targetId);
        if (attacker != null) {
            record("authoritative_position", observedPlayerFields(attacker));
        }
        if (target != null) {
            record("authoritative_position", observedPlayerFields(target));
        }
    }

    private void recordTargetState(String event) {
        LabSession current = session;
        if (current == null) {
            return;
        }
        Player target = Bukkit.getPlayer(current.targetId);
        if (target == null) {
            return;
        }
        Map<String, Object> fields = observedPlayerFields(target);
        fields.put("health", target.getHealth());
        fields.put("dead", target.isDead());
        record(event, fields);
    }

    private boolean isObserved(Player player) {
        LabSession current = session;
        return current != null && (player.getUniqueId().equals(current.attackerId)
            || player.getUniqueId().equals(current.targetId));
    }

    private Map<String, Object> observedPlayerFields(Player player) {
        Map<String, Object> fields = session == null ? new LinkedHashMap<>() : session.identityFields();
        fields.put("playerId", player.getUniqueId());
        fields.put("playerName", player.getName());
        fields.put("position", Position.of(player.getLocation()));
        fields.put("onGround", ((Entity) player).isOnGround());
        return fields;
    }

    private void cleanupSession(String reason) {
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

    private void record(String event, Map<String, ?> fields) {
        if (evidence == null) {
            return;
        }
        try {
            evidence.write(event, labTick, fields);
        } catch (IOException exception) {
            getLogger().severe("Disabling evidence after write failure: " + exception.getMessage());
            evidence.close();
            evidence = null;
        }
    }

    private static Map<String, Object> entityIdentity(Entity entity) {
        return Map.of("id", entity.getUniqueId(), "type", entity.getType().name(), "name", entity.getName());
    }

    private static final class LabSession {
        private final UUID attackerId;
        private final String attackerName;
        private final UUID targetId;
        private final String targetName;
        private final Location originalTargetPosition;
        private final double originalTargetHealth;
        private final double originalMaxHealth;
        private final Map<Block, BlockData> replacedBlocks;
        private String clientSessionId = "UNMARKED";

        private LabSession(
            Player attacker,
            Player target,
            Location originalTargetPosition,
            double originalTargetHealth,
            double originalMaxHealth,
            Map<Block, BlockData> replacedBlocks
        ) {
            this.attackerId = attacker.getUniqueId();
            this.attackerName = attacker.getName();
            this.targetId = target.getUniqueId();
            this.targetName = target.getName();
            this.originalTargetPosition = originalTargetPosition;
            this.originalTargetHealth = originalTargetHealth;
            this.originalMaxHealth = originalMaxHealth;
            this.replacedBlocks = replacedBlocks;
        }

        private static LabSession arm(Player attacker, Player target) {
            AttributeInstance maxHealth = Objects.requireNonNull(target.getAttribute(Attribute.MAX_HEALTH));
            Location originalPosition = target.getLocation().clone();
            double originalHealth = target.getHealth();
            double originalMaxHealth = maxHealth.getBaseValue();
            Map<Block, BlockData> replaced = buildCell(target.getLocation());
            try {
                Location cellCenter = target.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                if (!target.teleport(cellCenter)) {
                    throw new IllegalStateException("target teleport was rejected");
                }
                maxHealth.setBaseValue(LAB_TARGET_HEALTH);
                target.setHealth(LAB_TARGET_HEALTH);
                return new LabSession(
                    attacker,
                    target,
                    originalPosition,
                    originalHealth,
                    originalMaxHealth,
                    replaced
                );
            } catch (RuntimeException exception) {
                replaced.forEach((block, data) -> block.setBlockData(data, false));
                maxHealth.setBaseValue(originalMaxHealth);
                target.teleport(originalPosition);
                target.setHealth(Math.min(originalHealth, originalMaxHealth));
                throw exception;
            }
        }

        private static Map<Block, BlockData> buildCell(Location center) {
            Map<Block, BlockData> replaced = new LinkedHashMap<>();
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Block block = center.getBlock().getRelative(x, y, z);
                        replaced.put(block, block.getBlockData().clone());
                        boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3;
                        block.setType(shell ? Material.OBSIDIAN : Material.AIR, false);
                    }
                }
            }
            return replaced;
        }

        private void restore() {
            replacedBlocks.forEach((block, data) -> block.setBlockData(data, false));
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                return;
            }
            AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                if (!target.isDead()) {
                    target.setHealth(Math.min(originalTargetHealth, originalMaxHealth));
                }
                maxHealth.setBaseValue(originalMaxHealth);
            }
            if (!target.isDead()) {
                target.teleport(originalTargetPosition);
            }
        }

        private Map<String, Object> identityFields() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("profile", PROFILE_ID);
            fields.put("clientSessionId", clientSessionId);
            fields.put("attackerId", attackerId);
            fields.put("attackerName", attackerName);
            fields.put("targetId", targetId);
            fields.put("targetName", targetName);
            return fields;
        }
    }

    private record Position(String world, double x, double y, double z, float yaw, float pitch) {
        private static Position of(Location location) {
            return new Position(
                location.getWorld() == null ? "null" : location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
            );
        }
    }

    private static final class EvidenceWriter implements AutoCloseable {
        private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneOffset.UTC);
        private final BufferedWriter writer;

        private EvidenceWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        private static EvidenceWriter open(Path directory) throws IOException {
            Files.createDirectories(directory);
            Path file = directory.resolve("macekill-lab_" + FILE_TIME.format(Instant.now()) + ".jsonl");
            return new EvidenceWriter(Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            ));
        }

        private synchronized void write(String event, long tick, Map<String, ?> fields) throws IOException {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("schemaVersion", 1);
            entry.put("time", Instant.now().toString());
            entry.put("tick", tick);
            entry.put("event", event);
            entry.putAll(fields);
            writer.write(Json.encode(entry));
            writer.newLine();
            writer.flush();
        }

        @Override
        public synchronized void close() {
            try {
                writer.close();
            } catch (IOException ignored) {
                // The first evidence failure was already reported by the plugin.
            }
        }
    }

    private static final class Json {
        private Json() {
        }

        private static String encode(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            if (value instanceof Map<?, ?> map) {
                StringBuilder result = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        result.append(',');
                    }
                    first = false;
                    result.append(encode(String.valueOf(entry.getKey()))).append(':').append(encode(entry.getValue()));
                }
                return result.append('}').toString();
            }
            if (value instanceof Position position) {
                return encode(Map.of(
                    "world", position.world(),
                    "x", position.x(),
                    "y", position.y(),
                    "z", position.z(),
                    "yaw", position.yaw(),
                    "pitch", position.pitch()
                ));
            }
            return '"' + escape(String.valueOf(value)) + '"';
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        }
    }
}
