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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.*
import kotlin.math.max

internal object SeedMismatchPolicy {
    fun adjustFalsePositives(
        heuristic: Set<BaseFalsePositive>,
        seedConfirmedStructures: Set<BaseFalsePositive>,
        seedStructureCheckActive: Boolean,
    ): Set<BaseFalsePositive> {
        if (heuristic.isEmpty()) return emptySet()
        if (!seedStructureCheckActive) return heuristic
        return heuristic.filterTo(linkedSetOf()) { falsePositive ->
            when {
                falsePositive == BaseFalsePositive.MINESHAFT_OR_DUNGEON -> true
                falsePositive in structureFalsePositives -> falsePositive in seedConfirmedStructures
                else -> true
            }
        }
    }

    fun shouldPromoteToFull(signal: SeedMismatchSignal, hasHeuristicPriority: Boolean): Boolean {
        if (signal.phase != SeedComparePhase.SPARSE) return false
        if (hasHeuristicPriority) return true
        val hits = signal.unexpectedSolidCount + signal.utilityMismatchCount
        return signal.mismatchRatio >= SPARSE_PROMOTION_RATIO || hits >= SPARSE_PROMOTION_HITS
    }

    private val structureFalsePositives = setOf(
        BaseFalsePositive.VILLAGE,
        BaseFalsePositive.MINESHAFT_OR_DUNGEON,
        BaseFalsePositive.RUINED_PORTAL,
        BaseFalsePositive.FORTRESS_BASTION_OR_END_CITY,
        BaseFalsePositive.ISOLATED_GENERATED_LOOT_CONTAINER,
    )
    private const val SPARSE_PROMOTION_RATIO = 0.04
    private const val SPARSE_PROMOTION_HITS = 8
}

internal object SeedMismatchSampling {
    private val allChunkLocals: List<Pair<Int, Int>> = buildList(256) {
        for (x in 0..15) for (z in 0..15) add(x to z)
    }

    fun allChunkLocals(): List<Pair<Int, Int>> = allChunkLocals

    fun sparseSampleLocals(sampleCount: Int): List<Pair<Int, Int>> {
        val count = sampleCount.coerceIn(1, 256)
        if (count >= 256) return allChunkLocals()
        val step = max(1, 16 / kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt())
        val samples = ArrayList<Pair<Int, Int>>(count)
        var x = step / 2
        while (x < 16 && samples.size < count) {
            var z = step / 2
            while (z < 16 && samples.size < count) {
                samples += x to z
                z += step
            }
            x += step
        }
        var fill = 0
        while (samples.size < count) {
            samples += (fill % 16) to ((fill / 16) % 16)
            fill++
        }
        return samples.distinct()
    }
}
