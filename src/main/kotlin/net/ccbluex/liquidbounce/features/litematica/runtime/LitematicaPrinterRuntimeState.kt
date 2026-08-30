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

internal fun <T : Any> LitematicaPrinterRuntime<T>.expireInteractions(
    now: Long,
): List<PendingPrinterInteraction<T>> {
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

internal fun <T : Any> LitematicaPrinterRuntime<T>.registerFailure(target: T): Int {
    val failures = failureCounts.getOrDefault(target, 0) + 1
    failureCounts[target] = failures
    if (failures >= policy.retryLimit && failedTarget == null) failedTarget = target
    return failures
}

internal fun <T : Any> LitematicaPrinterRuntime<T>.lockTargetExceedingRetryLimit() {
    if (failedTarget != null) return
    failedTarget = failureCounts.entries.firstOrNull { it.value >= policy.retryLimit }?.key
}

internal fun <T : Any> LitematicaPrinterRuntime<T>.refreshPhase() {
    when {
        !moduleEnabled -> setPhase(PrinterRuntimePhase.DISABLED, null)
        failedTarget != null -> setPhase(PrinterRuntimePhase.PAUSED, PrinterPauseReason.RETRY_LIMIT_REACHED)
        !synchronizer.printerEnabled -> setPhase(
            PrinterRuntimePhase.IDLE, PrinterPauseReason.PRINTER_TOGGLE_DISABLED,
        )
        activationMode == PrinterActivationMode.LITEMATICA_KEY && !litematicaKeyActive -> setPhase(
            PrinterRuntimePhase.IDLE, PrinterPauseReason.LITEMATICA_KEY_IDLE,
        )
        externalPauseReason != null -> setPhase(PrinterRuntimePhase.PAUSED, externalPauseReason)
        currentTick == null -> setPhase(PrinterRuntimePhase.IDLE, null)
        else -> setPhase(PrinterRuntimePhase.READY, null)
    }
}

private fun <T : Any> LitematicaPrinterRuntime<T>.setPhase(
    newPhase: PrinterRuntimePhase,
    reason: PrinterPauseReason?,
) {
    phase = newPhase
    pauseReason = reason
}

internal fun <T : Any> LitematicaPrinterRuntime<T>.clearOwnedState(
    removePositionProvider: Boolean,
    clearOverlays: Boolean,
): PrinterCleanup<T> {
    val cleanup = PrinterCleanup(
        pendingInteractions.values.toList(), ownedMiningSession, removePositionProvider, clearOverlays,
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

internal fun <T : Any> LitematicaPrinterRuntime<T>.releaseMiningSession(id: PrinterInteractionId) {
    if (ownedMiningSession?.interactionId == id) ownedMiningSession = null
}

internal fun <T : Any> LitematicaPrinterRuntime<T>.monotonicTime(): Long {
    val now = timeSource.nowMillis()
    require(now >= 0L) { "Printer time must not be negative" }
    require(lastObservedTime == null || now >= requireNotNull(lastObservedTime)) {
        "Printer time must be monotonic"
    }
    lastObservedTime = now
    return now
}

internal fun <T : Any> LitematicaPrinterRuntime<T>.nextId(): PrinterInteractionId {
    check(nextInteractionId < Long.MAX_VALUE) { "Printer interaction ID space exhausted" }
    return PrinterInteractionId(nextInteractionId++)
}

internal fun <T : Any> LitematicaPrinterRuntime<T>.rejected(
    reason: PrinterInteractionRejection,
): PrinterInteractionStart<T> = PrinterInteractionStart.Rejected(reason)

internal fun <T : Any> LitematicaPrinterRuntime<T>.unmatchedConfirmation() = PrinterConfirmation<T>(
    matched = false,
    interaction = null,
    failureCount = 0,
    paused = phase == PrinterRuntimePhase.PAUSED,
)
