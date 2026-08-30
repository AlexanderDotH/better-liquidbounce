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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura

import it.unimi.dsi.fastutil.floats.FloatFloatImmutablePair
import it.unimi.dsi.fastutil.floats.FloatFloatPair

internal data class CrystalDamageLimits(
    val minEnemyDamage: Float,
    val maxSelfDamage: Float,
    val antiSuicide: Boolean,
    val efficient: Boolean,
)

internal fun acceptCrystalDamage(
    damageToTarget: DamageProvider,
    selfDamage: () -> DamageProvider,
    unsafeFriendDamage: () -> Boolean,
    playerHealth: () -> Float,
    limits: CrystalDamageLimits,
): FloatFloatPair? {
    if (damageToTarget.isSmallerThan(limits.minEnemyDamage)) return null

    val damageToSelf = selfDamage()
    if (limits.antiSuicide && damageToSelf.isAnyGreaterThanOrEqual(playerHealth())) return null
    if (damageToSelf.isGreaterThan(limits.maxSelfDamage)) return null
    if (unsafeFriendDamage()) return null
    if (limits.efficient && damageToTarget.isSmallerThanOrEqual(damageToSelf)) return null

    return FloatFloatImmutablePair(damageToSelf.getFixed(), damageToTarget.getFixed())
}
