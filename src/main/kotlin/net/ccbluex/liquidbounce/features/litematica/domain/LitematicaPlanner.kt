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

class LitematicaPlanner {

    fun plan(request: LitematicaPlanRequest): LitematicaPrintPlan {
        val visiblePlacements = request.placements.filter { it.enabled && it.rendered }
        val ambiguousPositions = findAmbiguousPositions(visiblePlacements)
        val localPlans = visiblePlacements.map { placement ->
            PlacementPlan(
                placement = placement,
                cells = localCells(request, placement, ambiguousPositions),
            )
        }
        val selected = selectPlacement(request.stickyPlacementId, localPlans)
            ?: return LitematicaPrintPlan(null, emptyList(), emptyList())
        val actions = selected.cells.mapNotNull(LitematicaPlannedCell::action).sortedWith(ACTION_COMPARATOR)
        return LitematicaPrintPlan(
            selectedPlacement = LitematicaSelectedPlacement(
                id = selected.placement.id,
                name = selected.placement.name,
                bounds = selected.placement.bounds,
            ),
            cells = selected.cells.sortedBy(LitematicaPlannedCell::position),
            actions = actions,
        )
    }

    private fun findAmbiguousPositions(
        placements: List<LitematicaPlacementSnapshot>,
    ): Set<LitematicaPosition> {
        val expectations = HashMap<LitematicaPosition, LitematicaBlockSnapshot>()
        val ambiguous = HashSet<LitematicaPosition>()
        placements.forEach { placement ->
            placement.cells.asSequence()
                .filter { placement.renderLayer.allows(it.position) }
                .forEach { cell ->
                    val existing = expectations.putIfAbsent(cell.position, cell.desired)
                    if (existing != null && !existing.sameStateAs(cell.desired)) ambiguous += cell.position
                }
        }
        return ambiguous
    }

    private fun localCells(
        request: LitematicaPlanRequest,
        placement: LitematicaPlacementSnapshot,
        ambiguousPositions: Set<LitematicaPosition>,
    ): List<LitematicaPlannedCell> {
        val rangeSquared = request.settings.range * request.settings.range
        return placement.cells.asSequence()
            .filter { placement.renderLayer.allows(it.position) }
            .filter { it.position.distanceSquaredTo(request.origin) <= rangeSquared }
            .map { cell ->
                classify(
                    placementId = placement.id,
                    cell = cell,
                    distanceSquared = cell.position.distanceSquaredTo(request.origin),
                    settings = request.settings,
                    pending = cell.position in request.pendingPositions,
                    ambiguous = cell.position in ambiguousPositions,
                )
            }
            .toList()
    }

    private fun selectPlacement(
        stickyPlacementId: LitematicaPlacementId?,
        plans: List<PlacementPlan>,
    ): PlacementPlan? {
        val candidates = plans.filter(PlacementPlan::hasIncompleteWork)
        val sticky = candidates.firstOrNull { it.placement.id == stickyPlacementId }
        if (sticky != null) return sticky
        return candidates.minWithOrNull(
            compareBy<PlacementPlan>(PlacementPlan::nearestIncompleteDistance)
                .thenBy { it.placement.id.value },
        )
    }

    private fun classify(
        placementId: LitematicaPlacementId,
        cell: LitematicaCellSnapshot,
        distanceSquared: Double,
        settings: LitematicaPlannerSettings,
        pending: Boolean,
        ambiguous: Boolean,
    ): LitematicaPlannedCell {
        val result = CellClassifier(placementId, cell, distanceSquared, settings).classify(pending, ambiguous)
        return LitematicaPlannedCell(
            placementId = placementId,
            position = cell.position,
            desired = cell.desired,
            actual = cell.actual,
            status = result.status,
            blockReason = result.reason,
            requiredMaterialId = cell.requiredMaterialId,
            distanceSquared = distanceSquared,
            action = result.action,
        )
    }

    private data class PlacementPlan(
        val placement: LitematicaPlacementSnapshot,
        val cells: List<LitematicaPlannedCell>,
    ) {
        val hasIncompleteWork: Boolean
            get() = cells.any { it.status != LitematicaCellStatus.CORRECT }

        val nearestIncompleteDistance: Double
            get() = cells.asSequence()
                .filter { it.status != LitematicaCellStatus.CORRECT }
                .minOf(LitematicaPlannedCell::distanceSquared)
    }

    private companion object {
        val ACTION_COMPARATOR = compareBy<LitematicaPrintAction>(LitematicaPrintAction::priority)
            .thenBy(LitematicaPrintAction::distanceSquared)
            .thenBy(LitematicaPrintAction::target)
    }
}

private data class CellClassification(
    val status: LitematicaCellStatus,
    val reason: LitematicaBlockReason? = null,
    val action: LitematicaPrintAction? = null,
)

private class CellClassifier(
    private val placementId: LitematicaPlacementId,
    private val cell: LitematicaCellSnapshot,
    private val distanceSquared: Double,
    private val settings: LitematicaPlannerSettings,
) {
    private val desired = cell.desired
    private val actual = cell.actual

    fun classify(pending: Boolean, ambiguous: Boolean): CellClassification {
        if (ambiguous) return blocked(LitematicaCellStatus.AMBIGUOUS, LitematicaBlockReason.AMBIGUOUS_OVERLAP)
        if (pending) return blocked(LitematicaCellStatus.PENDING, LitematicaBlockReason.PENDING_CONFIRMATION)
        if (desired.sameStateAs(actual)) return CellClassification(LitematicaCellStatus.CORRECT)
        if (desired.kind == LitematicaBlockKind.FLUID_FLOWING || actual.kind == LitematicaBlockKind.FLUID_FLOWING) {
            return blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.FLOWING_FLUID)
        }
        if (!desired.reproducible) {
            return blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.NON_REPRODUCIBLE_BLOCK)
        }
        if (desired.kind == LitematicaBlockKind.UNSUPPORTED || actual.kind == LitematicaBlockKind.UNSUPPORTED) {
            return blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.UNSUPPORTED_BLOCK)
        }
        return when (desired.kind) {
            LitematicaBlockKind.AIR -> classifyExtra()
            LitematicaBlockKind.FLUID_SOURCE -> classifyDesiredFluid()
            LitematicaBlockKind.SOLID -> classifyDesiredSolid()
            LitematicaBlockKind.FLUID_FLOWING,
            LitematicaBlockKind.UNSUPPORTED,
            -> blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.UNSUPPORTED_BLOCK)
        }
    }

    private fun classifyExtra(): CellClassification {
        if (actual.hasBlockEntity && !settings.breakBlockEntities) {
            return blocked(LitematicaCellStatus.PROTECTED, LitematicaBlockReason.BLOCK_ENTITY_PROTECTED)
        }
        if (!settings.breakExtra) {
            return blocked(LitematicaCellStatus.EXTRA, LitematicaBlockReason.BREAK_EXTRA_DISABLED)
        }
        if (actual.kind == LitematicaBlockKind.FLUID_SOURCE) {
            if (!settings.fluids) return blocked(LitematicaCellStatus.EXTRA, LitematicaBlockReason.FLUIDS_DISABLED)
            return actionable(LitematicaCellStatus.EXTRA, LitematicaActionKind.FLUID_PICKUP)
        }
        return actionable(LitematicaCellStatus.EXTRA, LitematicaActionKind.BREAK)
    }

    private fun classifyDesiredFluid(): CellClassification {
        if (actual.kind == LitematicaBlockKind.AIR || actual.replaceable) {
            if (!settings.fluids) {
                return blocked(LitematicaCellStatus.SOURCE_FLUID, LitematicaBlockReason.FLUIDS_DISABLED)
            }
            return place(LitematicaCellStatus.SOURCE_FLUID, LitematicaActionKind.FLUID_PLACE)
        }
        return classifyWrong()
    }

    private fun classifyDesiredSolid(): CellClassification {
        if (actual.kind == LitematicaBlockKind.AIR || actual.replaceable) {
            return placeSolid()
        }
        return classifyWrong()
    }

    private fun classifyWrong(): CellClassification {
        if (actual.hasBlockEntity && !settings.breakBlockEntities) {
            return blocked(LitematicaCellStatus.PROTECTED, LitematicaBlockReason.BLOCK_ENTITY_PROTECTED)
        }
        val status = if (desired.sameBlockAs(actual)) {
            LitematicaCellStatus.WRONG_STATE
        } else {
            LitematicaCellStatus.WRONG_BLOCK
        }
        if (!settings.breakWrong) return blocked(status, LitematicaBlockReason.BREAK_WRONG_DISABLED)
        if (actual.kind != LitematicaBlockKind.FLUID_SOURCE) {
            return actionable(status, LitematicaActionKind.BREAK)
        }
        if (!settings.fluids) return blocked(status, LitematicaBlockReason.FLUIDS_DISABLED)
        return actionable(status, LitematicaActionKind.FLUID_PICKUP)
    }

    private fun placeSolid(): CellClassification {
        if (!cell.materialAvailable) {
            return blocked(LitematicaCellStatus.MISSING, LitematicaBlockReason.MISSING_MATERIAL)
        }
        return when (cell.placementMethod) {
            LitematicaPlacementMethod.NEIGHBOR_FACE -> actionable(
                LitematicaCellStatus.MISSING,
                LitematicaActionKind.PLACE,
            )
            LitematicaPlacementMethod.AIR_PLACE -> if (settings.airPlace == LitematicaAirPlaceMode.SMART) {
                actionable(LitematicaCellStatus.MISSING, LitematicaActionKind.AIR_PLACE)
            } else {
                blocked(LitematicaCellStatus.MISSING, LitematicaBlockReason.AIR_PLACE_DISABLED)
            }
            LitematicaPlacementMethod.UNAVAILABLE -> blocked(
                LitematicaCellStatus.MISSING,
                LitematicaBlockReason.NO_PLACEMENT_FACE,
            )
        }
    }

    private fun place(status: LitematicaCellStatus, kind: LitematicaActionKind): CellClassification {
        if (!cell.materialAvailable) return blocked(status, LitematicaBlockReason.MISSING_MATERIAL)
        return actionable(status, kind)
    }

    private fun actionable(status: LitematicaCellStatus, kind: LitematicaActionKind) = CellClassification(
        status = status,
        action = LitematicaPrintAction(
            target = cell.position,
            placementId = placementId,
            kind = kind,
            priority = kind.priority,
            desired = desired,
            actual = actual,
            distanceSquared = distanceSquared,
        ),
    )

    private fun blocked(status: LitematicaCellStatus, reason: LitematicaBlockReason) = CellClassification(
        status = status,
        reason = reason,
    )
}

private val LitematicaActionKind.priority: LitematicaActionPriority
    get() = when (this) {
        LitematicaActionKind.BREAK,
        LitematicaActionKind.FLUID_PICKUP,
        -> LitematicaActionPriority.CLEANUP
        LitematicaActionKind.PLACE -> LitematicaActionPriority.SUPPORTED_SOLID
        LitematicaActionKind.AIR_PLACE -> LitematicaActionPriority.AIRPLACE_SOLID
        LitematicaActionKind.FLUID_PLACE -> LitematicaActionPriority.DESIRED_FLUID
    }
