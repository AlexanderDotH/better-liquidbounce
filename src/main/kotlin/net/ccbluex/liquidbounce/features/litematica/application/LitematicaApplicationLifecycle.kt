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

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActivationMode
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceExecutionGate
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementPositionProvider
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPositionProviderLease
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaHudSnapshot
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaRenderMapper
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaRenderSnapshot
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaVerifierTotals
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterCleanup
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterPauseReason
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterSyncCommand

internal fun LitematicaApplication.reconcileOwnership() {
    if (!runtime.snapshot.printerEnabled) {
        closeOwnership()
        return
    }
    if (easyPlaceOwnership?.isActive != true) {
        easyPlaceOwnership = LitematicaEasyPlaceExecutionGate.acquirePrinterOwnership()
    }
    if (!positionProviderLease.isActive) {
        positionProviderLease = port.installPositionProvider(
            LitematicaPlacementPositionProvider { target?.target?.toBlockPos() },
        )
    }
}

internal fun LitematicaApplication.applySync(commands: List<PrinterSyncCommand>) {
    commands.forEach { command ->
        when (command) {
            is PrinterSyncCommand.SetPrinterToggle -> setPrinterToggle(command.enabled)
            is PrinterSyncCommand.SetEasyPlace -> port.setEasyPlaceEnabled(command.enabled)
        }
    }
}

internal fun LitematicaApplication.applyCleanup(cleanup: PrinterCleanup<LitematicaPosition>) {
    cleanup.cancelledInteractions.forEach { pendingActions.remove(it.id) }
    if (cleanup.miningSessionToStop != null) actionDriver.stopMining()
    if (cleanup.removePositionProvider) closeOwnership()
    if (cleanup.clearOverlays) renderSink.clear()
}

internal fun LitematicaApplication.closeOwnership() {
    positionProviderLease.close()
    positionProviderLease = LitematicaPositionProviderLease.NONE
    easyPlaceOwnership?.close()
    easyPlaceOwnership = null
    actionDriver.reset()
}

internal fun LitematicaApplication.resetScan() {
    scanCoordinator.reset()
    pendingActions.clear()
    target = null
    aimAvailable = false
}

internal fun LitematicaApplication.publishRender(settings: LitematicaApplicationSettings) {
    val runtimeSnapshot = runtime.snapshot
    val current = ownedMiningAction() ?: plan.target
    renderSink.update(
        LitematicaRenderSnapshot(
            targets = LitematicaRenderMapper.targetsFor(
                plan, runtimeSnapshot.pendingTargets, setOfNotNull(runtimeSnapshot.failedTarget),
            ),
            hud = LitematicaHudSnapshot(
                placementName = plan.selectedPlacement?.name,
                activationMode = settings.planner.activation.displayName(),
                counts = LitematicaRenderMapper.countsFor(plan),
                currentTarget = current?.target?.toBlockPos(),
                missingMaterial = plan.missingMaterials.firstOrNull(),
                pauseReason = runtimeSnapshot.pauseReason?.displayName(),
                retryCount = current?.target?.let { runtimeSnapshot.failureCounts[it] } ?: 0,
                verifierTotals = verifierTotals(),
            ),
        ),
    )
}

private fun LitematicaApplication.verifierTotals(): LitematicaVerifierTotals? {
    val selectedId = plan.selectedPlacement?.id ?: return null
    return port.verifier(selectedId)?.let {
        LitematicaVerifierTotals(
            correct = it.correct,
            missing = it.missing,
            wrong = it.wrongState + it.wrongBlockEntityData,
            extra = it.extra,
        )
    }
}

internal fun LitematicaApplication.ownedMiningAction(): LitematicaPrintAction? {
    val mining = runtime.snapshot.ownedMiningSession ?: return null
    return pendingActions[mining.interactionId]
}

private fun LitematicaActivationMode.displayName(): String = when (this) {
    LitematicaActivationMode.LITEMATICA_KEY -> "LitematicaKey"
    LitematicaActivationMode.CONTINUOUS -> "Continuous"
}

private fun PrinterPauseReason.displayName(): String = name.lowercase()
    .split('_')
    .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
