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
package net.ccbluex.liquidbounce.features.litematica.runtime

/**
 * Pure printer lifecycle and confirmation state. Minecraft and Litematica I/O belongs to the caller.
 *
 * Each call to [beginTick] opens one scheduling window. At most one new interaction can be accepted
 * in that window, while confirmations from earlier windows remain independently pending.
 */
class LitematicaPrinterRuntime<T : Any>(
    internal val timeSource: PrinterTimeSource = PrinterTimeSource.SYSTEM,
) {

    internal val synchronizer = EasyPlaceSynchronizer()
    internal val pendingInteractions = linkedMapOf<PrinterInteractionId, PendingPrinterInteraction<T>>()
    internal val failureCounts = linkedMapOf<T, Int>()
    internal var phase = PrinterRuntimePhase.DISABLED
    internal var pauseReason: PrinterPauseReason? = null
    internal var activationMode = PrinterActivationMode.LITEMATICA_KEY
    internal var policy = PrinterRuntimePolicy()
    internal var moduleEnabled = false
    internal var litematicaKeyActive = false
    internal var externalPauseReason: PrinterPauseReason? = null
    internal var currentTick: Long? = null
    internal var lastStartedTick: Long? = null
    internal var lastObservedTime: Long? = null
    internal var nextInteractionId = 1L
    internal var ownedMiningSession: PrinterMiningSession<T>? = null
    internal var failedTarget: T? = null

    val snapshot: PrinterRuntimeSnapshot<T>
        get() = PrinterRuntimeSnapshot(
            phase = phase,
            pauseReason = pauseReason,
            activationMode = activationMode,
            printerEnabled = synchronizer.printerEnabled,
            easyPlaceEnabled = synchronizer.easyPlaceEnabled,
            policy = policy,
            pendingInteractions = pendingInteractions.values.toList(),
            ownedMiningSession = ownedMiningSession,
            failureCounts = failureCounts.toMap(),
            failedTarget = failedTarget,
        )

    /** Enabling an already enabled runtime is a manual re-enable and resets all owned state. */
    fun enable(currentPrinterToggle: Boolean, currentEasyPlace: Boolean): PrinterEnableResult<T> {
        val cleanup = clearOwnedState(
            removePositionProvider = moduleEnabled,
            clearOverlays = moduleEnabled,
        )
        moduleEnabled = true
        phase = PrinterRuntimePhase.IDLE
        val commands = synchronizer.activate(currentPrinterToggle, currentEasyPlace)
        refreshPhase()
        return PrinterEnableResult(commands, cleanup)
    }

    fun disable(): PrinterCleanup<T> {
        moduleEnabled = false
        synchronizer.deactivate()
        val cleanup = clearOwnedState(removePositionProvider = true, clearOverlays = true)
        phase = PrinterRuntimePhase.DISABLED
        pauseReason = null
        return cleanup
    }

    fun worldChanged(): PrinterCleanup<T> {
        val cleanup = clearOwnedState(removePositionProvider = true, clearOverlays = true)
        refreshPhase()
        return cleanup
    }

    fun placementChanged(): PrinterCleanup<T> {
        val cleanup = clearOwnedState(removePositionProvider = false, clearOverlays = true)
        refreshPhase()
        return cleanup
    }

    fun setActivationMode(mode: PrinterActivationMode) {
        activationMode = mode
        refreshPhase()
    }

    fun printerToggleChanged(enabled: Boolean): List<PrinterSyncCommand> {
        val commands = synchronizer.printerChanged(enabled)
        refreshPhase()
        return commands
    }

    fun easyPlaceChanged(enabled: Boolean): List<PrinterSyncCommand> {
        val commands = synchronizer.easyPlaceChanged(enabled)
        refreshPhase()
        return commands
    }

    fun beginTick(input: PrinterTickInput): PrinterTickResult<T> {
        if (!moduleEnabled) return PrinterTickResult(emptyList(), null)
        require(currentTick == null || input.tick >= requireNotNull(currentTick)) {
            "Printer ticks must be monotonic"
        }

        currentTick = input.tick
        policy = input.policy
        litematicaKeyActive = input.litematicaKeyActive
        externalPauseReason = input.externalPauseReason
        val now = monotonicTime()
        val timedOut = expireInteractions(now)
        lockTargetExceedingRetryLimit()
        refreshPhase()
        return PrinterTickResult(
            timedOutInteractions = timedOut,
            miningSessionToContinue = ownedMiningSession.takeIf { phase == PrinterRuntimePhase.READY },
        )
    }

    fun startInteraction(target: T, kind: PrinterInteractionKind): PrinterInteractionStart<T> {
        val tick = currentTick ?: return rejected(PrinterInteractionRejection.TICK_NOT_STARTED)
        if (phase != PrinterRuntimePhase.READY) return rejected(PrinterInteractionRejection.RUNTIME_NOT_READY)
        val previousStart = lastStartedTick
        if (previousStart == tick) return rejected(PrinterInteractionRejection.ALREADY_STARTED_THIS_TICK)
        if (previousStart != null && tick - previousStart < policy.actionDelayTicks) {
            return rejected(PrinterInteractionRejection.ACTION_DELAY)
        }
        if (pendingInteractions.values.any { it.target == target }) {
            return rejected(PrinterInteractionRejection.TARGET_PENDING)
        }
        if (ownedMiningSession != null) return rejected(PrinterInteractionRejection.MINING_IN_PROGRESS)

        val interaction = PendingPrinterInteraction(
            id = nextId(),
            target = target,
            kind = kind,
            startedAtMillis = monotonicTime(),
        )
        pendingInteractions[interaction.id] = interaction
        lastStartedTick = tick
        if (kind == PrinterInteractionKind.BREAK) {
            ownedMiningSession = PrinterMiningSession(interaction.id, target)
        }
        return PrinterInteractionStart.Accepted(interaction)
    }

    fun confirmInteraction(
        id: PrinterInteractionId,
        outcome: PrinterInteractionOutcome,
    ): PrinterConfirmation<T> {
        val interaction = pendingInteractions.remove(id) ?: return unmatchedConfirmation()
        releaseMiningSession(id)
        val failureCount = when (outcome) {
            PrinterInteractionOutcome.SUCCESS -> failureCounts.remove(interaction.target).let { 0 }
            PrinterInteractionOutcome.FAILURE -> registerFailure(interaction.target)
        }
        refreshPhase()
        return PrinterConfirmation(
            matched = true,
            interaction = interaction,
            failureCount = failureCount,
            paused = phase == PrinterRuntimePhase.PAUSED,
        )
    }

}
