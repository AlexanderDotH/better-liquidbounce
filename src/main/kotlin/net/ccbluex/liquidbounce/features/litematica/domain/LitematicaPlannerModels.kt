/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.litematica.domain

import net.ccbluex.liquidbounce.common.Tagged

enum class LitematicaActivationMode(override val tag: String) : Tagged {
    LITEMATICA_KEY("LitematicaKey"),
    CONTINUOUS("Continuous"),
}

enum class LitematicaAirPlaceMode(override val tag: String) : Tagged {
    DISABLED("Disabled"),
    SMART("Smart"),
}

@Suppress("LongParameterList")
data class LitematicaPlannerSettings(
    val activation: LitematicaActivationMode = LitematicaActivationMode.LITEMATICA_KEY,
    val range: Double = DEFAULT_RANGE,
    val actionDelayTicks: Int = DEFAULT_ACTION_DELAY_TICKS,
    val retryLimit: Int = DEFAULT_RETRY_LIMIT,
    val airPlace: LitematicaAirPlaceMode = LitematicaAirPlaceMode.SMART,
    val breakWrong: Boolean = true,
    val breakExtra: Boolean = true,
    val breakBlockEntities: Boolean = false,
    val fluids: Boolean = true,
) {
    init {
        require(range.isFinite() && range > 0.0) { "Litematica range must be finite and positive" }
        require(actionDelayTicks >= 0) { "Litematica action delay must not be negative" }
        require(retryLimit > 0) { "Litematica retry limit must be positive" }
    }

    private companion object {
        const val DEFAULT_RANGE = 4.5
        const val DEFAULT_ACTION_DELAY_TICKS = 1
        const val DEFAULT_RETRY_LIMIT = 10
    }
}

class LitematicaPlanRequest(
    val origin: LitematicaPoint,
    placements: Collection<LitematicaPlacementSnapshot>,
    val stickyPlacementId: LitematicaPlacementId? = null,
    pendingPositions: Set<LitematicaPosition> = emptySet(),
    val settings: LitematicaPlannerSettings = LitematicaPlannerSettings(),
) {
    val placements: List<LitematicaPlacementSnapshot> = placements.toList()
    val pendingPositions: Set<LitematicaPosition> = pendingPositions.toSet()
}

enum class LitematicaCellStatus {
    CORRECT,
    MISSING,
    WRONG_BLOCK,
    WRONG_STATE,
    EXTRA,
    SOURCE_FLUID,
    PROTECTED,
    PENDING,
    UNSUPPORTED,
    AMBIGUOUS,
}

enum class LitematicaBlockReason {
    AMBIGUOUS_OVERLAP,
    PENDING_CONFIRMATION,
    BLOCK_ENTITY_PROTECTED,
    FLOWING_FLUID,
    NON_REPRODUCIBLE_BLOCK,
    UNSUPPORTED_BLOCK,
    BREAK_WRONG_DISABLED,
    BREAK_EXTRA_DISABLED,
    AIR_PLACE_DISABLED,
    NO_PLACEMENT_FACE,
    MISSING_MATERIAL,
    FLUIDS_DISABLED,
}

enum class LitematicaActionKind {
    BREAK,
    PLACE,
    AIR_PLACE,
    FLUID_PLACE,
    FLUID_PICKUP,
}

enum class LitematicaActionPriority {
    CLEANUP,
    SUPPORTED_SOLID,
    AIRPLACE_SOLID,
    DESIRED_FLUID,
}

data class LitematicaPrintAction(
    val target: LitematicaPosition,
    val placementId: LitematicaPlacementId,
    val kind: LitematicaActionKind,
    val priority: LitematicaActionPriority,
    val desired: LitematicaBlockSnapshot,
    val actual: LitematicaBlockSnapshot,
    val distanceSquared: Double,
) {
    val position: LitematicaPosition
        get() = target
}

@Suppress("LongParameterList")
data class LitematicaPlannedCell(
    val placementId: LitematicaPlacementId,
    val position: LitematicaPosition,
    val desired: LitematicaBlockSnapshot,
    val actual: LitematicaBlockSnapshot,
    val status: LitematicaCellStatus,
    val blockReason: LitematicaBlockReason? = null,
    val requiredMaterialId: String? = null,
    val distanceSquared: Double,
    val action: LitematicaPrintAction? = null,
)

data class LitematicaSelectedPlacement(
    val id: LitematicaPlacementId,
    val name: String,
    val bounds: LitematicaBounds,
)

class LitematicaPrintPlan(
    val selectedPlacement: LitematicaSelectedPlacement?,
    cells: Collection<LitematicaPlannedCell>,
    actions: Collection<LitematicaPrintAction>,
) {
    val cells: List<LitematicaPlannedCell> = cells.toList()
    val actions: List<LitematicaPrintAction> = actions.toList()
    val target: LitematicaPrintAction? = this.actions.firstOrNull()
    val statusCounts: Map<LitematicaCellStatus, Int> = LitematicaCellStatus.entries.associateWith { status ->
        this.cells.count { it.status == status }
    }
    val missingMaterials: List<String> = this.cells.asSequence()
        .filter { it.blockReason == LitematicaBlockReason.MISSING_MATERIAL }
        .mapNotNull(LitematicaPlannedCell::requiredMaterialId)
        .distinct()
        .sorted()
        .toList()
}
