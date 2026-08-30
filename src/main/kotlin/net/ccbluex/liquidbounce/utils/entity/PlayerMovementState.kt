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

@file:JvmName("EntityExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.common.ShapeFlag
import net.ccbluex.liquidbounce.interfaces.ClientInputAddition
import net.ccbluex.liquidbounce.interfaces.LocalPlayerAddition
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.DIRECTIONS_EXCLUDING_UP
import net.ccbluex.liquidbounce.utils.block.collisionShape
import net.ccbluex.liquidbounce.utils.block.getBlock
import net.ccbluex.liquidbounce.utils.block.isBlastResistant
import net.ccbluex.liquidbounce.utils.block.raycast
import net.ccbluex.liquidbounce.utils.client.isBlocksAttacksExisting
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.item.isSword
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.math.fma
import net.ccbluex.liquidbounce.utils.math.iterateBottomLayerBlockPos
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.findEdgeCollision
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.DamageTypeTags
import net.minecraft.util.Mth
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.ShieldItem
import net.minecraft.world.item.component.UseEffects
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.MagmaBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.scores.DisplaySlot
import java.lang.Math.fma
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Copied from 1.21.4
val LocalPlayer.airTicks: Int
    get() = (this as LocalPlayerAddition).`liquid_bounce$getAirTicks`()

val LocalPlayer.onGroundTicks: Int
    get() = (this as LocalPlayerAddition).`liquid_bounce$getOnGroundTicks`()

/**
 * Check if the attack speed is below 1 tick. If so, we have a cooldown.
 */
val LocalPlayer.hasCooldown: Boolean
    get() = !isOlderThanOrEqual1_8 && this.getAttributeValue(Attributes.ATTACK_SPEED) < 20.0

@JvmOverloads
fun LocalPlayer.getMovementDirectionOfInput(input: DirectionalInput = DirectionalInput(this.input)): Float {
    return getMovementDirectionOfInput(this.yRot, input)
}

val LivingEntity.usingItemOrNull: ItemStack?
    get() = if (isUsingItem) useItem else null

fun LivingEntity.isInHand(itemStack: ItemStack?, hand: InteractionHand) =
    this.getItemInHand(hand) === itemStack

val LivingEntity.isBlockAction: Boolean
    get() = usingItemOrNull?.useAnimation === ItemUseAnimation.BLOCK

val LivingEntity.isBlockingServerside: Boolean
    get() {
        if (this.isBlocking) return true

        // 1.8 server + 1.9~1.21.4 protocol
        if (this.isUsingItem && !isBlocksAttacksExisting) {
            val usingItem = this.useItem

            // I don't know why but if you join 1.8 server with 1.21.11 client + 1.20.x protocol [useItem] will be same as [mainHandItem]
            if (isInHand(usingItem, InteractionHand.MAIN_HAND) && usingItem.isSword ||
                isInHand(usingItem, InteractionHand.OFF_HAND) && usingItem.item is ShieldItem
            ) {
                return true
            }
        }

        return false
    }

/**
 * @see LocalPlayer.isSlowDueToUsingItem
 */
val Player.isSlowDueToUsingItem: Boolean
    get() = isUsingItem && !(useItem[DataComponents.USE_EFFECTS] ?: UseEffects.DEFAULT).canSprint

fun Entity.lastRenderPos() = Vec3(this.xOld, this.yOld, this.zOld)

fun Player.wouldBeCloseToFallOff(position: Vec3): Boolean {
    val hitbox =
        this.dimensions
            .makeBoundingBox(position)
            .inflate(-0.05, 0.0, -0.05)
            .move(0.0, this.fallDistance - this.maxUpStep(), 0.0)

    return this.level().noCollision(this, hitbox)
}

fun LocalPlayer.isCloseToEdge(
    directionalInput: DirectionalInput = DirectionalInput(this.input),
    distance: Double = 0.1,
    pos: Vec3 = this.position(),
): Boolean {
    val simulatedInput = SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(directionalInput)
    simulatedInput.set(
        jump = false,
        sneak = false
    )

    val simulatedPlayer = SimulatedPlayer.fromClientPlayer(
        simulatedInput
    )

    simulatedPlayer.pos = pos
    simulatedPlayer.tick()

    val nextVelocity = simulatedPlayer.deltaMovement
    val direction = if (nextVelocity.horizontalDistanceSqr() > 0.003 * 0.003) {
        nextVelocity.copy(y = 0.0).normalize()
    } else {
        val movementYaw = getMovementDirectionOfInput(directionalInput)
        Vec3.directionFromRotation(0.0F, movementYaw)
    }

    val from = pos.add(0.0, -0.1, 0.0)
    val to = from.fma(distance, direction)

    if (findEdgeCollision(from, to) != null) {
        return true
    }

    val playerPosInTwoTicks = simulatedPlayer.pos.add(nextVelocity.copy(y = 0.0))

    return wouldBeCloseToFallOff(pos) || wouldBeCloseToFallOff(playerPosInTwoTicks)
}
