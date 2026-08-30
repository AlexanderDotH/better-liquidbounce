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


@file:JvmName("NetworkUtilsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.network

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.shouldSwingHand
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType

/**
 * [MultiPlayerGameMode.useItem] but with custom rotations.
 *
 * Vanilla [net.minecraft.world.item.BucketItem.use] ray traces through
 * [net.minecraft.world.item.Item.getPlayerPOVHitResult], which reads the player's current rotation.
 * Keep that local prediction aligned with the rotation carried by [ServerboundUseItemPacket].
 */
fun MultiPlayerGameMode.useItem(
    player: Player,
    hand: InteractionHand,
    yRot: Float,
    xRot: Float,
): InteractionResult {
    if (localPlayerMode == GameType.SPECTATOR) {
        return InteractionResult.PASS
    }

    this.ensureHasSentCarriedItem()
    var interactionResult: InteractionResult = InteractionResult.PASS
    this.startPrediction(world) { sequence ->
        val packet = UseItemPacketRotation.createExplicit(hand, sequence, yRot, xRot)
        val itemStack = player.getItemInHand(hand)
        if (player.cooldowns.isOnCooldown(itemStack)) {
            interactionResult = InteractionResult.PASS
            return@startPrediction packet
        }

        interactionResult = player.useItemWithRotation(itemStack, hand, yRot, xRot)
        return@startPrediction packet
    }

    return interactionResult
}

private fun Player.useItemWithRotation(
    itemStack: ItemStack,
    hand: InteractionHand,
    explicitYRot: Float,
    explicitXRot: Float,
): InteractionResult {
    val previousYRot = yRot
    val previousXRot = xRot
    val useResult = try {
        yRot = explicitYRot
        xRot = explicitXRot
        itemStack.use(world, this, hand)
    } finally {
        yRot = previousYRot
        xRot = previousXRot
    }
    val result = if (useResult is InteractionResult.Success) {
        useResult.heldItemTransformedTo() ?: getItemInHand(hand)
    } else {
        getItemInHand(hand)
    }

    if (result !== itemStack) {
        setItemInHand(hand, result)
    }

    return useResult
}

fun handlePacket(packet: Packet<*>) =
    runCatching { (packet as Packet<ClientGamePacketListener>).handle(mc.connection!!) }

enum class MovePacketType(override val tag: String) : Tagged {
    ON_GROUND_ONLY("OnGroundOnly") {
        override fun generatePacket() =
            ServerboundMovePlayerPacket.StatusOnly(player.onGround(), player.horizontalCollision)
    },
    POSITION_AND_ON_GROUND("PositionAndOnGround") {
        override fun generatePacket() =
            ServerboundMovePlayerPacket.Pos(
                player.x, player.y, player.z,
                player.onGround(), player.horizontalCollision)
    },
    LOOK_AND_ON_GROUND("LookAndOnGround") {
        override fun generatePacket() =
            ServerboundMovePlayerPacket.Rot(player.yRot, player.xRot, player.onGround(), player.horizontalCollision)
    },
    FULL("Full") {
        override fun generatePacket() =
            ServerboundMovePlayerPacket.PosRot(
                player.x, player.y, player.z,
                player.yRot, player.xRot, player.onGround(), player.horizontalCollision)
    };

    abstract fun generatePacket(): ServerboundMovePlayerPacket
}
