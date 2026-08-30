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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockLayer
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSearchCursor
import net.ccbluex.liquidbounce.lang.translation
import net.minecraft.network.chat.Component

internal data class SeedCrackerPresentation(
    val message: Component,
    val severity: NotificationEvent.Severity = NotificationEvent.Severity.INFO,
)

internal fun seedCrackerTranslation(key: String, vararg arguments: Any) =
    translation("liquidbounce.module.seedCracker.$key", *arguments)

internal data class RuntimeSettings(
    val structuresEnabled: Boolean = true,
    val netherBedrockEnabled: Boolean = true,
    val autoAcceptStrongEvidence: Boolean = true,
    val persistProgress: Boolean = true,
    val workerLimit: Int = DEFAULT_WORKERS,
) {
    val enabledTechniques: Set<CrackingTechnique>
        get() = buildSet {
            if (structuresEnabled) add(CrackingTechnique.STRUCTURES)
            if (netherBedrockEnabled) add(CrackingTechnique.NETHER_BEDROCK)
        }
}

internal data class RuntimeSolveResult(
    val candidate: SeedCandidate? = null,
    val state: CrackerState,
    val messageKey: String? = null,
    val messageArguments: List<String> = emptyList(),
    val severity: NotificationEvent.Severity = NotificationEvent.Severity.INFO,
    val conflictReport: SeedCrackerConflictReport? = null,
    val nextStructureCursor: StructureSeedSearchCursor? = null,
)

internal data class ScopedChunk(
    val scope: CrackScope,
    val chunk: ChunkCoordinate,
)

internal const val CHUNK_SHIFT = 4
internal const val MIN_WORKERS = 1
internal const val MAX_WORKERS = 8
internal const val DEFAULT_WORKERS = 2
internal const val MAX_DIRTY_RESCANS_PER_TICK = 2
internal const val NANOS_PER_MILLI = 1_000_000L
internal const val NETHER_CHECKPOINT_PREFIX_INTERVAL = 1L shl 30
internal val NETHER_PATTERN_LAYERS = setOf(NetherBedrockLayer.FLOOR.blockY, NetherBedrockLayer.ROOF.blockY)
internal val RELEVANT_STRUCTURE_BLOCKS = setOf(
    "snow_block", "redstone_torch", "chest", "ladder", "blue_terracotta", "stone_pressure_plate",
    "tripwire_hook", "redstone_wire", "dispenser", "mossy_cobblestone", "cobblestone", "cauldron",
    "crafting_table", "oak_fence", "spruce_planks", "oak_planks", "oak_stairs", "oak_trapdoor",
    "stripped_oak_log", "dark_oak_log", "dark_oak_planks", "prismarine", "prismarine_bricks",
    "dark_prismarine", "sea_lantern",
)

internal fun resolveCrackerState(
    phase: SeedCrackerTrackerPhase,
    resultState: CrackerState?,
): CrackerState {
    if (phase == SeedCrackerTrackerPhase.PAUSED) return CrackerState.PAUSED
    return resultState ?: when (phase) {
        SeedCrackerTrackerPhase.INACTIVE,
        SeedCrackerTrackerPhase.COLLECTING -> CrackerState.COLLECTING
        SeedCrackerTrackerPhase.DEBOUNCING,
        SeedCrackerTrackerPhase.SOLVING -> CrackerState.SOLVING
        SeedCrackerTrackerPhase.CANDIDATE -> CrackerState.CANDIDATE
        SeedCrackerTrackerPhase.PAUSED -> CrackerState.PAUSED
        SeedCrackerTrackerPhase.FAILED -> CrackerState.CONTRADICTED
    }
}
