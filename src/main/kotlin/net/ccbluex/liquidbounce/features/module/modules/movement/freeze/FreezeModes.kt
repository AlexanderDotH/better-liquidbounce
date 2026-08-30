/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.freeze

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMovementTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.events.BlinkPacketAction as Action
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleEasyPearl
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.network.UseItemPacketRotation
import net.ccbluex.liquidbounce.features.network.sendPacketSilently
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import kotlin.math.abs
import kotlin.random.Random

internal object FreezeQueue : Mode("Queue") {
    private val origins by multiEnumChoice("Origin", TransferOrigin.OUTGOING)

    @Suppress("unused")
    private val fakeLagHandler = handler<BlinkPacketEvent>(
        priority = EventPriorityConvention.SAFETY_FEATURE
    ) { event ->
        if (event.origin in origins) event.action = Action.QUEUE
    }
}

internal object FreezeCancel : Mode("Cancel") {
    private val origins by multiEnumChoice("Origin", TransferOrigin.OUTGOING)

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.origin in origins) event.cancelEvent()
    }
}

internal object FreezeStationary : Mode("Stationary") {
    private val cancelC0B by boolean("CancelC0B", true)
    private val yawOffset = FloatOffsetGenerator()
    private val pitchOffset = FloatOffsetGenerator()

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val yaw = RotationManager.currentRotation?.yaw ?: player.yRot
        val pitch = RotationManager.currentRotation?.pitch ?: player.xRot
        val yawOffset = yawOffset.nextFloat()
        val pitchOffset = pitchOffset.nextFloat()

        when (val packet = event.packet) {
            is ServerboundPongPacket -> if (cancelC0B) event.cancelEvent()
            is ServerboundUseItemPacket -> replaceUseItemPacket(event, packet, yaw, pitch, yawOffset, pitchOffset)
            is ServerboundInteractPacket,
            is ServerboundAttackPacket,
            is ServerboundSpectatorActionPacket -> replaceRotationPacket(event, packet, yaw, pitch, yawOffset, pitchOffset)
            is ServerboundUseItemOnPacket -> replaceRotationPacket(event, packet, yaw, pitch, yawOffset, pitchOffset)
        }
    }

    private fun replaceUseItemPacket(
        event: PacketEvent,
        packet: ServerboundUseItemPacket,
        yaw: Float,
        pitch: Float,
        yawOffset: Float,
        pitchOffset: Float,
    ) {
        event.cancelEvent()
        sendPacketSilently(
            ServerboundMovePlayerPacket.Rot(
                ModuleEasyPearl.currentTargetRotation?.yaw ?: (player.yRot + yawOffset),
                ModuleEasyPearl.currentTargetRotation?.pitch ?: (player.xRot + pitchOffset),
                player.onGround(),
                player.horizontalCollision,
            )
        )
        sendPacketSilently(UseItemPacketRotation.createExplicit(packet.hand, packet.sequence, yaw + yawOffset, pitch + pitchOffset))
    }

    private fun replaceRotationPacket(
        event: PacketEvent,
        packet: Any,
        yaw: Float,
        pitch: Float,
        yawOffset: Float,
        pitchOffset: Float,
    ) {
        event.cancelEvent()
        sendPacketSilently(
            ServerboundMovePlayerPacket.Rot(
                yaw + yawOffset,
                pitch + pitchOffset,
                player.onGround(),
                player.horizontalCollision,
            )
        )
        when (packet) {
            is ServerboundInteractPacket -> sendPacketSilently(packet)
            is ServerboundAttackPacket -> sendPacketSilently(packet)
            is ServerboundSpectatorActionPacket -> sendPacketSilently(packet)
            is ServerboundUseItemOnPacket -> sendPacketSilently(packet)
        }
    }
}

internal object FreezeTickMovement : Mode("TickMovement") {
    private val interval by intRange("Interval", 20..20, 1..200, "ticks")
    private var ticksUntilMovement = 0

    override fun enable() {
        ticksUntilMovement = interval.random()
    }

    override fun disable() {
        ticksUntilMovement = 0
    }

    @Suppress("unused")
    private val movementTickHandler = handler<PlayerMovementTickEvent> { event ->
        if (--ticksUntilMovement <= 0) {
            ticksUntilMovement = interval.random()
            return@handler
        }
        event.cancelEvent()
    }
}

private class FloatOffsetGenerator : FloatIterator() {
    private var previous = 0f

    override fun hasNext() = true

    override fun nextFloat(): Float {
        var offset: Float
        do {
            offset = Random.nextDouble(0.002, 0.01).toFloat()
        } while (abs(offset - previous) < 1.0E-6F)
        return offset.also { previous = it }
    }
}
