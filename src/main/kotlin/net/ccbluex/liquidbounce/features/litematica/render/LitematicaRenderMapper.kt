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
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaCellStatus
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannedCell
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintPlan
import net.minecraft.core.BlockPos

object LitematicaRenderMapper {
    fun targetsFor(
        plan: LitematicaPrintPlan,
        pendingPositions: Set<LitematicaPosition> = emptySet(),
        blockedPositions: Set<LitematicaPosition> = emptySet(),
    ): List<LitematicaRenderTarget> = plan.cells.mapNotNull { cell ->
        targetFor(
            cell,
            pending = cell.position in pendingPositions,
            blocked = cell.position in blockedPositions,
        )
    }

    fun targetFor(
        cell: LitematicaPlannedCell,
        pending: Boolean = false,
        blocked: Boolean = false,
    ): LitematicaRenderTarget? {
        val style = styleFor(cell, pending, blocked) ?: return null
        return LitematicaRenderTarget(
            position = cell.position.toBlockPos(),
            style = style,
            detail = when {
                blocked -> "RETRY_LIMIT_REACHED"
                pending -> "PENDING_CONFIRMATION"
                else -> cell.blockReason?.name ?: cell.action?.kind?.name
            },
        )
    }

    fun countsFor(plan: LitematicaPrintPlan) = LitematicaPlacementCounts(
        correct = plan.count(LitematicaCellStatus.CORRECT),
        missing = plan.count(LitematicaCellStatus.MISSING, LitematicaCellStatus.SOURCE_FLUID),
        wrong = plan.count(LitematicaCellStatus.WRONG_BLOCK, LitematicaCellStatus.WRONG_STATE),
        extra = plan.count(LitematicaCellStatus.EXTRA),
        pending = plan.count(LitematicaCellStatus.PENDING),
    )

    private fun styleFor(
        cell: LitematicaPlannedCell,
        pending: Boolean,
        blocked: Boolean,
    ): LitematicaTargetStyle? {
        if (blocked) return LitematicaTargetStyle.BLOCKED
        if (pending) return LitematicaTargetStyle.PENDING
        if (cell.status == LitematicaCellStatus.CORRECT) return null
        if (cell.status == LitematicaCellStatus.PENDING) return LitematicaTargetStyle.PENDING
        return cell.action?.kind?.toTargetStyle() ?: LitematicaTargetStyle.BLOCKED
    }

    private fun LitematicaActionKind.toTargetStyle() = when (this) {
        LitematicaActionKind.PLACE,
        LitematicaActionKind.AIR_PLACE,
        -> LitematicaTargetStyle.PLACE
        LitematicaActionKind.BREAK -> LitematicaTargetStyle.BREAK
        LitematicaActionKind.FLUID_PLACE,
        LitematicaActionKind.FLUID_PICKUP,
        -> LitematicaTargetStyle.FLUID
    }

    private fun LitematicaPrintPlan.count(vararg statuses: LitematicaCellStatus): Int =
        statuses.sumOf { status -> statusCounts.getValue(status) }

    private fun LitematicaPosition.toBlockPos() = BlockPos(x, y, z)
}
