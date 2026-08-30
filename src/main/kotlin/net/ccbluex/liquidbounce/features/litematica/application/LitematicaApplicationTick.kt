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

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActionKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannerSettings
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPoint
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionKind
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionOutcome
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionStart
import net.ccbluex.liquidbounce.features.litematica.runtime.PendingPrinterInteraction
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterPauseReason
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterRuntimePolicy
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterTickInput
import net.ccbluex.liquidbounce.utils.client.player

internal fun LitematicaApplication.tickApplication(settings: LitematicaApplicationSettings) {
    tick++
    val easyPlace = port.easyPlaceSnapshot()
    applySync(runtime.easyPlaceChanged(easyPlace.enabled))
    runtime.setActivationMode(settings.planner.activation.toRuntimeMode())
    reconcileOwnership()
    if (!runtime.snapshot.printerEnabled) {
        clearDisabledTick()
        return
    }
    scan(settings.planner)
    confirmCompletedActions()
    val tickResult = runtime.beginTick(tickInput(settings, easyPlace.hotkey.easyPlaceHeld))
    clearTimedOutInteractions(tickResult.timedOutInteractions)
    if (continueOwnedMining(tickResult.miningSessionToContinue?.interactionId, settings)) return
    startReadyPlanTarget(settings)
    publishRender(settings)
}

private fun LitematicaApplication.clearDisabledTick() {
    target = null
    aimAvailable = false
    renderSink.clear()
}

private fun LitematicaApplication.tickInput(
    settings: LitematicaApplicationSettings,
    easyPlaceHeld: Boolean,
) = PrinterTickInput(
    tick = tick,
    litematicaKeyActive = easyPlaceHeld,
    externalPauseReason = externalPauseReason(rotationUnavailable(settings)),
    policy = PrinterRuntimePolicy(
        actionDelayTicks = settings.planner.actionDelayTicks.coerceAtLeast(1),
        retryLimit = settings.planner.retryLimit,
    ),
)

private fun LitematicaApplication.rotationUnavailable(settings: LitematicaApplicationSettings): Boolean =
    target != null && (!aimAvailable || !actionDriver.rotationReady(requireNotNull(target), settings.planner.range))

private fun LitematicaApplication.clearTimedOutInteractions(
    interactions: Collection<PendingPrinterInteraction<LitematicaPosition>>,
) {
    interactions.forEach { timedOut ->
        pendingActions.remove(timedOut.id)
        if (timedOut.kind == PrinterInteractionKind.BREAK) actionDriver.stopMining()
    }
}

private fun LitematicaApplication.continueOwnedMining(
    interactionId: net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionId?,
    settings: LitematicaApplicationSettings,
): Boolean {
    if (interactionId == null) return false
    val action = pendingActions[interactionId]
    if (action != null && actionDriver.rotationReady(action, settings.planner.range)) {
        actionDriver.continueMining(action, settings.swingMode)
    }
    publishRender(settings)
    return true
}

private fun LitematicaApplication.startReadyPlanTarget(settings: LitematicaApplicationSettings) {
    val action = plan.target ?: return
    if (actionDriver.rotationReady(action, settings.planner.range)) start(action, settings)
}

private fun LitematicaApplication.start(action: LitematicaPrintAction, settings: LitematicaApplicationSettings) {
    val accepted = runtime.startInteraction(action.target, action.kind.toRuntimeKind())
        as? PrinterInteractionStart.Accepted ?: return
    val pending = accepted.interaction
    pendingActions[pending.id] = action
    val token = if (action.kind.isPlacement()) easyPlaceOwnership?.beginExecution() else null
    val result = try {
        actionDriver.execute(
            action, materialFor(action), port, token, settings.swingMode, settings.planner.actionDelayTicks + 2,
        )
    } finally {
        token?.close()
    }
    if (result == LitematicaExecutionResult.SUBMITTED) return
    runtime.confirmInteraction(pending.id, PrinterInteractionOutcome.FAILURE)
    pendingActions.remove(pending.id)
}

private fun LitematicaActionKind.isPlacement(): Boolean =
    this == LitematicaActionKind.PLACE || this == LitematicaActionKind.AIR_PLACE

private fun LitematicaApplication.scan(settings: LitematicaPlannerSettings) {
    if (!runtime.snapshot.printerEnabled) return
    val eye = player.eyePosition
    val update = scanCoordinator.scan(
        LitematicaPoint(eye.x, eye.y, eye.z), settings, runtime.snapshot.pendingTargets,
    )
    if (update?.placementChanged == true) applyCleanup(runtime.placementChanged())
}

private fun LitematicaApplication.confirmCompletedActions() {
    val cells = plan.cells.associateBy { it.position }
    runtime.snapshot.pendingInteractions.toList().forEach { pending ->
        val action = pendingActions[pending.id] ?: return@forEach
        val cell = cells[action.target] ?: return@forEach
        if (!action.confirmedBy(cell.actual)) return@forEach
        runtime.confirmInteraction(pending.id, PrinterInteractionOutcome.SUCCESS)
        pendingActions.remove(pending.id)
    }
}

private fun LitematicaApplication.externalPauseReason(rotationUnavailable: Boolean): PrinterPauseReason? {
    val conflict = LitematicaConflictPolicy.firstPause(
        conflictSource.capture(rotationUnavailable, runtime.snapshot.ownedMiningSession != null),
    )
    if (conflict != null) return conflict.toPauseReason()
    if (plan.selectedPlacement == null) return PrinterPauseReason.NO_PLACEMENT
    if (plan.target == null && runtime.snapshot.ownedMiningSession == null) return PrinterPauseReason.NO_ACTION
    return null
}

private fun LitematicaPrintAction.confirmedBy(actual: LitematicaBlockSnapshot): Boolean = when (kind) {
    LitematicaActionKind.PLACE, LitematicaActionKind.AIR_PLACE, LitematicaActionKind.FLUID_PLACE ->
        desired.sameStateAs(actual)
    LitematicaActionKind.BREAK -> actual.kind == LitematicaBlockKind.AIR || actual.replaceable
    LitematicaActionKind.FLUID_PICKUP -> actual.kind != LitematicaBlockKind.FLUID_SOURCE
}

private fun LitematicaActionKind.toRuntimeKind(): PrinterInteractionKind = when (this) {
    LitematicaActionKind.PLACE, LitematicaActionKind.AIR_PLACE -> PrinterInteractionKind.PLACE
    LitematicaActionKind.BREAK -> PrinterInteractionKind.BREAK
    LitematicaActionKind.FLUID_PLACE -> PrinterInteractionKind.FLUID_PLACE
    LitematicaActionKind.FLUID_PICKUP -> PrinterInteractionKind.FLUID_PICKUP
}

private fun LitematicaConflict.toPauseReason(): PrinterPauseReason = when (this) {
    LitematicaConflict.PACKET_MINE -> PrinterPauseReason.PACKET_MINE_ACTIVE
    LitematicaConflict.SCAFFOLD -> PrinterPauseReason.SCAFFOLD_ACTIVE
    LitematicaConflict.AUTO_BUILD -> PrinterPauseReason.AUTO_BUILD_ACTIVE
    LitematicaConflict.FUCKER -> PrinterPauseReason.FUCKER_ACTIVE
    LitematicaConflict.BLINK -> PrinterPauseReason.BLINK_ACTIVE
    LitematicaConflict.FOREIGN_SILENT_HOTBAR -> PrinterPauseReason.SILENT_HOTBAR_BUSY
    LitematicaConflict.CONTAINER_SCREEN -> PrinterPauseReason.CONTAINER_OPEN
    LitematicaConflict.ITEM_USE -> PrinterPauseReason.USING_ITEM
    LitematicaConflict.ROTATION_UNAVAILABLE -> PrinterPauseReason.HIGHER_PRIORITY_ROTATION
}

private fun LitematicaApplication.materialFor(action: LitematicaPrintAction): String? =
    scanCoordinator.materialFor(action)
