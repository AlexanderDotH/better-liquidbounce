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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.intave

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.BlockShapeEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerPushOutEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.exploit.phase.modes.IntaveBlockPlacementSupport
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.flyAutomationSneak
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Revive Intave fly port.
 *
 * Place a block into the player's hitbox, then fly inside the loosened collision.
 */
internal object FlyIntave : Mode("Intave"), FlyAutomationProfile {

    private val upwardSneakMotion by float("UpwardSneakMotion", 0.01f, 0f..0.2f)
    private val collisionDrop by float("CollisionDrop", 1f, 0f..1f, "blocks")


    private var startY = 0

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = false,
        landing = false,
        kind = FlyAutomationKind.CONTINUOUS,
        resource = "Placeable Block",
    )

    override fun automationReadiness(): FlyAutomationReadiness =
        if (IntaveBlockPlacementSupport.isInsidePhaseBlock()) {
            FlyAutomationReadiness.Ready
        } else {
            FlyAutomationReadiness.Arming("Waiting for a block inside the player hitbox")
        }

    override fun enable() {
        startY = player.blockPosition().y
        super.enable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        player.deltaMovement.y = if (flyAutomationSneak(mc.options.keyShift.isDown)) {
            upwardSneakMotion.toDouble()
        } else {
            0.0
        }

        if (!player.abilities.mayfly) {
            player.abilities.flying = false
        }
    }

    @Suppress("unused")
    private val shapeHandler = handler<BlockShapeEvent> { event ->
        if (!IntaveBlockPlacementSupport.isInsidePhaseBlock() || event.pos.y <= startY - 1) {
            return@handler
        }

        event.shape = lowerShape(event.shape)
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

    private fun lowerShape(shape: VoxelShape): VoxelShape {
        if (collisionDrop <= 0f || shape.isEmpty) {
            return shape
        }

        var loweredShape = Shapes.empty()
        shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
            val loweredBox = Shapes.create(minX, minY - collisionDrop.toDouble(), minZ, maxX, maxY, maxZ)
            loweredShape = Shapes.joinUnoptimized(loweredShape, loweredBox, BooleanOp.OR)
        }

        return loweredShape.optimize()
    }

    @JvmStatic
    fun canPlaceThroughPlayer(context: BlockPlaceContext, state: BlockState): Boolean {
        return IntaveBlockPlacementSupport.canPlaceThroughPlayer(running, context, state)
    }

    @JvmStatic
    fun shouldSuppressWallOverlay(): Boolean {
        return IntaveBlockPlacementSupport.shouldSuppressWallOverlay(running)
    }

}
