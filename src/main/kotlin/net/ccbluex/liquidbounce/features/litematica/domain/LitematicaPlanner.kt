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
