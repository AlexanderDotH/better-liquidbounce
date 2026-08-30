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
/**
 * Check if the player can step up by [height] blocks.
 *
 * TODO: Use Minecraft Step logic instead of this basic collision check.
 */
fun LocalPlayer.canStep(height: Double = 1.0): Boolean {
    if (!horizontalCollision || isDescending || !onGround()) {
        // If we are not colliding with anything, we are not meant to step
        return false
    }

    val box = this.boundingBox

    val angle = Math.toRadians(this.yRot.toDouble())
    val xOffset = -sin(angle) * 0.1
    val zOffset = cos(angle) * 0.1

    val offsetBox = box.move(xOffset, 0.0, zOffset)
    val stepBox = offsetBox.move(0.0, height, 0.0)

    return this.level().getBlockCollisions(this, stepBox).allEmpty()
        && this.level().getBlockCollisions(this, offsetBox).anyNotEmpty()
}

fun getMovementDirectionOfInput(facingYaw: Float, input: DirectionalInput = DirectionalInput(player.input)): Float {
    var actualYaw = facingYaw
    val forwardMultiplier = when {
        input.backwards && !input.forwards -> {
            actualYaw += 180f
            -0.5f
        }

        input.forwards && !input.backwards -> 0.5f
        else -> 1f
    }

    if (input.left && !input.right) {
        actualYaw -= 90f * forwardMultiplier
    }
    if (input.right && !input.left) {
        actualYaw += 90f * forwardMultiplier
    }

    return actualYaw
}

inline val Entity.horizontalSpeed: Double
    get() = deltaMovement.horizontalDistance()

fun Vec3.withStrafe(
    speed: Double = horizontalDistance(),
    strength: Double = 1.0,
    input: DirectionalInput? = DirectionalInput(player.input),
    yaw: Float = player.getMovementDirectionOfInput(input ?: DirectionalInput(player.input)),
): Vec3 {
    if (input?.isMoving == false) {
        return Vec3(0.0, y, 0.0)
    }

    val oneMinusStrength = 1.0 - strength
    val prevX = x * oneMinusStrength
    val prevZ = z * oneMinusStrength
    val usedSpeed = speed * strength

    val angle = Math.toRadians(yaw.toDouble())
    val newX = prevX - sin(angle) * usedSpeed
    val newZ = prevZ + cos(angle) * usedSpeed

    return Vec3(newX, y, newZ)
}
