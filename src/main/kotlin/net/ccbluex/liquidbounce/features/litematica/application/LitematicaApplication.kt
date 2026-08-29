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
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActivationMode
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannerSettings
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPoint
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintPlan
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceExecutionGate
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceOwnershipLease
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementPositionProvider
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPositionProviderLease
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaHudSnapshot
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaRenderMapper
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaRenderSink
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaRenderSnapshot
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaVerifierTotals
import net.ccbluex.liquidbounce.features.litematica.runtime.LitematicaPrinterRuntime
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterActivationMode
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterCleanup
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionId
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionKind
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionOutcome
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionStart
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterPauseReason
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterRuntimePolicy
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterRuntimeSnapshot
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterSyncCommand
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterTickInput
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.player

data class LitematicaApplicationSettings(
    val planner: LitematicaPlannerSettings,
    val swingMode: SwingMode,
)

@Suppress("TooManyFunctions")
class LitematicaApplication(
    private val port: LitematicaPort,
    private val actionDriver: MinecraftLitematicaActionDriver,
    private val conflictSource: MinecraftLitematicaConflictSource,
    private val renderSink: LitematicaRenderSink,
    private val setPrinterToggle: (Boolean) -> Unit,
    private val runtime: LitematicaPrinterRuntime<LitematicaPosition> = LitematicaPrinterRuntime(),
) {

    private val scanCoordinator = LitematicaScanCoordinator(port)
    private var positionProviderLease = LitematicaPositionProviderLease.NONE
    private var easyPlaceOwnership: LitematicaEasyPlaceOwnershipLease? = null
    private val pendingActions = linkedMapOf<PrinterInteractionId, LitematicaPrintAction>()
    private val plan: LitematicaPrintPlan
        get() = scanCoordinator.plan
    private var target: LitematicaPrintAction? = null
    private var aimAvailable = false
    private var tick = 0L

    fun enable(printerToggle: Boolean, settings: LitematicaApplicationSettings) {
        val easyPlace = port.easyPlaceSnapshot()
        val enabled = runtime.enable(printerToggle, easyPlace.enabled)
        applyCleanup(enabled.cleanup)
        runtime.setActivationMode(settings.planner.activation.toRuntimeMode())
        applySync(enabled.syncCommands)
        reconcileOwnership()
    }

    fun printerToggleChanged(enabled: Boolean) {
        applySync(runtime.printerToggleChanged(enabled))
        if (!enabled) {
            applyCleanup(runtime.placementChanged())
            resetScan()
        }
        reconcileOwnership()
    }

    fun activationChanged(mode: LitematicaActivationMode) {
        runtime.setActivationMode(mode.toRuntimeMode())
    }

    fun rotationUpdate(settings: LitematicaApplicationSettings) {
        if (!runtime.snapshot.printerEnabled) {
            target = null
            aimAvailable = false
            return
        }
        val action = ownedMiningAction() ?: plan.target
        target = action
        val conflict = LitematicaConflictPolicy.firstPause(
            conflictSource.capture(
                rotationUnavailable = false,
                allowOwnedMiningAutoTool = runtime.snapshot.ownedMiningSession != null,
            ),
        )
        if (conflict != null) {
            aimAvailable = false
            return
        }
        aimAvailable = action?.let {
            actionDriver.requestAim(it, interactionFor(it), settings.planner.range)
        } == true
    }

    fun tick(settings: LitematicaApplicationSettings) {
        tick++
        val easyPlace = port.easyPlaceSnapshot()
        applySync(runtime.easyPlaceChanged(easyPlace.enabled))
        runtime.setActivationMode(settings.planner.activation.toRuntimeMode())
        reconcileOwnership()
        if (!runtime.snapshot.printerEnabled) {
            target = null
            aimAvailable = false
            renderSink.clear()
            return
        }
        scan(settings.planner)
        confirmCompletedActions()

        val rotationUnavailable = target != null && (!aimAvailable || !actionDriver.rotationReady(
            requireNotNull(target),
            settings.planner.range,
        ))
        val externalPause = externalPauseReason(rotationUnavailable)
        val tickResult = runtime.beginTick(
            PrinterTickInput(
                tick = tick,
                litematicaKeyActive = easyPlace.hotkey.easyPlaceHeld,
                externalPauseReason = externalPause,
                policy = PrinterRuntimePolicy(
                    actionDelayTicks = settings.planner.actionDelayTicks.coerceAtLeast(1),
                    retryLimit = settings.planner.retryLimit,
                ),
            ),
        )
        tickResult.timedOutInteractions.forEach { timedOut ->
            pendingActions.remove(timedOut.id)
            if (timedOut.kind == PrinterInteractionKind.BREAK) actionDriver.stopMining()
        }

        val mining = tickResult.miningSessionToContinue
        if (mining != null) {
            val action = pendingActions[mining.interactionId]
            if (action != null && actionDriver.rotationReady(action, settings.planner.range)) {
                actionDriver.continueMining(action, settings.swingMode)
            }
            publishRender(settings)
            return
        }

        val action = plan.target
        if (action != null && actionDriver.rotationReady(action, settings.planner.range)) {
            start(action, settings)
        }
        publishRender(settings)
    }

    fun worldChanged() {
        applyCleanup(runtime.worldChanged())
        resetScan()
    }

    fun disable() {
        applyCleanup(runtime.disable())
        resetScan()
        port.close()
        actionDriver.reset()
    }

    private fun start(action: LitematicaPrintAction, settings: LitematicaApplicationSettings) {
        val kind = action.kind.toRuntimeKind()
        val accepted = runtime.startInteraction(action.target, kind) as? PrinterInteractionStart.Accepted ?: return
        val pending = accepted.interaction
        pendingActions[pending.id] = action
        val token = if (action.kind == LitematicaActionKind.PLACE || action.kind == LitematicaActionKind.AIR_PLACE) {
            easyPlaceOwnership?.beginExecution()
        } else {
            null
        }
        val result = try {
            actionDriver.execute(
                action = action,
                materialId = materialFor(action),
                port = port,
                token = token,
                swingMode = settings.swingMode,
                resetDelayTicks = settings.planner.actionDelayTicks + 2,
            )
        } finally {
            token?.close()
        }
        if (result != LitematicaExecutionResult.SUBMITTED) {
            runtime.confirmInteraction(pending.id, PrinterInteractionOutcome.FAILURE)
            pendingActions.remove(pending.id)
        }
    }

    private fun scan(settings: LitematicaPlannerSettings) {
        if (!runtime.snapshot.printerEnabled) return
        val eye = player.eyePosition
        val update = scanCoordinator.scan(
            center = LitematicaPoint(eye.x, eye.y, eye.z),
            settings = settings,
            pendingPositions = runtime.snapshot.pendingTargets,
        )
        if (update?.placementChanged == true) {
            applyCleanup(runtime.placementChanged())
        }
    }

    private fun confirmCompletedActions() {
        val cells = plan.cells.associateBy { it.position }
        runtime.snapshot.pendingInteractions.toList().forEach { pending ->
            val action = pendingActions[pending.id] ?: return@forEach
            val cell = cells[action.target] ?: return@forEach
            if (!action.confirmedBy(cell.actual)) return@forEach
            runtime.confirmInteraction(pending.id, PrinterInteractionOutcome.SUCCESS)
            pendingActions.remove(pending.id)
        }
    }

    private fun externalPauseReason(rotationUnavailable: Boolean): PrinterPauseReason? {
        val conflict = LitematicaConflictPolicy.firstPause(
            conflictSource.capture(
                rotationUnavailable = rotationUnavailable,
                allowOwnedMiningAutoTool = runtime.snapshot.ownedMiningSession != null,
            ),
        )
        if (conflict != null) return conflict.toPauseReason()
        if (plan.selectedPlacement == null) return PrinterPauseReason.NO_PLACEMENT
        if (plan.target == null && runtime.snapshot.ownedMiningSession == null) return PrinterPauseReason.NO_ACTION
        return null
    }

    private fun reconcileOwnership() {
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

    private fun applySync(commands: List<PrinterSyncCommand>) {
        commands.forEach { command ->
            when (command) {
                is PrinterSyncCommand.SetPrinterToggle -> setPrinterToggle(command.enabled)
                is PrinterSyncCommand.SetEasyPlace -> port.setEasyPlaceEnabled(command.enabled)
            }
        }
    }

    private fun applyCleanup(cleanup: PrinterCleanup<LitematicaPosition>) {
        cleanup.cancelledInteractions.forEach { pendingActions.remove(it.id) }
        if (cleanup.miningSessionToStop != null) actionDriver.stopMining()
        if (cleanup.removePositionProvider) closeOwnership()
        if (cleanup.clearOverlays) renderSink.clear()
    }

    private fun closeOwnership() {
        positionProviderLease.close()
        positionProviderLease = LitematicaPositionProviderLease.NONE
        easyPlaceOwnership?.close()
        easyPlaceOwnership = null
        actionDriver.reset()
    }

    private fun resetScan() {
        scanCoordinator.reset()
        pendingActions.clear()
        target = null
        aimAvailable = false
    }

    private fun publishRender(settings: LitematicaApplicationSettings) {
        val runtime = runtime.snapshot
        val selectedId = plan.selectedPlacement?.id
        val verifier = selectedId?.let(port::verifier)?.let {
            LitematicaVerifierTotals(
                correct = it.correct,
                missing = it.missing,
                wrong = it.wrongState + it.wrongBlockEntityData,
                extra = it.extra,
            )
        }
        val current = ownedMiningAction() ?: plan.target
        renderSink.update(
            LitematicaRenderSnapshot(
                targets = LitematicaRenderMapper.targetsFor(
                    plan = plan,
                    pendingPositions = runtime.pendingTargets,
                    blockedPositions = setOfNotNull(runtime.failedTarget),
                ),
                hud = LitematicaHudSnapshot(
                    placementName = plan.selectedPlacement?.name,
                    activationMode = settings.planner.activation.displayName(),
                    counts = LitematicaRenderMapper.countsFor(plan),
                    currentTarget = current?.target?.toBlockPos(),
                    missingMaterial = plan.missingMaterials.firstOrNull(),
                    pauseReason = runtime.pauseReason?.displayName(),
                    retryCount = current?.target?.let { runtime.failureCounts[it] } ?: 0,
                    verifierTotals = verifier,
                ),
            ),
        )
    }

    private fun ownedMiningAction(): LitematicaPrintAction? {
        val mining = runtime.snapshot.ownedMiningSession ?: return null
        return pendingActions[mining.interactionId]
    }

    private fun interactionFor(action: LitematicaPrintAction) = scanCoordinator.interactionFor(action)

    private fun materialFor(action: LitematicaPrintAction): String? = scanCoordinator.materialFor(action)

    internal fun runtimeSnapshot(): PrinterRuntimeSnapshot<LitematicaPosition> = runtime.snapshot

}

private fun LitematicaPrintAction.confirmedBy(actual: LitematicaBlockSnapshot): Boolean =
    when (kind) {
        LitematicaActionKind.PLACE,
        LitematicaActionKind.AIR_PLACE,
        LitematicaActionKind.FLUID_PLACE,
        -> desired.sameStateAs(actual)
        LitematicaActionKind.BREAK -> actual.kind == LitematicaBlockKind.AIR || actual.replaceable
        LitematicaActionKind.FLUID_PICKUP -> actual.kind != LitematicaBlockKind.FLUID_SOURCE
    }

private fun LitematicaActionKind.toRuntimeKind(): PrinterInteractionKind = when (this) {
    LitematicaActionKind.PLACE,
    LitematicaActionKind.AIR_PLACE,
    -> PrinterInteractionKind.PLACE
    LitematicaActionKind.BREAK -> PrinterInteractionKind.BREAK
    LitematicaActionKind.FLUID_PLACE -> PrinterInteractionKind.FLUID_PLACE
    LitematicaActionKind.FLUID_PICKUP -> PrinterInteractionKind.FLUID_PICKUP
}

private fun LitematicaActivationMode.toRuntimeMode(): PrinterActivationMode = when (this) {
    LitematicaActivationMode.LITEMATICA_KEY -> PrinterActivationMode.LITEMATICA_KEY
    LitematicaActivationMode.CONTINUOUS -> PrinterActivationMode.CONTINUOUS
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

private fun LitematicaActivationMode.displayName(): String = when (this) {
    LitematicaActivationMode.LITEMATICA_KEY -> "LitematicaKey"
    LitematicaActivationMode.CONTINUOUS -> "Continuous"
}

private fun PrinterPauseReason.displayName(): String = name.lowercase()
    .split('_')
    .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
