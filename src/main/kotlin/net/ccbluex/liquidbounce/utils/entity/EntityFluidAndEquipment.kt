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
val Entity.isInsideWaterOrBubbleColumn: Boolean
    get() = this.isInWater || this.inBlockState.`is`(Blocks.BUBBLE_COLUMN)

inline var ClientInput.movementForward: Float
    get() = moveVector.y
    set(value) {
        (this as ClientInputAddition).`liquid_bounce$setMovementInput`(moveVector.copy(y = value))
    }

inline var ClientInput.movementSideways: Float
    get() = moveVector.x
    set(value) {
        (this as ClientInputAddition).`liquid_bounce$setMovementInput`(moveVector.copy(x = value))
    }

val LivingEntity.handItems: Array<ItemStack>
    get() = arrayOf(mainHandItem, offhandItem)

val LivingEntity.armorItems: Array<ItemStack>
    get() = arrayOf(
        getItemBySlot(EquipmentSlot.FEET),
        getItemBySlot(EquipmentSlot.LEGS),
        getItemBySlot(EquipmentSlot.CHEST),
        getItemBySlot(EquipmentSlot.HEAD),
    )

// Copied from 1.21.4 END

/**
 * Mirrors the blocking-angle and bypass checks from
 * `net.minecraft.world.entity.LivingEntity#applyItemBlocking`.
 *
 * @see net.minecraft.world.entity.LivingEntity#applyItemBlocking
 */
@JvmOverloads
fun LivingEntity.blockedByShield(source: DamageSource, damageAmount: Float = 1.0F): Boolean =
    getBlockedDamage(source, damageAmount) > 0.0F

/**
 * Mirrors the client-computable part of `net.minecraft.world.entity.LivingEntity#applyItemBlocking`.
 *
 * @see net.minecraft.world.entity.LivingEntity#applyItemBlocking
 */
internal fun LivingEntity.getBlockedDamage(source: DamageSource, damageAmount: Float): Float {
    if (damageAmount <= 0.0F) {
        return 0.0F
    }

    val itemStack = itemBlockingWith ?: return 0.0F
    val blocksAttacks = itemStack[DataComponents.BLOCKS_ATTACKS] ?: return 0.0F

    if (blocksAttacks.bypassedBy().orElse(null)?.contains(source.typeHolder()) ?: false) {
        return 0.0F
    }

    val entity = source.directEntity
    if (entity is AbstractArrow && entity.pierceLevel > 0.toByte()) {
        return 0.0F
    }

    val horizontalAngle = source.sourcePosition?.let { sourcePosition ->
        val viewVector = calculateViewVector(0.0F, yHeadRot)
        val sourceDirection = sourcePosition
            .subtract(position())
            .copy(y = 0.0)
            .normalize()
        acos(sourceDirection.dot(viewVector))
    } ?: Math.PI

    return blocksAttacks.resolveBlockedDamage(source, damageAmount, horizontalAngle)
}

val Entity.netherPosition: Vec3
    get() = if (this.level().dimension() == Level.NETHER) {
        Vec3(x, y, z)
    } else {
        Vec3(x / 8.0, y, z / 8.0)
    }

val LocalPlayer.moving
    get() = input.moveVector != Vec2.ZERO

val ClientInput.untransformed: Input
    get() = (this as ClientInputAddition).`liquid_bounce$getUntransformed`()

val ClientInput.initial: Input
    get() = (this as ClientInputAddition).`liquid_bounce$getInitial`()

val Player.ping: Int
    get() = mc.connection?.getPlayerInfo(uuid)?.latency ?: 0

val InteractionHand.opposite: InteractionHand
    get() = if (this === InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND

fun GameType.shortName(): String = when (this) {
    GameType.SURVIVAL -> "S"
    GameType.CREATIVE -> "C"
    GameType.ADVENTURE -> "A"
    GameType.SPECTATOR -> "S"
}
