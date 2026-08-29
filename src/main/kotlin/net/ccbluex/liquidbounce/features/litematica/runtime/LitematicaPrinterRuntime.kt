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
@Suppress("TooManyFunctions")
class LitematicaPrinterRuntime<T : Any>(
    private val timeSource: PrinterTimeSource = PrinterTimeSource.SYSTEM,
) {

    private val synchronizer = EasyPlaceSynchronizer()
    private val pendingInteractions = linkedMapOf<PrinterInteractionId, PendingPrinterInteraction<T>>()
    private val failureCounts = linkedMapOf<T, Int>()

    private var phase = PrinterRuntimePhase.DISABLED
    private var pauseReason: PrinterPauseReason? = null
    private var activationMode = PrinterActivationMode.LITEMATICA_KEY
    private var policy = PrinterRuntimePolicy()
    private var moduleEnabled = false
    private var litematicaKeyActive = false
    private var externalPauseReason: PrinterPauseReason? = null
    private var currentTick: Long? = null
    private var lastStartedTick: Long? = null
    private var lastObservedTime: Long? = null
    private var nextInteractionId = 1L
    private var ownedMiningSession: PrinterMiningSession<T>? = null
    private var failedTarget: T? = null

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

    private fun expireInteractions(now: Long): List<PendingPrinterInteraction<T>> {
        val timedOut = pendingInteractions.values.filter {
            now - it.startedAtMillis >= policy.confirmationTimeoutMillis
        }
        timedOut.forEach { interaction ->
            pendingInteractions.remove(interaction.id)
            releaseMiningSession(interaction.id)
            registerFailure(interaction.target)
        }
        return timedOut
    }

    private fun registerFailure(target: T): Int {
        val failures = failureCounts.getOrDefault(target, 0) + 1
        failureCounts[target] = failures
        if (failures >= policy.retryLimit && failedTarget == null) failedTarget = target
        return failures
    }

    private fun lockTargetExceedingRetryLimit() {
        if (failedTarget != null) return
        failedTarget = failureCounts.entries.firstOrNull { it.value >= policy.retryLimit }?.key
    }

    private fun refreshPhase() {
        when {
            !moduleEnabled -> setPhase(PrinterRuntimePhase.DISABLED, null)
            failedTarget != null -> setPhase(PrinterRuntimePhase.PAUSED, PrinterPauseReason.RETRY_LIMIT_REACHED)
            !synchronizer.printerEnabled -> setPhase(
                PrinterRuntimePhase.IDLE,
                PrinterPauseReason.PRINTER_TOGGLE_DISABLED,
            )
            activationMode == PrinterActivationMode.LITEMATICA_KEY && !litematicaKeyActive -> setPhase(
                PrinterRuntimePhase.IDLE,
                PrinterPauseReason.LITEMATICA_KEY_IDLE,
            )
            externalPauseReason != null -> setPhase(PrinterRuntimePhase.PAUSED, externalPauseReason)
            currentTick == null -> setPhase(PrinterRuntimePhase.IDLE, null)
            else -> setPhase(PrinterRuntimePhase.READY, null)
        }
    }

    private fun setPhase(newPhase: PrinterRuntimePhase, reason: PrinterPauseReason?) {
        phase = newPhase
        pauseReason = reason
    }

    private fun clearOwnedState(
        removePositionProvider: Boolean,
        clearOverlays: Boolean,
    ): PrinterCleanup<T> {
        val cleanup = PrinterCleanup(
            cancelledInteractions = pendingInteractions.values.toList(),
            miningSessionToStop = ownedMiningSession,
            removePositionProvider = removePositionProvider,
            clearOverlays = clearOverlays,
        )
        pendingInteractions.clear()
        failureCounts.clear()
        ownedMiningSession = null
        failedTarget = null
        currentTick = null
        lastStartedTick = null
        lastObservedTime = null
        litematicaKeyActive = false
        externalPauseReason = null
        return cleanup
    }

    private fun releaseMiningSession(id: PrinterInteractionId) {
        if (ownedMiningSession?.interactionId == id) ownedMiningSession = null
    }

    private fun monotonicTime(): Long {
        val now = timeSource.nowMillis()
        require(now >= 0L) { "Printer time must not be negative" }
        require(lastObservedTime == null || now >= requireNotNull(lastObservedTime)) {
            "Printer time must be monotonic"
        }
        lastObservedTime = now
        return now
    }

    private fun nextId(): PrinterInteractionId {
        check(nextInteractionId < Long.MAX_VALUE) { "Printer interaction ID space exhausted" }
        return PrinterInteractionId(nextInteractionId++)
    }

    private fun rejected(reason: PrinterInteractionRejection): PrinterInteractionStart<T> {
        return PrinterInteractionStart.Rejected(reason)
    }

    private fun unmatchedConfirmation() = PrinterConfirmation<T>(
        matched = false,
        interaction = null,
        failureCount = 0,
        paused = phase == PrinterRuntimePhase.PAUSED,
    )
}
