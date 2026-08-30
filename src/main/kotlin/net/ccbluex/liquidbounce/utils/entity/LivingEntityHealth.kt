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
 * Sometimes the server does not publish the actual entity health with its metadata.
 * This function incorporates other sources to get the actual value.
 *
 * Currently, uses the following sources:
 * 1. Scoreboard
 */
fun LivingEntity.getActualHealth(fromScoreboard: Boolean = true): Float {
    if (fromScoreboard) {
        val health = getHealthFromScoreboard()

        if (health != null) {
            return health
        }
    }


    return health
}

private val HEALTH_KEYWORDS = listOf("❤", "HP", "Health", "Здоровья", "Здоровье")

fun LivingEntity.hasHealthScoreboard(): Boolean {
    if (this == player) return false

    val objective = this.level().scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME) ?: return false
    val displayName = objective.displayName.string

    return HEALTH_KEYWORDS.any { displayName.contains(it) }
}

private fun LivingEntity.getHealthFromScoreboard(): Float? {
    if (!this.hasHealthScoreboard()) return null
    val objective = this.level().scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME)
    val score = objective?.scoreboard?.getPlayerScoreInfo(this, objective) ?: return null

    return score.value().toFloat()
}

fun Entity.getBoundingBoxAt(pos: Vec3): AABB {
    return boundingBox.move(pos - this.position())
}

/**
 * Check if the entity collides with anything below his bounding box.
 */
fun Entity.doesNotCollideBelow(until: Double = -64.0): Boolean {
    if (this.y < until || boundingBox.minY < until) {
        return true
    }

    val offsetBb = boundingBox.setMinY(until)
    return this.level().getBlockCollisions(this, offsetBb).allEmpty()
}

/**
 * Check if the entity box collides with any block in the world at the given [pos].
 */
fun Entity.doesCollideAt(pos: Vec3 = this.position()): Boolean {
    return !this.level().getBlockCollisions(this, getBoundingBoxAt(pos)).allEmpty()
}

/**
 * Check if the entity is likely falling to the void based on the given position and bounding box.
 */
fun Entity.wouldFallIntoVoid(pos: Vec3, voidLevel: Double = -64.0, safetyExpand: Double = 0.0): Boolean {
    val offsetBb = getBoundingBoxAt(pos)

    if (pos.y < voidLevel || offsetBb.minY < voidLevel) {
        return true
    }

    // If there is no collision to void threshold, we don't want to teleport down.
    val boundingBox = offsetBb
        // Set the minimum Y to the void threshold to check for collisions below the player
        .setMinY(voidLevel)
        // Expand the bounding box to check if there might be blocks to safely land on
        .inflate(safetyExpand, 0.0, safetyExpand)
    return this.level().getBlockCollisions(this, boundingBox).allEmpty()
}


fun LocalPlayer.warp(pos: Vec3? = null, onGround: Boolean = false) {
    val vehicle = this.vehicle

    if (vehicle != null) {
        pos?.let(vehicle::setPos)
        connection.send(ServerboundMoveVehiclePacket.fromEntity(vehicle))
        return
    }

    if (pos != null) {
        connection.send(ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, onGround, horizontalCollision))
    } else {
        connection.send(ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision))
    }
}
