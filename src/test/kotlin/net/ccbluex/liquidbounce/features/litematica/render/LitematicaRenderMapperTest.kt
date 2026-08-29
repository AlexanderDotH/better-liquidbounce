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
package net.ccbluex.liquidbounce.features.litematica.render

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActionKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActionPriority
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockReason
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaCellStatus
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannedCell
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintPlan
import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LitematicaRenderMapperTest {

    @Test
    fun `planner actions map to place break and fluid target colors`() {
        val mappings = mapOf(
            LitematicaActionKind.PLACE to LitematicaTargetStyle.PLACE,
            LitematicaActionKind.AIR_PLACE to LitematicaTargetStyle.PLACE,
            LitematicaActionKind.BREAK to LitematicaTargetStyle.BREAK,
            LitematicaActionKind.FLUID_PLACE to LitematicaTargetStyle.FLUID,
            LitematicaActionKind.FLUID_PICKUP to LitematicaTargetStyle.FLUID,
        )

        mappings.forEach { (actionKind, expectedStyle) ->
            val target = LitematicaRenderMapper.targetFor(cell(LitematicaCellStatus.MISSING, actionKind))

            assertEquals(expectedStyle, target?.style)
            assertEquals(BlockPos(3, 64, -5), target?.position)
        }
    }

    @Test
    fun `pending blocked and correct cells keep distinct rendering behavior`() {
        val pending = cell(
            status = LitematicaCellStatus.PENDING,
            reason = LitematicaBlockReason.PENDING_CONFIRMATION,
        )
        val blocked = cell(
            status = LitematicaCellStatus.PROTECTED,
            reason = LitematicaBlockReason.BLOCK_ENTITY_PROTECTED,
        )

        assertEquals(LitematicaTargetStyle.PENDING, LitematicaRenderMapper.targetFor(pending)?.style)
        assertEquals(LitematicaTargetStyle.BLOCKED, LitematicaRenderMapper.targetFor(blocked)?.style)
        assertEquals(
            "BLOCK_ENTITY_PROTECTED",
            LitematicaRenderMapper.targetFor(blocked)?.detail,
        )
        assertNull(LitematicaRenderMapper.targetFor(cell(LitematicaCellStatus.CORRECT)))
    }

    @Test
    fun `live pending and retry state override a stale actionable scan`() {
        val position = LitematicaPosition(3, 64, -5)
        val plan = LitematicaPrintPlan(
            selectedPlacement = null,
            cells = listOf(cell(LitematicaCellStatus.MISSING, LitematicaActionKind.PLACE)),
            actions = emptyList(),
        )

        val pending = LitematicaRenderMapper.targetsFor(plan, pendingPositions = setOf(position)).single()
        val failed = LitematicaRenderMapper.targetsFor(plan, blockedPositions = setOf(position)).single()

        assertEquals(LitematicaTargetStyle.PENDING, pending.style)
        assertEquals("PENDING_CONFIRMATION", pending.detail)
        assertEquals(LitematicaTargetStyle.BLOCKED, failed.style)
        assertEquals("RETRY_LIMIT_REACHED", failed.detail)
    }

    @Test
    fun `plan status counts collapse state mismatches and source fluids into HUD categories`() {
        val cells = listOf(
            cell(LitematicaCellStatus.CORRECT),
            cell(LitematicaCellStatus.MISSING),
            cell(LitematicaCellStatus.SOURCE_FLUID),
            cell(LitematicaCellStatus.WRONG_BLOCK),
            cell(LitematicaCellStatus.WRONG_STATE),
            cell(LitematicaCellStatus.EXTRA),
            cell(LitematicaCellStatus.PENDING),
            cell(LitematicaCellStatus.UNSUPPORTED),
        )
        val plan = LitematicaPrintPlan(selectedPlacement = null, cells = cells, actions = emptyList())

        val counts = LitematicaRenderMapper.countsFor(plan)

        assertEquals(LitematicaPlacementCounts(correct = 1, missing = 2, wrong = 2, extra = 1, pending = 1), counts)
    }

    private fun cell(
        status: LitematicaCellStatus,
        actionKind: LitematicaActionKind? = null,
        reason: LitematicaBlockReason? = null,
    ): LitematicaPlannedCell {
        val position = LitematicaPosition(3, 64, -5)
        return LitematicaPlannedCell(
            placementId = PLACEMENT_ID,
            position = position,
            desired = STONE,
            actual = AIR,
            status = status,
            blockReason = reason,
            distanceSquared = 1.0,
            action = actionKind?.let { action(position, it) },
        )
    }

    private fun action(position: LitematicaPosition, kind: LitematicaActionKind) = LitematicaPrintAction(
        target = position,
        placementId = PLACEMENT_ID,
        kind = kind,
        priority = LitematicaActionPriority.CLEANUP,
        desired = STONE,
        actual = AIR,
        distanceSquared = 1.0,
    )

    private companion object {
        val PLACEMENT_ID = LitematicaPlacementId("test")
        val STONE = LitematicaBlockSnapshot.solid("minecraft:stone")
        val AIR = LitematicaBlockSnapshot.air()
    }
}
