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
package net.ccbluex.liquidbounce.features.trialchamber

import java.util.UUID

/** Immutable block coordinate used at the runtime, HUD, and renderer boundary. */
data class TrialChamberPosition(val x: Int, val y: Int, val z: Int) : Comparable<TrialChamberPosition> {

    fun squaredDistanceTo(other: TrialChamberPosition): Double {
        val dx = x.toDouble() - other.x
        val dy = y.toDouble() - other.y
        val dz = z.toDouble() - other.z
        return dx * dx + dy * dy + dz * dz
    }

    override fun compareTo(other: TrialChamberPosition): Int =
        compareValuesBy(this, other, TrialChamberPosition::x, TrialChamberPosition::y, TrialChamberPosition::z)
}

enum class TrialChamberAnchorType {
    TRIAL_SPAWNER,
    VAULT,
    OMINOUS_VAULT,
}

data class TrialChamberAnchorSnapshot(
    val position: TrialChamberPosition,
    val type: TrialChamberAnchorType,
)

enum class TrialSpawnerPhase {
    INACTIVE,
    WAITING_FOR_PLAYERS,
    ACTIVE,
    WAITING_FOR_REWARD_EJECTION,
    EJECTING_REWARD,
    COOLDOWN,
}

data class TrialSpawnerSnapshot(
    val position: TrialChamberPosition,
    val phase: TrialSpawnerPhase,
    val ominous: Boolean,
    val expectedEntityType: String?,
) {
    val completed: Boolean
        get() = phase == TrialSpawnerPhase.COOLDOWN
}

enum class TrialVaultStatus {
    AVAILABLE,
    CLAIMED,
    UNKNOWN,
}

data class TrialVaultSnapshot(
    val position: TrialChamberPosition,
    val ominous: Boolean,
    val status: TrialVaultStatus,
) {
    val completed: Boolean
        get() = status == TrialVaultStatus.CLAIMED
}

enum class TrialLootType {
    CHEST,
    BARREL,
    POT,
    DISPENSER,
}

data class TrialLootSnapshot(
    val position: TrialChamberPosition,
    val type: TrialLootType,
    val visited: Boolean,
)

data class TrialMobSnapshot(
    val uuid: UUID,
    val entityType: String,
    val position: TrialChamberPosition,
    val originSpawner: TrialChamberPosition,
    val alive: Boolean,
)

/**
 * Stable, immutable view published once per client tick.
 *
 * Minecraft objects never cross this boundary. Every collection is defensively copied so render,
 * targeting, and HUD readers can safely retain a snapshot while the next tick is reduced.
 */
class TrialChamberSnapshot private constructor(
    val worldEpoch: Long,
    val revision: Long,
    val playerInsideChamber: Boolean,
    anchors: Collection<TrialChamberAnchorSnapshot>,
    spawners: Collection<TrialSpawnerSnapshot>,
    mobs: Collection<TrialMobSnapshot>,
    vaults: Collection<TrialVaultSnapshot>,
    loot: Collection<TrialLootSnapshot>,
) {

    val anchors: List<TrialChamberAnchorSnapshot> = java.util.List.copyOf(anchors)
    val spawners: List<TrialSpawnerSnapshot> = java.util.List.copyOf(spawners)
    val mobs: List<TrialMobSnapshot> = java.util.List.copyOf(mobs)
    val vaults: List<TrialVaultSnapshot> = java.util.List.copyOf(vaults)
    val loot: List<TrialLootSnapshot> = java.util.List.copyOf(loot)
    val currentTrialMobIds: Set<UUID> = java.util.Set.copyOf(this.mobs.map(TrialMobSnapshot::uuid))

    fun isCurrentTrialMob(uuid: UUID): Boolean = uuid in currentTrialMobIds

    companion object {
        fun create(
            worldEpoch: Long,
            revision: Long,
            playerInsideChamber: Boolean,
            anchors: Collection<TrialChamberAnchorSnapshot>,
            spawners: Collection<TrialSpawnerSnapshot>,
            mobs: Collection<TrialMobSnapshot>,
            vaults: Collection<TrialVaultSnapshot>,
            loot: Collection<TrialLootSnapshot>,
        ) = TrialChamberSnapshot(
            worldEpoch,
            revision,
            playerInsideChamber,
            anchors,
            spawners,
            mobs,
            vaults,
            loot,
        )
    }
}
