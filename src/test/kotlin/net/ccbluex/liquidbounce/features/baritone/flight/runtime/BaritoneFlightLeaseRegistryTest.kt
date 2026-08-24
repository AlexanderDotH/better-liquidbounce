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
package net.ccbluex.liquidbounce.features.baritone.flight.runtime

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFlyOwnership
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaritoneFlightLeaseRegistryTest {

    @AfterEach
    fun clearRegistry() {
        BaritoneFlightLeaseRegistry.clear()
    }

    @Test
    fun `Fly conflict exemption follows the active matching lease validation`() {
        var valid = true
        val lease = BaritoneFlyLease(1, "Vanilla", BaritoneFlyOwnership.BARITONE)

        BaritoneFlightLeaseRegistry.publish(lease) { valid }
        assertTrue(BaritoneFlightLeaseRegistry.exemptsFlyConflict())

        valid = false
        assertFalse(BaritoneFlightLeaseRegistry.exemptsFlyConflict())
    }

    @Test
    fun `clearing a stale lease cannot remove the current exemption`() {
        val stale = BaritoneFlyLease(1, "Vanilla", BaritoneFlyOwnership.BARITONE)
        val current = BaritoneFlyLease(2, "Packet", BaritoneFlyOwnership.BARITONE)

        BaritoneFlightLeaseRegistry.publish(current) { true }
        BaritoneFlightLeaseRegistry.clear(stale)

        assertTrue(BaritoneFlightLeaseRegistry.exemptsFlyConflict())
    }
}
