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

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VClipPacketEmitterTest {

    private val origin = VClipPosition(2.0, 64.0, -3.0)
    private val checkpoint = VClipPosition(2.0, 61.5, -3.0)
    private val target = VClipPosition(2.0, 59.0, -3.0)

    @Test
    fun `player emission preserves packet shapes order and state`() {
        val plan = listOf(
            VClipPlayerPacketStep(VClipPlayerPacketShape.STATUS_ONLY, null, onGround = false),
            VClipPlayerPacketStep(VClipPlayerPacketShape.POSITION, checkpoint, onGround = true),
            VClipPlayerPacketStep(VClipPlayerPacketShape.FULL, target, onGround = false),
        )
        val sent = mutableListOf<ServerboundMovePlayerPacket>()

        val completed = VClipPacketEmitter.sendPlayerPlan(
            plan = plan,
            yRot = 72.5f,
            xRot = -18.25f,
            horizontalCollision = true,
            sendPacket = sent::add,
        )

        assertTrue(completed)
        assertEquals(3, sent.size)
        assertInstanceOf(ServerboundMovePlayerPacket.StatusOnly::class.java, sent[0])
        assertInstanceOf(ServerboundMovePlayerPacket.Pos::class.java, sent[1])
        assertInstanceOf(ServerboundMovePlayerPacket.PosRot::class.java, sent[2])
        assertEquals(listOf(false, true, false), sent.map { it.isOnGround })
        assertEquals(listOf(true, true, true), sent.map { it.horizontalCollision() })
        assertFalse(sent[0].hasPosition())
        assertFalse(sent[0].hasRotation())
        assertPosition(checkpoint, sent[1])
        assertFalse(sent[1].hasRotation())
        assertPosition(target, sent[2])
        assertEquals(72.5f, sent[2].getYRot(0.0f))
        assertEquals(-18.25f, sent[2].getXRot(0.0f))
    }

    @Test
    fun `player emission stops when an owned packet is not delivered`() {
        val plan = listOf(
            VClipPlayerPacketStep(VClipPlayerPacketShape.POSITION, checkpoint, onGround = true),
            VClipPlayerPacketStep(VClipPlayerPacketShape.POSITION, target, onGround = true),
            VClipPlayerPacketStep(VClipPlayerPacketShape.POSITION, origin, onGround = true),
        )
        val sent = mutableListOf<ServerboundMovePlayerPacket>()

        val completed = VClipPacketEmitter.sendPlayerPlan(
            plan = plan,
            yRot = 0.0f,
            xRot = 0.0f,
            horizontalCollision = false,
        ) { packet ->
            sent += packet
            sent.size < 2
        }

        assertFalse(completed)
        assertEquals(2, sent.size)
        assertPosition(target, sent.last())
    }

    @Test
    fun `vehicle emission substitutes origin for status fillers and preserves the planned sequence`() {
        val plan = listOf(
            VClipPlayerPacketStep(VClipPlayerPacketShape.STATUS_ONLY, null, onGround = false),
            VClipPlayerPacketStep(VClipPlayerPacketShape.POSITION, checkpoint, onGround = true),
            VClipPlayerPacketStep(VClipPlayerPacketShape.FULL, target, onGround = false),
        )
        val sent = mutableListOf<ServerboundMoveVehiclePacket>()

        VClipPacketEmitter.sendVehiclePlan(
            plan = plan,
            origin = origin,
            yRot = 135.5f,
            xRot = 12.25f,
            sendPacket = sent::add,
        )

        assertEquals(listOf(origin, checkpoint, target), sent.map { it.position().toVClipPosition() })
        assertEquals(listOf(false, true, false), sent.map { it.onGround() })
        assertEquals(List(3) { 135.5f }, sent.map { it.yRot() })
        assertEquals(List(3) { 12.25f }, sent.map { it.xRot() })
    }

    @Test
    fun `vehicle emission rejects a positional step without a position`() {
        val malformedPlan = listOf(
            VClipPlayerPacketStep(VClipPlayerPacketShape.POSITION, null, onGround = true),
        )

        assertThrows(IllegalArgumentException::class.java) {
            VClipPacketEmitter.sendVehiclePlan(
                plan = malformedPlan,
                origin = origin,
                yRot = 0.0f,
                xRot = 0.0f,
                sendPacket = {},
            )
        }
    }

    private fun assertPosition(expected: VClipPosition, packet: ServerboundMovePlayerPacket) {
        assertTrue(packet.hasPosition())
        assertEquals(expected.x, packet.getX(0.0))
        assertEquals(expected.y, packet.getY(0.0))
        assertEquals(expected.z, packet.getZ(0.0))
    }

    private fun net.minecraft.world.phys.Vec3.toVClipPosition() = VClipPosition(x, y, z)
}
