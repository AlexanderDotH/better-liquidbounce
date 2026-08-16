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
package net.ccbluex.liquidbounce.features.module.modules.combat

internal enum class SpearKillPendingPacketStepValidation {
    CLEAR,
    BUDGET_EXCEEDED,
    BLOCKED,
}

/** Preserves a live safety failure even though rejecting the wire packet also marks it cancelled. */
internal fun resolveSpearKillPendingPacketStepRejection(
    packetAlreadyCancelled: Boolean,
    validation: SpearKillPendingPacketStepValidation,
): SpearKillPendingPacketStepValidation? = when {
    validation != SpearKillPendingPacketStepValidation.CLEAR -> validation
    packetAlreadyCancelled -> SpearKillPendingPacketStepValidation.CLEAR
    else -> null
}

/** Blink ownership and ordinary cancellation both mean the server has not accepted the packet. */
internal fun spearKillPacketDeliveryConfirmed(
    packetCancelled: Boolean,
    queuedByBlink: Boolean,
): Boolean = !packetCancelled && !queuedByBlink
