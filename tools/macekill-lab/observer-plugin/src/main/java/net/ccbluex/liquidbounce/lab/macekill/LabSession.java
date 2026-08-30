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

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class LabSession {
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

    static LabSession arm(Player attacker, Player target) {
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
            maxHealth.setBaseValue(LabProfile.TARGET_HEALTH);
            target.setHealth(LabProfile.TARGET_HEALTH);
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

    UUID attackerId() {
        return attackerId;
    }

    String attackerName() {
        return attackerName;
    }

    UUID targetId() {
        return targetId;
    }

    String targetName() {
        return targetName;
    }

    String clientSessionId() {
        return clientSessionId;
    }

    void mark(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }

    void restore() {
        replacedBlocks.forEach((block, data) -> block.setBlockData(data, false));
        Player target = org.bukkit.Bukkit.getPlayer(targetId);
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

    Map<String, Object> identityFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("profile", LabProfile.ID);
        fields.put("clientSessionId", clientSessionId);
        fields.put("attackerId", attackerId);
        fields.put("attackerName", attackerName);
        fields.put("targetId", targetId);
        fields.put("targetName", targetName);
        return fields;
    }

    private static Map<Block, BlockData> buildCell(Location center) {
        Map<Block, BlockData> replaced = new LinkedHashMap<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                replaceRow(center, replaced, x, y);
            }
        }
        return replaced;
    }

    private static void replaceRow(Location center, Map<Block, BlockData> replaced, int x, int y) {
        for (int z = -2; z <= 2; z++) {
            Block block = center.getBlock().getRelative(x, y, z);
            replaced.put(block, block.getBlockData().clone());
            boolean shell = LabCellGeometry.isShell(x, y, z);
            block.setType(shell ? Material.OBSIDIAN : Material.AIR, false);
        }
    }
}
