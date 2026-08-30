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
import net.ccbluex.liquidbounce.utils.block.raycast
import net.minecraft.core.BlockPos
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.EntityCollisionContext
import kotlin.math.sqrt

// Copied from 1.21.4
/**
 * Mirrors the vanilla entity damage formula for explosions.
 *
 * Pass [damageSource] when the original explosion type is known so shield checks and
 * source-sensitive tags stay aligned with vanilla.
 *
 * @see net.minecraft.world.level.ExplosionDamageCalculator#getEntityDamageAmount
 * @see net.minecraft.world.level.ServerExplosion#getSeenPercent
 */
@Suppress("LongParameterList")
fun LivingEntity.getDamageFromExplosion(
    pos: Vec3,
    power: Float = 6f,
    explosionRange: Float = power * 2f, // allows setting precomputed values
    damageDistance: Float = explosionRange * explosionRange,
    exclude: Collection<BlockPos>? = null,
    include: BlockPos? = null,
    maxBlastResistance: Float? = null,
    entityBoundingBox: AABB? = null,
    damageSource: DamageSource? = null,
): Float {
    if (this.distanceToSqr(pos) > damageDistance) {
        return 0f
    }

    try {
        ShapeFlag.noShapeChange = true

        val useTweakedMethod = exclude != null ||
            maxBlastResistance != null ||
            include != null ||
            entityBoundingBox != null

        val exposure = if (useTweakedMethod) {
            getExposureToExplosion(pos, exclude, include, maxBlastResistance, entityBoundingBox)
        } else {
            ServerExplosion.getSeenPercent(pos, this)
        }

        val preprocessedDamage = ExplosionDamageFormula.calculate(
            distanceSquared = this.distanceToSqr(pos),
            explosionRange = explosionRange,
            exposure = exposure,
        )
        if (preprocessedDamage == 0.0) {
            return 0f
        }

        val actualDamageSource = damageSource
            ?: DamageSource(this.level().damageSources().explosion(null).typeHolder(), pos)
        return getEffectiveDamage(actualDamageSource, preprocessedDamage.toFloat())
    } finally {
        ShapeFlag.noShapeChange = false
    }
}

/**
 * Basically [ServerExplosion.getSeenPercent] but this method allows us to exclude blocks using [exclude].
 *
 * @see net.minecraft.world.level.ServerExplosion.getSeenPercent
 */
fun LivingEntity.getExposureToExplosion(
    source: Vec3,
    exclude: Collection<BlockPos>?,
    include: BlockPos?,
    maxBlastResistance: Float?,
    entityBoundingBox: AABB?
): Float {
    val entityBoundingBox1 = entityBoundingBox ?: boundingBox
    val shapeContext = EntityCollisionContext(
        isDescending,
        false,
        entityBoundingBox1.minY,
        mainHandItem,
        false,
        this
    )

    return ExplosionExposureSampler.calculate(entityBoundingBox1) { samplePoint ->
        this.level().raycast(
            ClipContext(
                samplePoint,
                source,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                shapeContext,
            ),
            exclude,
            include,
            maxBlastResistance,
        ).type == HitResult.Type.MISS
    }
}

/**
 * Uses the current horizontal speed to reproduce the vanilla TNT minecart blast upper bound.
 *
 * @see net.minecraft.world.entity.vehicle.minecart.MinecartTNT.explode
 */
internal fun MinecartTNT.getMaximumPotentialExplosionPower(): Float {
    val currentHorizontalSpeed = sqrt(this.deltaMovement.horizontalDistanceSqr()).coerceAtMost(5.0).toFloat()
    return 4f + currentHorizontalSpeed * 1.5f
}
