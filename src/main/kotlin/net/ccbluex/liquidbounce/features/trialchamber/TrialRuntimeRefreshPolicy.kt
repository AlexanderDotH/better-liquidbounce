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

/** Independent cadence gates for the always-on runtime's expensive observation passes. */
internal class TrialRuntimeRefreshPolicy(
    private val snapshotIntervalTicks: Long = DEFAULT_SNAPSHOT_INTERVAL_TICKS,
    private val lootIntervalTicks: Long = DEFAULT_LOOT_INTERVAL_TICKS,
    private val fallbackIntervalTicks: Long = DEFAULT_FALLBACK_INTERVAL_TICKS,
) {

    private var lastSnapshotTick: Long? = null
    private var lastLootTick: Long? = null
    private var lastFallbackTick: Long? = null

    init {
        require(snapshotIntervalTicks > 0) { "Snapshot refresh interval must be positive" }
        require(lootIntervalTicks > 0) { "Loot refresh interval must be positive" }
        require(fallbackIntervalTicks > 0) { "Fallback refresh interval must be positive" }
    }

    fun shouldRefreshSnapshot(tick: Long): Boolean = due(tick, lastSnapshotTick, snapshotIntervalTicks).also {
        if (it) lastSnapshotTick = tick
    }

    fun shouldRefreshLoot(tick: Long): Boolean = due(tick, lastLootTick, lootIntervalTicks).also {
        if (it) lastLootTick = tick
    }

    fun shouldReconstructWave(tick: Long): Boolean = due(tick, lastFallbackTick, fallbackIntervalTicks).also {
        if (it) lastFallbackTick = tick
    }

    fun forceSnapshotAndLootRefresh() {
        lastSnapshotTick = null
        lastLootTick = null
    }

    fun forceLootRefresh() {
        lastLootTick = null
    }

    fun reset() {
        lastSnapshotTick = null
        lastLootTick = null
        lastFallbackTick = null
    }

    private fun due(tick: Long, lastTick: Long?, interval: Long): Boolean {
        require(tick >= 0) { "Runtime refresh tick must not be negative" }
        return lastTick == null || tick < lastTick || tick - lastTick >= interval
    }

    companion object {
        const val DEFAULT_SNAPSHOT_INTERVAL_TICKS = 4L
        const val DEFAULT_LOOT_INTERVAL_TICKS = 20L
        const val DEFAULT_FALLBACK_INTERVAL_TICKS = 20L
    }
}
