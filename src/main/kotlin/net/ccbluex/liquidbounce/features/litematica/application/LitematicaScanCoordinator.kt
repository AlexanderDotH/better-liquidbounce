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
package net.ccbluex.liquidbounce.features.litematica.application

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaCellSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlanRequest
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlanner
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannerSettings
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPoint
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintPlan
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCellInteractionSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanCursor
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanRequest

data class LitematicaScanUpdate(
    val plan: LitematicaPrintPlan,
    val placementChanged: Boolean,
)

class LitematicaScanCoordinator(
    private val port: LitematicaPort,
    private val planner: LitematicaPlanner = LitematicaPlanner(),
) {
    private var cursor: LitematicaScanCursor? = null
    private var metadata: List<LitematicaPlacementMetadataSnapshot> = emptyList()
    private val placements = linkedMapOf<LitematicaPlacementId, LitematicaPlacementSnapshot>()
    private val buildingInteractions = linkedMapOf<CellKey, LitematicaCellInteractionSnapshot>()
    private val currentInteractions = linkedMapOf<CellKey, LitematicaCellInteractionSnapshot>()
    private var stickyPlacementId: LitematicaPlacementId? = null

    var plan = LitematicaPrintPlan(null, emptyList(), emptyList())
        private set

    fun scan(
        center: LitematicaPoint,
        settings: LitematicaPlannerSettings,
        pendingPositions: Set<LitematicaPosition>,
    ): LitematicaScanUpdate? {
        val metadataChanged = refreshMetadata()
        val batch = port.scanPlacements(
            LitematicaScanRequest(
                center = center,
                range = settings.range,
                maxCells = SCAN_MAX_CELLS,
                timeBudgetNanos = SCAN_TIME_BUDGET_NANOS,
                cursor = cursor,
            ),
        )
        if (batch.restartGeneration) restartGeneration()
        batch.placements.forEach(::mergePlacement)
        batch.interactions.forEach { interaction ->
            buildingInteractions[CellKey(interaction.placementId, interaction.position)] = interaction
        }
        cursor = batch.nextCursor
        if (!batch.complete) {
            return if (batch.restartGeneration) LitematicaScanUpdate(plan, placementChanged = true) else null
        }

        val nextPlan = planner.plan(
            LitematicaPlanRequest(
                origin = center,
                placements = placements.values,
                stickyPlacementId = stickyPlacementId,
                pendingPositions = pendingPositions,
                settings = settings,
            ),
        )
        val nextPlacement = nextPlan.selectedPlacement?.id
        val selectionChanged = stickyPlacementId != null && nextPlacement != stickyPlacementId
        stickyPlacementId = nextPlacement
        plan = nextPlan
        currentInteractions.clear()
        currentInteractions.putAll(buildingInteractions)
        clearGeneration()
        return LitematicaScanUpdate(
            nextPlan,
            batch.restartGeneration || metadataChanged || selectionChanged,
        )
    }

    fun interactionFor(action: LitematicaPrintAction): LitematicaCellInteractionSnapshot? =
        currentInteractions[CellKey(action.placementId, action.target)]

    fun materialFor(action: LitematicaPrintAction): String? = plan.cells.firstOrNull {
        it.placementId == action.placementId && it.position == action.target
    }?.requiredMaterialId

    fun reset() {
        cursor = null
        metadata = emptyList()
        placements.clear()
        buildingInteractions.clear()
        currentInteractions.clear()
        stickyPlacementId = null
        plan = LitematicaPrintPlan(null, emptyList(), emptyList())
    }

    private fun refreshMetadata(): Boolean {
        if (cursor != null || placements.isNotEmpty()) return false
        val refreshed = port.placementMetadata()
        val changed = metadata.isNotEmpty() && metadata != refreshed
        metadata = refreshed
        if (changed) {
            buildingInteractions.clear()
            currentInteractions.clear()
            stickyPlacementId = null
        }
        return changed
    }

    private fun mergePlacement(partial: LitematicaPlacementSnapshot) {
        val existing = placements[partial.id]
        if (existing == null) {
            placements[partial.id] = partial
            return
        }
        placements[partial.id] = LitematicaPlacementSnapshot(
            id = existing.id,
            name = partial.name,
            enabled = partial.enabled,
            rendered = partial.rendered,
            bounds = partial.bounds,
            renderLayer = partial.renderLayer,
            cells = (existing.cells + partial.cells).distinctBy(LitematicaCellSnapshot::position),
        )
    }

    private fun clearGeneration() {
        cursor = null
        placements.clear()
        buildingInteractions.clear()
    }

    private fun restartGeneration() {
        clearGeneration()
        currentInteractions.clear()
        plan = LitematicaPrintPlan(null, emptyList(), emptyList())
    }

    private data class CellKey(
        val placementId: LitematicaPlacementId,
        val position: LitematicaPosition,
    )

    private companion object {
        const val SCAN_MAX_CELLS = 256
        const val SCAN_TIME_BUDGET_NANOS = 2_000_000L
    }
}
