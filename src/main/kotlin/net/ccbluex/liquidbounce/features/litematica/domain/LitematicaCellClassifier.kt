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
package net.ccbluex.liquidbounce.features.litematica.domain

internal data class CellClassification(
    val status: LitematicaCellStatus,
    val reason: LitematicaBlockReason? = null,
    val action: LitematicaPrintAction? = null,
)

internal class CellClassifier(
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
        unsupportedClassification()?.let { return it }
        return when (desired.kind) {
            LitematicaBlockKind.AIR -> classifyExtra()
            LitematicaBlockKind.FLUID_SOURCE -> classifyDesiredFluid()
            LitematicaBlockKind.SOLID -> classifyDesiredSolid()
            LitematicaBlockKind.FLUID_FLOWING, LitematicaBlockKind.UNSUPPORTED ->
                blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.UNSUPPORTED_BLOCK)
        }
    }

    private fun unsupportedClassification(): CellClassification? = when {
        desired.kind == LitematicaBlockKind.FLUID_FLOWING || actual.kind == LitematicaBlockKind.FLUID_FLOWING ->
            blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.FLOWING_FLUID)
        !desired.reproducible ->
            blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.NON_REPRODUCIBLE_BLOCK)
        desired.kind == LitematicaBlockKind.UNSUPPORTED || actual.kind == LitematicaBlockKind.UNSUPPORTED ->
            blocked(LitematicaCellStatus.UNSUPPORTED, LitematicaBlockReason.UNSUPPORTED_BLOCK)
        else -> null
    }

    private fun classifyExtra(): CellClassification {
        if (actual.hasBlockEntity && !settings.breakBlockEntities) {
            return blocked(LitematicaCellStatus.PROTECTED, LitematicaBlockReason.BLOCK_ENTITY_PROTECTED)
        }
        if (!settings.breakExtra) return blocked(LitematicaCellStatus.EXTRA, LitematicaBlockReason.BREAK_EXTRA_DISABLED)
        if (actual.kind == LitematicaBlockKind.FLUID_SOURCE) {
            if (!settings.fluids) return blocked(LitematicaCellStatus.EXTRA, LitematicaBlockReason.FLUIDS_DISABLED)
            return actionable(LitematicaCellStatus.EXTRA, LitematicaActionKind.FLUID_PICKUP)
        }
        return actionable(LitematicaCellStatus.EXTRA, LitematicaActionKind.BREAK)
    }

    private fun classifyDesiredFluid(): CellClassification {
        if (actual.kind != LitematicaBlockKind.AIR && !actual.replaceable) return classifyWrong()
        if (!settings.fluids) return blocked(LitematicaCellStatus.SOURCE_FLUID, LitematicaBlockReason.FLUIDS_DISABLED)
        return place(LitematicaCellStatus.SOURCE_FLUID, LitematicaActionKind.FLUID_PLACE)
    }

    private fun classifyDesiredSolid(): CellClassification =
        if (actual.kind == LitematicaBlockKind.AIR || actual.replaceable) placeSolid() else classifyWrong()

    private fun classifyWrong(): CellClassification {
        if (actual.hasBlockEntity && !settings.breakBlockEntities) {
            return blocked(LitematicaCellStatus.PROTECTED, LitematicaBlockReason.BLOCK_ENTITY_PROTECTED)
        }
        val status = if (desired.sameBlockAs(actual)) LitematicaCellStatus.WRONG_STATE else LitematicaCellStatus.WRONG_BLOCK
        if (!settings.breakWrong) return blocked(status, LitematicaBlockReason.BREAK_WRONG_DISABLED)
        if (actual.kind != LitematicaBlockKind.FLUID_SOURCE) return actionable(status, LitematicaActionKind.BREAK)
        if (!settings.fluids) return blocked(status, LitematicaBlockReason.FLUIDS_DISABLED)
        return actionable(status, LitematicaActionKind.FLUID_PICKUP)
    }

    private fun placeSolid(): CellClassification {
        if (!cell.materialAvailable) return blocked(LitematicaCellStatus.MISSING, LitematicaBlockReason.MISSING_MATERIAL)
        return when (cell.placementMethod) {
            LitematicaPlacementMethod.NEIGHBOR_FACE -> actionable(
                LitematicaCellStatus.MISSING, LitematicaActionKind.PLACE,
            )
            LitematicaPlacementMethod.AIR_PLACE -> classifyAirPlace()
            LitematicaPlacementMethod.UNAVAILABLE -> blocked(
                LitematicaCellStatus.MISSING, LitematicaBlockReason.NO_PLACEMENT_FACE,
            )
        }
    }

    private fun classifyAirPlace(): CellClassification =
        if (settings.airPlace == LitematicaAirPlaceMode.SMART) {
            actionable(LitematicaCellStatus.MISSING, LitematicaActionKind.AIR_PLACE)
        } else {
            blocked(LitematicaCellStatus.MISSING, LitematicaBlockReason.AIR_PLACE_DISABLED)
        }

    private fun place(status: LitematicaCellStatus, kind: LitematicaActionKind): CellClassification {
        if (!cell.materialAvailable) return blocked(status, LitematicaBlockReason.MISSING_MATERIAL)
        return actionable(status, kind)
    }

    private fun actionable(status: LitematicaCellStatus, kind: LitematicaActionKind) = CellClassification(
        status = status,
        action = LitematicaPrintAction(
            cell.position, placementId, kind, kind.priority, desired, actual, distanceSquared,
        ),
    )

    private fun blocked(status: LitematicaCellStatus, reason: LitematicaBlockReason) =
        CellClassification(status = status, reason = reason)
}

private val LitematicaActionKind.priority: LitematicaActionPriority
    get() = when (this) {
        LitematicaActionKind.BREAK, LitematicaActionKind.FLUID_PICKUP -> LitematicaActionPriority.CLEANUP
        LitematicaActionKind.PLACE -> LitematicaActionPriority.SUPPORTED_SOLID
        LitematicaActionKind.AIR_PLACE -> LitematicaActionPriority.AIRPLACE_SOLID
        LitematicaActionKind.FLUID_PLACE -> LitematicaActionPriority.DESIRED_FLUID
    }
