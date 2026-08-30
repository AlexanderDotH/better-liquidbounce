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
fun LocalPlayer.isInHole(feetBlockPos: BlockPos = getFeetBlockPos()): Boolean {
    return DIRECTIONS_EXCLUDING_UP.all {
        feetBlockPos.relative(it).isBlastResistant()
    }
}

fun LocalPlayer.isBurrowed(): Boolean {
    return getFeetBlockPos().isBlastResistant()
}

fun LocalPlayer.getFeetBlockPos(): BlockPos {
    val bb = boundingBox
    return BlockPos(
        Mth.floor(Mth.lerp(0.5, bb.minX, bb.maxX)),
        Mth.ceil(bb.minY),
        Mth.floor(Mth.lerp(0.5, bb.minZ, bb.maxZ))
    )
}

val LivingEntity.wouldBlockHit
    get() = !isOlderThanOrEqual1_8 &&
        this.blockedByShield(this.level().damageSources().playerAttack(player))

/**
 * @see <a href="https://minecraft.fandom.com/wiki/Magma_Block#Damage">Magma Block — Damage</a>
 */
val LocalPlayer.immuneToMagmaBlocks
    get() = this.hasEffect(MobEffects.FIRE_RESISTANCE)
        || (this.getEffect(MobEffects.RESISTANCE)?.amplifier ?: -1) >= 4
        || this.isCreative
        || this.isSpectator
        || this.getItemBySlot(EquipmentSlot.FEET).getEnchantment(Enchantments.FROST_WALKER) > 0

/**
 * @receiver the specific bounding box of a player, mob or even another block.
 */
fun AABB.isOnMagmaBlock(): Boolean {
    // Blocks that are the height of a trapdoor or lower
    // (such as snow layers, carpets, repeaters, or comparators)
    // do not prevent a magma block from damaging mobs and players above it.
    // Therefore, we expand the box downward by 0.2 blocks.
    val expandedBox = inflate(0.0, 0.1, 0.0)
        .move(0.0, -0.1, 0.0)

    return expandedBox.iterateBottomLayerBlockPos().any {
        it.getBlock() is MagmaBlock &&
            expandedBox.intersects(it.collisionShape.bounds().move(it))
    }
}

val Entity?.cameraDistance: Float
    get() {
        var scale: Float
        var distance: Float
        if (this is LivingEntity) {
            scale = this.scale
            distance = this.getAttributeValue(Attributes.CAMERA_DISTANCE).toFloat()
        } else {
            scale = 1f
            distance = 4f
        }

        (this?.vehicle as? LivingEntity)
            ?.takeIf { this.isPassenger }
            ?.also { mount ->
                scale = max(scale, mount.scale)
                distance = max(distance, mount.getAttributeValue(Attributes.CAMERA_DISTANCE).toFloat())
            }

        return scale * distance
    }
