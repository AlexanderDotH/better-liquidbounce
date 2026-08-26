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
package net.ccbluex.liquidbounce.integration.theme.component.components.trialchamber

/** Semantic keys rendered by the native Trial Chamber HUD. Declaration order is presentation order. */
internal enum class TrialChamberHudMetric {
    SPAWNER_INACTIVE,
    SPAWNER_WAITING_FOR_PLAYERS,
    SPAWNER_ACTIVE,
    SPAWNER_WAITING_FOR_REWARD_EJECTION,
    SPAWNER_EJECTING_REWARD,
    SPAWNER_COOLDOWN,
    LIVING_TRIAL_MOBS,
    VAULT_AVAILABLE,
    VAULT_CLAIMED,
    VAULT_UNKNOWN,
    LOOT_CHEST,
    LOOT_BARREL,
    LOOT_POT,
    LOOT_DISPENSER,
}

internal enum class TrialSpawnerHudPhase(val metric: TrialChamberHudMetric) {
    INACTIVE(TrialChamberHudMetric.SPAWNER_INACTIVE),
    WAITING_FOR_PLAYERS(TrialChamberHudMetric.SPAWNER_WAITING_FOR_PLAYERS),
    ACTIVE(TrialChamberHudMetric.SPAWNER_ACTIVE),
    WAITING_FOR_REWARD_EJECTION(TrialChamberHudMetric.SPAWNER_WAITING_FOR_REWARD_EJECTION),
    EJECTING_REWARD(TrialChamberHudMetric.SPAWNER_EJECTING_REWARD),
    COOLDOWN(TrialChamberHudMetric.SPAWNER_COOLDOWN),
}

internal enum class TrialVaultHudStatus(val metric: TrialChamberHudMetric) {
    AVAILABLE(TrialChamberHudMetric.VAULT_AVAILABLE),
    CLAIMED(TrialChamberHudMetric.VAULT_CLAIMED),
    UNKNOWN(TrialChamberHudMetric.VAULT_UNKNOWN),
}

internal enum class TrialLootHudType(val metric: TrialChamberHudMetric) {
    CHEST(TrialChamberHudMetric.LOOT_CHEST),
    BARREL(TrialChamberHudMetric.LOOT_BARREL),
    POT(TrialChamberHudMetric.LOOT_POT),
    DISPENSER(TrialChamberHudMetric.LOOT_DISPENSER),
}

internal data class TrialChamberHudMob(
    val isCurrentTrialMob: Boolean,
    val isAlive: Boolean,
)

internal data class TrialChamberHudLoot(
    val type: TrialLootHudType,
    val isVisited: Boolean,
)

/**
 * Immutable projection boundary between the runtime snapshot and HUD aggregation.
 *
 * The builder consumes the collections immediately and never retains this input.
 */
internal data class TrialChamberHudInput(
    val spawnerPhases: List<TrialSpawnerHudPhase> = emptyList(),
    val trialMobs: List<TrialChamberHudMob> = emptyList(),
    val vaultStatuses: List<TrialVaultHudStatus> = emptyList(),
    val loot: List<TrialChamberHudLoot> = emptyList(),
)

internal enum class TrialChamberHudSection {
    SPAWNERS,
    TRIAL_MOBS,
    VAULTS,
    LOOT,
}

internal data class TrialChamberHudEntry(
    val metric: TrialChamberHudMetric,
    val count: Int,
) {
    init {
        require(count >= 0) { "HUD counts cannot be negative" }
    }
}

/** One compact native-renderer line containing a stable sequence of observed counts. */
internal data class TrialChamberHudLine(
    val section: TrialChamberHudSection,
    val entries: List<TrialChamberHudEntry>,
) {
    init {
        require(entries.isNotEmpty()) { "HUD lines must contain at least one count" }
        require(entries.distinctBy(TrialChamberHudEntry::metric).size == entries.size) {
            "HUD line metrics must be unique"
        }
    }
}

internal data class TrialChamberHudModel(val lines: List<TrialChamberHudLine>) {
    init {
        require(lines.distinctBy(TrialChamberHudLine::section).size == lines.size) {
            "HUD sections must be unique"
        }
    }

    fun line(section: TrialChamberHudSection): TrialChamberHudLine? = lines.firstOrNull { it.section == section }

    fun count(metric: TrialChamberHudMetric): Int = lines
        .asSequence()
        .flatMap { it.entries.asSequence() }
        .firstOrNull { it.metric == metric }
        ?.count
        ?: 0
}

/** Returns no model unless the tracker runs and the player is physically inside the selected chamber. */
internal fun buildTrialChamberHudModel(
    trackerRunning: Boolean,
    playerInsideChamber: Boolean,
    currentChamber: TrialChamberHudInput?,
): TrialChamberHudModel? {
    if (!trackerRunning || !playerInsideChamber || currentChamber == null) return null
    if (TrialSpawnerHudPhase.ACTIVE !in currentChamber.spawnerPhases) return null

    return TrialChamberHudModel(buildList {
        addObservedLine(
            TrialChamberHudSection.SPAWNERS,
            observedCounts(currentChamber.spawnerPhases, TrialSpawnerHudPhase.entries) { it.metric },
        )
        add(livingTrialMobLine(currentChamber.trialMobs))
        addObservedLine(
            TrialChamberHudSection.VAULTS,
            observedCounts(currentChamber.vaultStatuses, TrialVaultHudStatus.entries) { it.metric },
        )
        addObservedLine(
            TrialChamberHudSection.LOOT,
            observedCounts(
                currentChamber.loot.filterNot(TrialChamberHudLoot::isVisited).map(TrialChamberHudLoot::type),
                TrialLootHudType.entries,
            ) { it.metric },
        )
    })
}

private fun livingTrialMobLine(mobs: List<TrialChamberHudMob>) = TrialChamberHudLine(
    TrialChamberHudSection.TRIAL_MOBS,
    listOf(TrialChamberHudEntry(
        TrialChamberHudMetric.LIVING_TRIAL_MOBS,
        mobs.count { it.isCurrentTrialMob && it.isAlive },
    )),
)

private fun MutableList<TrialChamberHudLine>.addObservedLine(
    section: TrialChamberHudSection,
    entries: List<TrialChamberHudEntry>,
) {
    if (entries.isNotEmpty()) add(TrialChamberHudLine(section, entries))
}

private fun <T> observedCounts(
    values: Collection<T>,
    presentationOrder: Collection<T>,
    metric: (T) -> TrialChamberHudMetric,
): List<TrialChamberHudEntry> {
    val counts = values.groupingBy { it }.eachCount()
    return presentationOrder.mapNotNull { value ->
        counts[value]?.let { TrialChamberHudEntry(metric(value), it) }
    }
}
