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
package net.ccbluex.liquidbounce.features.litematica.integration.api

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBounds
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPoint
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaRenderLayer
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

@JvmInline
value class LitematicaScanCursor(val value: String) {
    init {
        require(value.isNotBlank()) { "Litematica scan cursor cannot be blank" }
    }
}

data class LitematicaPlacementMetadataSnapshot(
    val id: LitematicaPlacementId,
    val name: String,
    val enabled: Boolean,
    val rendered: Boolean,
    val bounds: LitematicaBounds,
    val renderLayer: LitematicaRenderLayer,
)

data class LitematicaScanRequest(
    val center: LitematicaPoint,
    val range: Double,
    val maxCells: Int,
    val timeBudgetNanos: Long,
    val cursor: LitematicaScanCursor? = null,
) {
    init {
        require(range.isFinite() && range > 0.0) { "Litematica scan range must be finite and positive" }
        require(maxCells > 0) { "Litematica scan must allow at least one cell" }
        require(timeBudgetNanos > 0L) { "Litematica scan time budget must be positive" }
    }
}

data class LitematicaCellInteractionSnapshot(
    val placementId: LitematicaPlacementId,
    val position: LitematicaPosition,
    val neighborHitResult: BlockHitResult?,
    val rotationTarget: Vec3,
)

data class LitematicaScanBatch(
    val placements: List<LitematicaPlacementSnapshot>,
    val interactions: List<LitematicaCellInteractionSnapshot>,
    val nextCursor: LitematicaScanCursor?,
    val complete: Boolean,
    val restartGeneration: Boolean = false,
) {
    init {
        require(complete == (nextCursor == null)) {
            "A complete scan cannot expose a continuation cursor"
        }
        require(interactions.size <= placements.sumOf { it.cells.size }) {
            "A scan cannot expose more interactions than cells"
        }
    }
}

data class LitematicaMaterialSnapshot(
    val materialId: String,
    val requiredCount: Int,
    val availableCount: Int,
) {
    init {
        require(materialId.isNotBlank()) { "Litematica material id cannot be blank" }
        require(requiredCount >= 0) { "Required material count cannot be negative" }
        require(availableCount >= 0) { "Available material count cannot be negative" }
    }

    val missingCount: Int
        get() = (requiredCount - availableCount).coerceAtLeast(0)
}

data class LitematicaEasyPlaceRequest(
    val placementId: LitematicaPlacementId,
    val targetPosition: LitematicaPosition,
    val desired: LitematicaBlockSnapshot,
    val interaction: LitematicaCellInteractionSnapshot,
    val strategy: LitematicaEasyPlaceStrategy,
) {
    init {
        require(interaction.placementId == placementId && interaction.position == targetPosition) {
            "Easy Place interaction must describe the requested placement cell"
        }
        require(strategy == LitematicaEasyPlaceStrategy.DIRECT_AIR_PLACE || interaction.neighborHitResult != null) {
            "Neighbor Easy Place requires a block hit result"
        }
    }
}

data class LitematicaHotkeySnapshot(
    val easyPlaceHeld: Boolean,
)

data class LitematicaEasyPlaceSnapshot(
    val enabled: Boolean,
    val hotkey: LitematicaHotkeySnapshot,
) {
    companion object {
        fun disabled() = LitematicaEasyPlaceSnapshot(
            enabled = false,
            hotkey = LitematicaHotkeySnapshot(easyPlaceHeld = false),
        )
    }
}

enum class LitematicaEasyPlaceStrategy {
    NEIGHBOR,
    DIRECT_AIR_PLACE,
}

sealed interface LitematicaEasyPlaceResult {
    data object Submitted : LitematicaEasyPlaceResult
    data class Rejected(val detail: String) : LitematicaEasyPlaceResult
    data class Failed(val detail: String) : LitematicaEasyPlaceResult
}

data class LitematicaVerifierSnapshot(
    val correct: Int,
    val missing: Int,
    val wrongState: Int,
    val extra: Int,
    val wrongBlockEntityData: Int,
)
