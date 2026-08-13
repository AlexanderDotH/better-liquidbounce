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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillMovementAssistTest {

    @Test
    fun `configured speed five remains five with and without Elytra`() {
        for (elytraActive in listOf(false, true)) {
            val transport = resolveSpearKillMovementTransport(
                configuredSpeed = 5.0,
                configuredStepLimit = 17.32,
                elytraActive = elytraActive,
            )

            assertEquals(5.0, transport.maxSpeed, 1e-9)
            assertEquals(5.0, transport.stepLimit, 1e-9)
            assertEquals(elytraActive, transport.elytraActive)
        }
    }

    @Test
    fun `configured speed seventeen point three two requires active Elytra`() {
        val normal = resolveSpearKillMovementTransport(17.32, 17.32, elytraActive = false)
        val elytra = resolveSpearKillMovementTransport(17.32, 17.32, elytraActive = true)

        assertEquals(10.0, normal.maxSpeed, 1e-9)
        assertEquals(10.0, normal.stepLimit, 1e-9)
        assertEquals(17.32, elytra.maxSpeed, 1e-9)
        assertEquals(17.32, elytra.stepLimit, 1e-9)
    }

    @Test
    fun `steps per teleport independently limits route chunks`() {
        val transport = resolveSpearKillMovementTransport(
            configuredSpeed = 17.32,
            configuredStepLimit = 6.0,
            elytraActive = true,
        )

        assertEquals(17.32, transport.maxSpeed, 1e-9)
        assertEquals(6.0, transport.stepLimit, 1e-9)
    }

    @Test
    fun `Input assist ORs automated input with physical input`() {
        val lease = resolveSpearKillMovementAssistLease(
            active = true,
            sneakMode = SpearKillMovementAssistMode.INPUT,
            elytraMode = SpearKillMovementAssistMode.NONE,
            elytraUsable = false,
            elytraActive = false,
        )

        assertFalse(lease.injectJump)
        assertTrue(lease.injectSneak)
        assertEquals(
            SpearKillMovementInput(jump = true, sneak = true),
            applySpearKillMovementInputLease(
                physical = SpearKillMovementInput(jump = true, sneak = false),
                lease = lease,
            ),
        )
    }

    @Test
    fun `active Elytra suppresses Packet and Input sneak automation`() {
        for (sneakMode in listOf(SpearKillMovementAssistMode.PACKET, SpearKillMovementAssistMode.INPUT)) {
            val lease = resolveSpearKillMovementAssistLease(
                active = true,
                sneakMode = sneakMode,
                elytraMode = SpearKillMovementAssistMode.INPUT,
                elytraUsable = true,
                elytraActive = true,
            )

            assertFalse(lease.injectSneak)
            assertFalse(lease.serverSneak)
        }
    }

    @Test
    fun `usable selected Elytra owns preparation before fall flight becomes active`() {
        val lease = resolveSpearKillMovementAssistLease(
            active = true,
            sneakMode = SpearKillMovementAssistMode.INPUT,
            elytraMode = SpearKillMovementAssistMode.INPUT,
            elytraUsable = true,
            elytraActive = false,
        )

        assertTrue(lease.injectJump)
        assertFalse(lease.injectSneak)
        assertFalse(lease.serverSneak)
    }

    @Test
    fun `inactive lease releases every injected assist without clearing physical input`() {
        val lease = resolveSpearKillMovementAssistLease(
            active = false,
            sneakMode = SpearKillMovementAssistMode.INPUT,
            elytraMode = SpearKillMovementAssistMode.INPUT,
            elytraUsable = true,
            elytraActive = true,
        )

        assertFalse(lease.injectJump)
        assertFalse(lease.injectSneak)
        assertFalse(lease.serverSneak)
        assertFalse(lease.requestPacketFallFlying)
        assertEquals(
            SpearKillMovementInput(jump = true, sneak = true),
            applySpearKillMovementInputLease(
                physical = SpearKillMovementInput(jump = true, sneak = true),
                lease = lease,
            ),
        )
    }

    @Test
    fun `Packet Elytra requests flight while Input Elytra only injects jump`() {
        val packet = resolveSpearKillMovementAssistLease(
            active = true,
            sneakMode = SpearKillMovementAssistMode.NONE,
            elytraMode = SpearKillMovementAssistMode.PACKET,
            elytraUsable = true,
            elytraActive = false,
        )
        val input = resolveSpearKillMovementAssistLease(
            active = true,
            sneakMode = SpearKillMovementAssistMode.NONE,
            elytraMode = SpearKillMovementAssistMode.INPUT,
            elytraUsable = true,
            elytraActive = false,
        )

        assertTrue(packet.requestPacketFallFlying)
        assertFalse(packet.injectJump)
        assertFalse(input.requestPacketFallFlying)
        assertTrue(input.injectJump)
    }
}
