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
package net.ccbluex.liquidbounce.utils.entity

import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal interface LivingDamageReductionAccess {
    fun isInvulnerable(): Boolean
    fun isDeadOrDying(): Boolean
    fun isPlayer(): Boolean
    fun isPlayerInvulnerable(): Boolean
    fun bypassesInvulnerability(): Boolean
    fun scalesWithDifficulty(): Boolean
    fun difficulty(): Difficulty
    fun isFireDamage(): Boolean
    fun hasFireResistance(): Boolean
    fun blockedDamage(amount: Float): Float
    fun afterArmorAbsorb(amount: Float): Float
    fun afterMagicAbsorb(amount: Float): Float
    fun absorptionAmount(): Float
}

internal object LivingDamageReducer {

    fun reduce(
        access: LivingDamageReductionAccess,
        damage: Float,
        ignoreShield: Boolean = false,
        includeAbsorption: Boolean = false,
    ): Float {
        if (access.isInvulnerable() || access.isDeadOrDying()) return 0.0F

        val adjustedDamage = adjustPlayerDamage(access, damage) ?: return 0.0F
        if (adjustedDamage == 0.0F) return 0.0F
        if (access.isFireDamage() && access.hasFireResistance()) return 0.0F

        return applyDefenses(access, adjustedDamage, ignoreShield, includeAbsorption)
    }

    private fun adjustPlayerDamage(access: LivingDamageReductionAccess, damage: Float): Float? {
        if (!access.isPlayer()) return damage
        if (access.isPlayerInvulnerable() && !access.bypassesInvulnerability()) return null
        if (!access.scalesWithDifficulty()) return damage

        return when (access.difficulty()) {
            Difficulty.PEACEFUL -> 0.0F
            Difficulty.EASY -> (damage / 2.0F + 1.0F).coerceAtMost(damage)
            Difficulty.HARD -> damage * 3.0F / 2.0F
            else -> damage
        }
    }

    private fun applyDefenses(
        access: LivingDamageReductionAccess,
        damage: Float,
        ignoreShield: Boolean,
        includeAbsorption: Boolean,
    ): Float {
        var amount = damage
        if (!ignoreShield) {
            amount -= access.blockedDamage(amount)
            if (amount == 0.0F) return 0.0F
        }

        amount = access.afterArmorAbsorb(amount)
        amount = access.afterMagicAbsorb(amount)
        return if (includeAbsorption) {
            (amount - access.absorptionAmount()).coerceAtLeast(0.0F)
        } else {
            amount
        }
    }
}

internal class LivingEntityDamageAccess(
    private val entity: LivingEntity,
    private val source: DamageSource,
) : LivingDamageReductionAccess {
    private val level = entity.level()

    override fun isInvulnerable(): Boolean {
        val serverLevel = level as? ServerLevel
        return if (serverLevel != null) {
            entity.isInvulnerableTo(serverLevel, source)
        } else {
            entity.isInvulnerableToBase(source)
        }
    }

    override fun isDeadOrDying() = entity.isDeadOrDying

    override fun isPlayer() = entity is Player

    override fun isPlayerInvulnerable() = (entity as Player).abilities.invulnerable

    override fun bypassesInvulnerability() = source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)

    override fun scalesWithDifficulty() = source.scalesWithDifficulty()

    override fun difficulty() = level.difficulty

    override fun isFireDamage() = source.`is`(DamageTypeTags.IS_FIRE)

    override fun hasFireResistance() = entity.hasEffect(MobEffects.FIRE_RESISTANCE)

    override fun blockedDamage(amount: Float) = entity.getBlockedDamage(source, amount)

    override fun afterArmorAbsorb(amount: Float) = entity.getDamageAfterArmorAbsorb(source, amount)

    override fun afterMagicAbsorb(amount: Float) = entity.getDamageAfterMagicAbsorb(source, amount)

    override fun absorptionAmount() = entity.absorptionAmount
}
