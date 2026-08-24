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
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VClipPacketPlannerTest {

    private val origin = VClipPosition(2.0, 64.0, -3.0)

    @Test
    fun `Folia five packet descent uses two primers and three grounded checkpoints`() {
        val target = origin.copy(y = 57.63)

        val result = VClipPacketPlanner.folia(
            origin = origin,
            target = target,
            movementPackets = 5,
            fullPacket = false,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as VClipPacketPlanResult.GroundedSegmentation

        assertEquals(5, result.steps.size)
        assertEquals(
            List(2) { VClipPlayerPacketShape.STATUS_ONLY },
            result.steps.take(2).map(VClipPlayerPacketStep::shape),
        )
        assertEquals(
            listOf(origin.copy(y = 61.25), origin.copy(y = 58.5), target),
            result.steps.drop(2).map(VClipPlayerPacketStep::position),
        )
        assertEquals(List(5) { true }, result.steps.map(VClipPlayerPacketStep::onGround))
    }

    @Test
    fun `Vanilla uses full grounded checkpoints and preserves the exact target`() {
        val target = origin.copy(y = 57.63)

        val result = VClipPacketPlanner.vanilla(
            origin = origin,
            target = target,
            paperBypass = false,
            fullPacket = true,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as VClipPacketPlanResult.GroundedSegmentation

        assertEquals(3, result.steps.size)
        assertEquals(
            List(3) { VClipPlayerPacketShape.FULL },
            result.steps.map(VClipPlayerPacketStep::shape),
        )
        assertEquals(List(3) { true }, result.steps.map(VClipPlayerPacketStep::onGround))
        assertEquals(target, result.steps.last().position)
    }

    @Test
    fun `Vanilla keeps Paper stationary primers before safe checkpoints`() {
        val target = origin.copy(y = 39.0)

        val result = VClipPacketPlanner.vanilla(
            origin = origin,
            target = target,
            paperBypass = true,
            fullPacket = false,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as VClipPacketPlanResult.GroundedSegmentation

        assertEquals(11, result.steps.size)
        assertEquals(origin, result.steps.first().position)
        assertEquals(origin.copy(y = 61.25), result.steps[1].position)
        assertEquals(target, result.steps.last().position)
        assertTrue(result.steps.all(VClipPlayerPacketStep::onGround))
    }

    @Test
    fun `Folia long descent uses exact packet budget for ungrounded PacketJump fallback`() {
        val target = origin.copy(y = 40.0)

        val result = VClipPacketPlanner.folia(
            origin = origin,
            target = target,
            movementPackets = 5,
            fullPacket = true,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as VClipPacketPlanResult.PacketJumpFallback

        assertEquals(5, result.steps.size)
        assertEquals(
            List(3) { VClipPlayerPacketShape.STATUS_ONLY },
            result.steps.take(3).map(VClipPlayerPacketStep::shape),
        )
        assertTrue(result.steps.take(3).all { it.position == null })
        assertEquals(target, result.steps[3].position)
        assertEquals(VClipPlayerPacketShape.FULL, result.steps[3].shape)
        assertEquals(target.y + 1.0E-9, result.steps[4].position?.y)
        assertEquals(target.x, result.steps[4].position?.x)
        assertEquals(target.z, result.steps[4].position?.z)
        assertEquals(VClipPlayerPacketShape.FULL, result.steps[4].shape)
        assertTrue(result.steps.none(VClipPlayerPacketStep::onGround))
    }

    @Test
    fun `Folia one packet rejects a descent that needs PacketJump fallback`() {
        val result = VClipPacketPlanner.folia(
            origin = origin,
            target = origin.copy(y = 58.0),
            movementPackets = 1,
            fullPacket = false,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        )

        assertSame(VClipPacketPlanResult.Unavailable, result)
    }

    @Test
    fun `Folia upward clip preserves four status primers before the target`() {
        val target = origin.copy(y = 72.0)

        val result = VClipPacketPlanner.folia(
            origin = origin,
            target = target,
            movementPackets = 5,
            fullPacket = true,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ) as VClipPacketPlanResult.GroundedSegmentation

        assertEquals(5, result.steps.size)
        assertEquals(List(4) { VClipPlayerPacketShape.STATUS_ONLY }, result.steps.dropLast(1).map { it.shape })
        assertEquals(List(4) { null }, result.steps.dropLast(1).map { it.position })
        assertEquals(VClipPlayerPacketShape.FULL, result.steps.last().shape)
        assertEquals(target, result.steps.last().position)
        assertEquals(List(5) { true }, result.steps.map { it.onGround })
    }

    @Test
    fun `Folia movement packet setting is bounded to the researched five packet window`() {
        assertThrows(IllegalArgumentException::class.java) {
            VClipPacketPlanner.folia(
                origin = origin,
                target = origin.copy(y = 60.0),
                movementPackets = 6,
                fullPacket = false,
                initialFallDistance = 0.0,
                safeFallDistance = 3.0,
            )
        }
    }
}
