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
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannerSettings
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintPlan
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceOwnershipLease
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPositionProviderLease
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaRenderSink
import net.ccbluex.liquidbounce.features.litematica.runtime.LitematicaPrinterRuntime
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterActivationMode
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterInteractionId
import net.ccbluex.liquidbounce.features.litematica.runtime.PrinterRuntimeSnapshot
import net.ccbluex.liquidbounce.utils.block.SwingMode

data class LitematicaApplicationSettings(
    val planner: LitematicaPlannerSettings,
    val swingMode: SwingMode,
)

class LitematicaApplication(
    internal val port: LitematicaPort,
    internal val actionDriver: MinecraftLitematicaActionDriver,
    internal val conflictSource: MinecraftLitematicaConflictSource,
    internal val renderSink: LitematicaRenderSink,
    internal val setPrinterToggle: (Boolean) -> Unit,
    internal val runtime: LitematicaPrinterRuntime<LitematicaPosition> = LitematicaPrinterRuntime(),
) {

    internal val scanCoordinator = LitematicaScanCoordinator(port)
    internal var positionProviderLease = LitematicaPositionProviderLease.NONE
    internal var easyPlaceOwnership: LitematicaEasyPlaceOwnershipLease? = null
    internal val pendingActions = linkedMapOf<PrinterInteractionId, LitematicaPrintAction>()
    internal val plan: LitematicaPrintPlan
        get() = scanCoordinator.plan
    internal var target: LitematicaPrintAction? = null
    internal var aimAvailable = false
    internal var tick = 0L

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
        tickApplication(settings)
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

    private fun interactionFor(action: LitematicaPrintAction) = scanCoordinator.interactionFor(action)

    internal fun runtimeSnapshot(): PrinterRuntimeSnapshot<LitematicaPosition> = runtime.snapshot
}

internal fun LitematicaActivationMode.toRuntimeMode(): PrinterActivationMode = when (this) {
    LitematicaActivationMode.LITEMATICA_KEY -> PrinterActivationMode.LITEMATICA_KEY
    LitematicaActivationMode.CONTINUOUS -> PrinterActivationMode.CONTINUOUS
}
