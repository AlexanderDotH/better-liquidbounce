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

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.level.Explosion

// Copied from 1.21.4
/**
 * Mirrors the vanilla damage-reduction pipeline after the base amount is known.
 *
 * By default, this returns the remaining damage before absorption so callers can compare it
 * against `health + absorptionAmount`. Pass [includeAbsorption] to mirror the final health
 * loss applied by vanilla.
 *
 * @see net.minecraft.world.entity.player.Player#hurtServer
 * @see net.minecraft.world.entity.LivingEntity#hurtServer
 * @see net.minecraft.world.entity.LivingEntity#getDamageAfterArmorAbsorb
 * @see net.minecraft.world.entity.LivingEntity#getDamageAfterMagicAbsorb
 * @see net.minecraft.world.entity.LivingEntity#actuallyHurt
 */
@JvmOverloads
fun LivingEntity.getEffectiveDamage(
    source: DamageSource,
    damage: Float,
    ignoreShield: Boolean = false,
    includeAbsorption: Boolean = false
): Float = LivingDamageReducer.reduce(
    access = LivingEntityDamageAccess(this, source),
    damage = damage,
    ignoreShield = ignoreShield,
    includeAbsorption = includeAbsorption,
)

/**
 * Mirrors the vanilla blast-power setup of explosive entities.
 *
 * TNT minecarts use the current speed to reproduce vanilla's upper-bound radius because
 * `net.minecraft.world.entity.vehicle.minecart.MinecartTNT#explode` multiplies the speed
 * term by server-side randomness.
 *
 * @see net.minecraft.world.entity.boss.enderdragon.EndCrystal.hurtServer
 * @see net.minecraft.world.entity.item.PrimedTnt
 * @see net.minecraft.world.entity.vehicle.minecart.MinecartTNT.explode
 * @see net.minecraft.world.entity.monster.Creeper
 */
fun LivingEntity.getExplosionDamageFromEntity(entity: Entity): Float {
    return when (entity) {
        is EndCrystal -> getDamageFromExplosion(
            pos = entity.position(),
            power = 6f,
            explosionRange = 12f,
            damageDistance = 144f,
            damageSource = Explosion.getDefaultDamageSource(this.level(), entity)
        )

        is PrimedTnt -> getDamageFromExplosion(
            pos = entity.position().add(0.0, 0.0625, 0.0),
            power = 4f,
            explosionRange = 8f,
            damageDistance = 64f,
            damageSource = Explosion.getDefaultDamageSource(this.level(), entity)
        )

        is MinecartTNT -> getDamageFromExplosion(
            pos = entity.position(),
            power = entity.getMaximumPotentialExplosionPower(),
            damageSource = Explosion.getDefaultDamageSource(this.level(), entity)
        )

        is Creeper -> {
            val f = if (entity.isPowered) 2f else 1f
            getDamageFromExplosion(
                pos = entity.position(),
                power = entity.explosionRadius * f,
                damageSource = Explosion.getDefaultDamageSource(this.level(), entity)
            )
        }

        else -> 0f
    }
}
