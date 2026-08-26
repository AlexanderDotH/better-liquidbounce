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

internal enum class TrialChamberHudTone {
    ACCENT,
    POSITIVE,
    WARNING,
    MUTED,
}

internal data class TrialChamberHudStat(
    val label: String,
    val count: Int,
    val tone: TrialChamberHudTone = TrialChamberHudTone.MUTED,
) {
    init {
        require(label.isNotBlank()) { "Trial Chamber HUD stat labels cannot be blank" }
        require(count >= 0) { "Trial Chamber HUD stat counts cannot be negative" }
    }
}

internal data class TrialChamberHudPresentation(
    val spawners: List<TrialChamberHudStat>,
    val livingMobs: Int,
    val vaults: List<TrialChamberHudStat>,
    val loot: List<TrialChamberHudStat>,
) {
    val allLabels: List<String>
        get() = spawners.map(TrialChamberHudStat::label) +
            vaults.map(TrialChamberHudStat::label) + loot.map(TrialChamberHudStat::label)
}

internal fun buildTrialChamberHudPresentation(model: TrialChamberHudModel): TrialChamberHudPresentation =
    TrialChamberHudPresentation(
        spawners = SPAWNER_STATS.observedStats(model),
        livingMobs = model.count(TrialChamberHudMetric.LIVING_TRIAL_MOBS),
        vaults = VAULT_STATS.observedStats(model),
        loot = LOOT_STATS.observedStats(model),
    )

private fun List<StatDefinition>.observedStats(model: TrialChamberHudModel): List<TrialChamberHudStat> =
    mapNotNull { definition ->
        model.count(definition.metric).takeIf { it > 0 }?.let { count ->
            TrialChamberHudStat(definition.label, count, definition.tone)
        }
    }

private data class StatDefinition(
    val metric: TrialChamberHudMetric,
    val label: String,
    val tone: TrialChamberHudTone,
)

private val SPAWNER_STATS = listOf(
    StatDefinition(TrialChamberHudMetric.SPAWNER_INACTIVE, "Idle", TrialChamberHudTone.MUTED),
    StatDefinition(TrialChamberHudMetric.SPAWNER_WAITING_FOR_PLAYERS, "Waiting", TrialChamberHudTone.ACCENT),
    StatDefinition(TrialChamberHudMetric.SPAWNER_ACTIVE, "Active", TrialChamberHudTone.WARNING),
    StatDefinition(TrialChamberHudMetric.SPAWNER_WAITING_FOR_REWARD_EJECTION, "Reward", TrialChamberHudTone.POSITIVE),
    StatDefinition(TrialChamberHudMetric.SPAWNER_EJECTING_REWARD, "Ejecting", TrialChamberHudTone.POSITIVE),
    StatDefinition(TrialChamberHudMetric.SPAWNER_COOLDOWN, "Cooldown", TrialChamberHudTone.MUTED),
)

private val VAULT_STATS = listOf(
    StatDefinition(TrialChamberHudMetric.VAULT_AVAILABLE, "Ready", TrialChamberHudTone.POSITIVE),
    StatDefinition(TrialChamberHudMetric.VAULT_CLAIMED, "Claimed", TrialChamberHudTone.MUTED),
    StatDefinition(TrialChamberHudMetric.VAULT_UNKNOWN, "Unknown", TrialChamberHudTone.MUTED),
)

private val LOOT_STATS = listOf(
    StatDefinition(TrialChamberHudMetric.LOOT_CHEST, "Chest", TrialChamberHudTone.MUTED),
    StatDefinition(TrialChamberHudMetric.LOOT_BARREL, "Barrel", TrialChamberHudTone.MUTED),
    StatDefinition(TrialChamberHudMetric.LOOT_POT, "Pot", TrialChamberHudTone.MUTED),
    StatDefinition(TrialChamberHudMetric.LOOT_DISPENSER, "Dispenser", TrialChamberHudTone.MUTED),
)
