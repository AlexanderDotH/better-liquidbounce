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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillMovementAssistTest {

    @Test
    fun `TargetSpeed remains independent from StepDistance with and without Elytra`() {
        for (elytraActive in listOf(false, true)) {
            val transport = resolveSpearKillMovementTransport(
                configuredSpeed = 5.0,
                configuredStepLimit = 17.32,
                elytraActive = elytraActive,
            )

            assertEquals(5.0, transport.maxSpeed, 1e-9)
            assertEquals(17.32, transport.stepLimit, 1e-9)
            assertEquals(elytraActive, transport.elytraActive)
        }
    }

    @Test
    fun `configured speed is independent from Elytra assistance`() {
        val normal = resolveSpearKillMovementTransport(17.32, 17.32, elytraActive = false)
        val elytra = resolveSpearKillMovementTransport(17.32, 17.32, elytraActive = true)

        assertEquals(17.32, normal.maxSpeed, 1e-9)
        assertEquals(17.32, normal.stepLimit, 1e-9)
        assertEquals(17.32, elytra.maxSpeed, 1e-9)
        assertEquals(17.32, elytra.stepLimit, 1e-9)
    }

    @Test
    fun `configured TargetSpeed supports five hundred blocks per tick`() {
        for (elytraActive in listOf(false, true)) {
            val transport = resolveSpearKillMovementTransport(500.0, 500.0, elytraActive)

            assertEquals(500.0, transport.maxSpeed, 1e-9)
            assertEquals(500.0, transport.stepLimit, 1e-9)
            assertEquals(elytraActive, transport.elytraActive)
        }
    }

    @Test
    fun `StepDistance independently limits experimental route chunks`() {
        val transport = resolveSpearKillMovementTransport(
            configuredSpeed = 500.0,
            configuredStepLimit = 6.0,
            elytraActive = false,
        )

        assertEquals(500.0, transport.maxSpeed, 1e-9)
        assertEquals(6.0, transport.stepLimit, 1e-9)
    }

    @Test
    fun `Input assist ORs automated input with physical input`() {
        val lease = resolveSpearKillMovementAssistLease(
            preparationActive = false,
            routeActive = true,
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
                preparationActive = false,
                routeActive = true,
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
            preparationActive = true,
            routeActive = false,
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
            preparationActive = false,
            routeActive = false,
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
            preparationActive = true,
            routeActive = false,
            sneakMode = SpearKillMovementAssistMode.NONE,
            elytraMode = SpearKillMovementAssistMode.PACKET,
            elytraUsable = true,
            elytraActive = false,
        )
        val input = resolveSpearKillMovementAssistLease(
            preparationActive = true,
            routeActive = false,
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

    @Test
    fun `Packet sneak starts with route movement and not while merely charging a target`() {
        val preparation = resolveSpearKillMovementAssistLease(
            preparationActive = true,
            routeActive = false,
            sneakMode = SpearKillMovementAssistMode.PACKET,
            elytraMode = SpearKillMovementAssistMode.NONE,
            elytraUsable = false,
            elytraActive = false,
        )
        val route = resolveSpearKillMovementAssistLease(
            preparationActive = false,
            routeActive = true,
            sneakMode = SpearKillMovementAssistMode.PACKET,
            elytraMode = SpearKillMovementAssistMode.NONE,
            elytraUsable = false,
            elytraActive = false,
        )

        assertFalse(preparation.serverSneak)
        assertTrue(route.serverSneak)
    }
}
