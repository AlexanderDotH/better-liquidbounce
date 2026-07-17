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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.intave

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.BlockShapeEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.PlayerPushOutEvent
import net.ccbluex.liquidbounce.event.events.PlayerStepEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.exploit.phase.modes.IntaveBlockPlacementSupport
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.minecraft.world.entity.MoverType
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.Shapes

/**
 * Revive-style Intave in-block speed.
 *
 * Place a block inside your own hitbox, sink slightly, then strafe inside the block.
 */
class SpeedIntaveInBlock(override val parent: ModeValueGroup<*>) : Mode("IntaveInBlock") {

    private val speed by float("Speed", 0.33f, 0.1f..5f)
    private val sinkDistance by float("SinkDistance", 0.07f, 0f..0.5f, "blocks")

    private var startY = 0
    private var sunk = false

    init {
        instances += this
    }

    override fun enable() {
        startY = player.blockPosition().y
        sunk = false
        super.enable()
    }

    override fun disable() {
        sunk = false
        super.disable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!IntaveBlockPlacementSupport.isInsidePhaseBlock()) {
            sunk = false
            return@tickHandler
        }

        if (!sunk && sinkDistance > 0f) {
            player.setPos(player.x, player.y - sinkDistance.toDouble(), player.z)
            sunk = true
        }
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent> { event ->
        if (event.type != MoverType.SELF || !player.moving || !IntaveBlockPlacementSupport.isInsidePhaseBlock()) {
            return@handler
        }

        event.movement = event.movement.withStrafe(speed = speed.toDouble())
    }

    @Suppress("unused")
    private val shapeHandler = handler<BlockShapeEvent> { event ->
        if (event.pos.y < startY - 1 ||
            !IntaveBlockPlacementSupport.shouldClearPhaseCollision(running, event.pos)) {
            return@handler
        }

        event.shape = Shapes.empty()
    }

    @Suppress("unused")
    private val stepHandler = handler<PlayerStepEvent> { event ->
        if (IntaveBlockPlacementSupport.isInsidePhaseBlock()) {
            event.height = 0f
        }
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent> { event ->
        if (IntaveBlockPlacementSupport.isInsidePhaseBlock()) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val pushOutHandler = handler<PlayerPushOutEvent> { event ->
        if (IntaveBlockPlacementSupport.isInsidePhaseBlock()) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val placementPacketHandler = handler<PacketEvent> { event ->
        if (event.origin == TransferOrigin.INCOMING &&
            IntaveBlockPlacementSupport.shouldCancelIncomingSetback(running, event.packet)) {
            event.cancelEvent()
            return@handler
        }

        if (event.origin == TransferOrigin.OUTGOING && event.packet is ServerboundUseItemOnPacket) {
            if (IntaveBlockPlacementSupport.spoofPlacementIfNeeded(running, event.packet)) {
                event.cancelEvent()
            }
        }
    }

    companion object {
        private val instances = hashSetOf<SpeedIntaveInBlock>()

        @JvmStatic
        fun canPlaceThroughPlayer(context: BlockPlaceContext, state: BlockState): Boolean {
            return IntaveBlockPlacementSupport.canPlaceThroughPlayer(instances.any { it.running }, context, state)
        }

        @JvmStatic
        fun shouldSuppressWallOverlay(): Boolean {
            return IntaveBlockPlacementSupport.shouldSuppressWallOverlay(instances.any { it.running })
        }
    }

}
