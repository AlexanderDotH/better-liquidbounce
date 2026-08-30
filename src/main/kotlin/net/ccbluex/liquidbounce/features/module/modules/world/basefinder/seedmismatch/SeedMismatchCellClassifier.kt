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
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks

internal object SeedMismatchCellClassifier {
    fun shouldIgnore(observedId: Int, expectedId: Int, fullTerrain: Boolean): Boolean {
        if (observedId == caveAirId || expectedId == caveAirId) return true
        if (BaseFinderBlockRegistry.isUnstableSeedComparison(observedId) ||
            BaseFinderBlockRegistry.isUnstableSeedComparison(expectedId)) {
            return true
        }
        return !fullTerrain &&
            isSoftIgnorableSeedDecorationId(observedId) &&
            (isSoftIgnorableSeedDecorationId(expectedId) || isEmptySeedSpaceId(expectedId))
    }

    fun classify(
        observedId: Int,
        expectedId: Int,
        fullTerrain: Boolean,
        compareMaterials: Boolean,
        clientObservedUpdate: Boolean,
    ): SeedMismatchKind? {
        if (observedId == expectedId) return null
        if (isUtilityMismatchId(observedId) && !isUtilityMismatchId(expectedId)) return SeedMismatchKind.UTILITY
        if (fullTerrain && !clientObservedUpdate && isNaturalDecorationDrift(observedId, expectedId)) return null
        classifyColumnFallback(observedId, expectedId, fullTerrain)?.let { return it }
        return classifyOccupancy(observedId, expectedId, fullTerrain, compareMaterials, clientObservedUpdate)
    }

    private fun classifyColumnFallback(
        observedId: Int,
        expectedId: Int,
        fullTerrain: Boolean,
    ): SeedMismatchKind? {
        if (fullTerrain) return null
        if (isSoftIgnorableSeedDecorationId(observedId) && isSolidTerrainId(expectedId)) return null
        if (!isSeedMismatchBuildMaterialId(observedId)) return null
        if (isNaturalTreeLogMaterialId(observedId) && !isSolidTerrainId(expectedId)) return null
        return SeedMismatchKind.UNEXPECTED_SOLID
    }

    private fun classifyOccupancy(
        observedId: Int,
        expectedId: Int,
        fullTerrain: Boolean,
        compareMaterials: Boolean,
        clientObservedUpdate: Boolean,
    ): SeedMismatchKind? {
        val observedSolid = isSolidTerrainId(observedId)
        val expectedSolid = isSolidTerrainId(expectedId)
        val materialDiffers = !BaseFinderBlockRegistry.sameMaterial(observedId, expectedId)
        return when {
            fullTerrain && clientObservedUpdate && observedSolid && expectedSolid && materialDiffers ->
                SeedMismatchKind.UNEXPECTED_SOLID
            observedSolid && !expectedSolid -> SeedMismatchKind.UNEXPECTED_SOLID
            !observedSolid && expectedSolid -> SeedMismatchKind.MISSING_SOLID
            observedSolid && expectedSolid && compareMaterials && fullTerrain && materialDiffers ->
                SeedMismatchKind.MATERIAL_SWAP
            else -> null
        }
    }

    private fun isNaturalDecorationDrift(observedId: Int, expectedId: Int): Boolean {
        val observedLog = isNaturalTreeLogMaterialId(observedId)
        val expectedLog = isNaturalTreeLogMaterialId(expectedId)
        val treeDrift = (observedLog && expectedLog) ||
            (observedLog && !isSolidTerrainId(expectedId)) ||
            (expectedLog && !isSolidTerrainId(observedId))
        if (treeDrift) return true
        val observedDecoration = BaseFinderBlockRegistry.isNaturalOccupancyDecoration(observedId)
        val expectedDecoration = BaseFinderBlockRegistry.isNaturalOccupancyDecoration(expectedId)
        return (observedDecoration && !isSolidTerrainId(expectedId)) ||
            (expectedDecoration && !isSolidTerrainId(observedId))
    }

    private val caveAirId by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BuiltInRegistries.BLOCK.getId(Blocks.CAVE_AIR)
    }
}
