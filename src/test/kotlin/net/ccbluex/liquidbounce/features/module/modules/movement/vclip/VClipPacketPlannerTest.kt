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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VClipPacketPlannerTest {

    private val origin = VClipPosition(2.0, 64.0, -3.0)

    @Test
    fun `vanilla without Paper bypass leaves movement local`() {
        val target = origin.copy(y = 69.0)

        assertEquals(
            emptyList<VClipPlayerPacketStep>(),
            VClipPacketPlanner.vanilla(origin, target, paperBypass = false, fullPacket = false, onGround = true),
        )
    }

    @Test
    fun `Vanilla NoFall sends one grounded target packet without Paper bypass`() {
        val target = origin.copy(y = 54.0)

        val plan = VClipPacketPlanner.vanilla(
            origin = origin,
            target = target,
            paperBypass = false,
            forceTargetPacket = true,
            fullPacket = false,
            onGround = true,
        )

        assertEquals(
            listOf(VClipPlayerPacketStep(VClipPlayerPacketShape.POSITION, target, onGround = true)),
            plan,
        )
    }

    @Test
    fun `vanilla Paper bypass preserves the command stationary packet calculation`() {
        val target = origin.copy(y = 89.0)

        val plan = VClipPacketPlanner.vanilla(
            origin,
            target,
            paperBypass = true,
            fullPacket = false,
            onGround = false,
        )

        assertEquals(2, plan.size)
        assertEquals(origin, plan.first().position)
        assertEquals(target, plan.last().position)
        assertEquals(List(2) { VClipPlayerPacketShape.POSITION }, plan.map { it.shape })
        assertEquals(List(2) { false }, plan.map { it.onGround })
    }

    @Test
    fun `Folia primes four status packets before the fifth movement packet`() {
        val target = origin.copy(y = 72.0)

        val plan = VClipPacketPlanner.folia(
            target,
            movementPackets = 5,
            fullPacket = true,
            onGround = true,
        )

        assertEquals(5, plan.size)
        assertEquals(List(4) { VClipPlayerPacketShape.STATUS_ONLY }, plan.dropLast(1).map { it.shape })
        assertEquals(List(4) { null }, plan.dropLast(1).map { it.position })
        assertEquals(VClipPlayerPacketShape.FULL, plan.last().shape)
        assertEquals(target, plan.last().position)
        assertEquals(List(5) { true }, plan.map { it.onGround })
    }

    @Test
    fun `Folia movement packet setting is bounded to the researched five packet window`() {
        assertThrows(IllegalArgumentException::class.java) {
            VClipPacketPlanner.folia(origin, movementPackets = 6, fullPacket = false, onGround = false)
        }
    }
}
