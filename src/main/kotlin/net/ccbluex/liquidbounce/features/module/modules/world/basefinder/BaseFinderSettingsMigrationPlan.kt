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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

internal fun BaseFinderSettingsMigrator.migrateLegacyLayout() {
    renameLegacyGroups()
    moveLegacySettings()
    dropRetiredSettings()
}

private fun BaseFinderSettingsMigrator.renameLegacyGroups() {
    renameRootGroup("SeedCompare", "SeedMismatch")
    renameRootGroup("GlowBox", "Render")
    foldLegacySeedMismatchToggle()
}

private fun BaseFinderSettingsMigrator.moveLegacySettings() {
    moveInto("Evidence", LEGACY_EVIDENCE_SETTINGS)
    moveInto("SeedMismatch", listOf("WorldSeed"))
    moveGroupInto("Evidence", "SeedMismatch")
    bumpLegacyScanChunksCountToRadius()
    moveInto("Alerts", listOf("Notifications", "ChatCoordinates"))
}

private fun BaseFinderSettingsMigrator.dropRetiredSettings() {
    dropRoot(RETIRED_ROOT_SETTINGS)
    dropFromNestedGroup("Evidence", "SeedMismatch", RETIRED_SEED_SETTINGS)
    dropNested("Render", listOf("Pulse"))
    dropNested("GlowBox", listOf("Pulse"))
}

private val LEGACY_EVIDENCE_SETTINGS = listOf(
    "Storage",
    "Utilities",
    "Automation",
    "Entities",
    "Structural",
    "Geometry",
    "Activity",
    "ChunkTrails",
)

private val RETIRED_ROOT_SETTINGS = listOf(
    "Performance",
    "DirtyChunksPerTick",
    "EntitySampleInterval",
    "FreezesPerTick",
    "WorkerThreads",
    "PromotionsPerTick",
    "SparseSamplesPerChunk",
    "CacheChunks",
    "OverlayYRadius",
    "OverlayRescanInterval",
    "OverlaySamplesPerChunk",
    "MismatchRenderLimit",
    "MismatchMaxDistance",
)

private val RETIRED_SEED_SETTINGS = listOf(
    "ShowOutlines",
    "ShowMismatches",
    "FreezesPerTick",
    "WorkerThreads",
    "PromotionsPerTick",
    "SparseSamplesPerChunk",
    "CacheChunks",
    "OverlayYRadius",
    "OverlayRescanInterval",
    "OverlaySamplesPerChunk",
    "MismatchRenderLimit",
    "MismatchMaxDistance",
    "MissingSolidColor",
    "UnexpectedSolidColor",
    "UtilityMismatchColor",
)
