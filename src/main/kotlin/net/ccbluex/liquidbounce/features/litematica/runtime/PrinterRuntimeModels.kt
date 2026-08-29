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

fun interface PrinterTimeSource {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = PrinterTimeSource { System.nanoTime() / NANOS_PER_MILLISECOND }

        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

enum class PrinterActivationMode {
    LITEMATICA_KEY,
    CONTINUOUS,
}

enum class PrinterRuntimePhase {
    DISABLED,
    IDLE,
    READY,
    PAUSED,
}

enum class PrinterPauseReason {
    PRINTER_TOGGLE_DISABLED,
    LITEMATICA_KEY_IDLE,
    HIGHER_PRIORITY_ROTATION,
    PACKET_MINE_ACTIVE,
    SCAFFOLD_ACTIVE,
    AUTO_BUILD_ACTIVE,
    FUCKER_ACTIVE,
    BLINK_ACTIVE,
    SILENT_HOTBAR_BUSY,
    CONTAINER_OPEN,
    USING_ITEM,
    INTEGRATION_MISSING,
    INTEGRATION_INCOMPATIBLE,
    NO_PLACEMENT,
    NO_ACTION,
    RETRY_LIMIT_REACHED,
}

enum class PrinterInteractionKind {
    PLACE,
    BREAK,
    FLUID_PLACE,
    FLUID_PICKUP,
}

enum class PrinterInteractionOutcome {
    SUCCESS,
    FAILURE,
}

@JvmInline
value class PrinterInteractionId(val value: Long) {
    init {
        require(value > 0L) { "Printer interaction ID must be positive" }
    }
}

data class PrinterRuntimePolicy(
    val actionDelayTicks: Int = 1,
    val retryLimit: Int = 10,
    val confirmationTimeoutMillis: Long = 2_000L,
) {
    init {
        require(actionDelayTicks >= 1) { "Printer action delay must be at least one tick" }
        require(retryLimit >= 1) { "Printer retry limit must be positive" }
        require(confirmationTimeoutMillis > 0L) { "Printer confirmation timeout must be positive" }
    }
}

data class PrinterTickInput(
    val tick: Long,
    val litematicaKeyActive: Boolean,
    val externalPauseReason: PrinterPauseReason? = null,
    val policy: PrinterRuntimePolicy = PrinterRuntimePolicy(),
) {
    init {
        require(tick >= 0L) { "Printer tick must not be negative" }
        require(externalPauseReason != PrinterPauseReason.RETRY_LIMIT_REACHED) {
            "Retry limit pause is owned by the printer runtime"
        }
    }
}

data class PendingPrinterInteraction<T : Any>(
    val id: PrinterInteractionId,
    val target: T,
    val kind: PrinterInteractionKind,
    val startedAtMillis: Long,
)

data class PrinterMiningSession<T : Any>(
    val interactionId: PrinterInteractionId,
    val target: T,
)

sealed interface PrinterSyncCommand {
    data class SetPrinterToggle(val enabled: Boolean) : PrinterSyncCommand
    data class SetEasyPlace(val enabled: Boolean) : PrinterSyncCommand
}

enum class PrinterInteractionRejection {
    TICK_NOT_STARTED,
    RUNTIME_NOT_READY,
    ALREADY_STARTED_THIS_TICK,
    ACTION_DELAY,
    TARGET_PENDING,
    MINING_IN_PROGRESS,
}

sealed interface PrinterInteractionStart<out T : Any> {
    data class Accepted<T : Any>(
        val interaction: PendingPrinterInteraction<T>,
    ) : PrinterInteractionStart<T>

    data class Rejected(
        val reason: PrinterInteractionRejection,
    ) : PrinterInteractionStart<Nothing>
}

data class PrinterTickResult<T : Any>(
    val timedOutInteractions: List<PendingPrinterInteraction<T>>,
    val miningSessionToContinue: PrinterMiningSession<T>?,
)

data class PrinterConfirmation<T : Any>(
    val matched: Boolean,
    val interaction: PendingPrinterInteraction<T>?,
    val failureCount: Int,
    val paused: Boolean,
)

data class PrinterCleanup<T : Any>(
    val cancelledInteractions: List<PendingPrinterInteraction<T>>,
    val miningSessionToStop: PrinterMiningSession<T>?,
    val removePositionProvider: Boolean,
    val clearOverlays: Boolean,
)

data class PrinterEnableResult<T : Any>(
    val syncCommands: List<PrinterSyncCommand>,
    val cleanup: PrinterCleanup<T>,
)

data class PrinterRuntimeSnapshot<T : Any>(
    val phase: PrinterRuntimePhase,
    val pauseReason: PrinterPauseReason?,
    val activationMode: PrinterActivationMode,
    val printerEnabled: Boolean,
    val easyPlaceEnabled: Boolean,
    val policy: PrinterRuntimePolicy,
    val pendingInteractions: List<PendingPrinterInteraction<T>>,
    val ownedMiningSession: PrinterMiningSession<T>?,
    val failureCounts: Map<T, Int>,
    val failedTarget: T?,
) {
    val pendingTargets: Set<T>
        get() = pendingInteractions.mapTo(linkedSetOf()) { it.target }
}
