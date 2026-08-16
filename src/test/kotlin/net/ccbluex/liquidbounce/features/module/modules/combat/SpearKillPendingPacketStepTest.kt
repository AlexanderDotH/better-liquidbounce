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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillPendingPacketStepTest {

    @Test
    fun `live blocked validation survives cancelling its movement packet`() {
        assertEquals(
            SpearKillPendingPacketStepValidation.BLOCKED,
            resolveSpearKillPendingPacketStepRejection(
                packetAlreadyCancelled = false,
                validation = SpearKillPendingPacketStepValidation.BLOCKED,
            ),
        )
    }

    @Test
    fun `external cancellation retries only a clear pending step`() {
        assertEquals(
            SpearKillPendingPacketStepValidation.CLEAR,
            resolveSpearKillPendingPacketStepRejection(
                packetAlreadyCancelled = true,
                validation = SpearKillPendingPacketStepValidation.CLEAR,
            ),
        )
        assertNull(
            resolveSpearKillPendingPacketStepRejection(
                packetAlreadyCancelled = false,
                validation = SpearKillPendingPacketStepValidation.CLEAR,
            ),
        )
    }

    @Test
    fun `reduced network budget reaches the route replan branch`() {
        assertEquals(
            SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED,
            resolveSpearKillPendingPacketStepRejection(
                packetAlreadyCancelled = false,
                validation = SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED,
            ),
        )
    }

    @Test
    fun `Blink queued and cancelled fall safety packets never confirm delivery`() {
        assertFalse(spearKillPacketDeliveryConfirmed(packetCancelled = false, queuedByBlink = true))
        assertFalse(spearKillPacketDeliveryConfirmed(packetCancelled = true, queuedByBlink = false))
        assertTrue(spearKillPacketDeliveryConfirmed(packetCancelled = false, queuedByBlink = false))
    }
}
