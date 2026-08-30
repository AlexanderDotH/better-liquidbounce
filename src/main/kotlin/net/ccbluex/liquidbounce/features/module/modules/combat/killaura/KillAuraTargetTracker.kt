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
package net.ccbluex.liquidbounce.features.module.modules.combat.killaura

import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoWeapon
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.features.combat.runtime.TargetTracker
import net.ccbluex.liquidbounce.utils.entity.wouldBlockHit
import net.ccbluex.liquidbounce.utils.item.isAxe
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

object KillAuraTargetTracker : TargetTracker(fovRange = 0f..365f) {

    /**
     * Allows to ignore when the target is holding a shield,
     * which would normally block attacks.
     */
    private val ignoreShield by boolean("IgnoreShield", true)

    override fun validate(entity: LivingEntity): Boolean {
        return super.validate(entity) && validateShield(entity)
    }

    /**
     * Check if the entity is holding a shield and if the shield would block the attack.
     */
    private fun validateShield(entity: LivingEntity): Boolean {
        if (ignoreShield || entity !is Player || isOlderThanOrEqual1_8) {
            return true
        }

        if (player.mainHandItem.isAxe || ModuleAutoWeapon.willShieldBreak) {
            return true
        }

        return !entity.wouldBlockHit
    }

}

internal fun calculateKillAuraTargetingRange(
    delegateKillAuraAttacks: Boolean,
    normalMaximumRange: Float,
    reachHitAvailable: Boolean,
    reachHitMaximumRange: Float,
    spearKillRunning: Boolean = false,
    spearKillMaximumRange: Float = 0f,
    maceKillRunning: Boolean = false,
    maceKillMaximumRange: Float = 0f,
): Float = if (!delegateKillAuraAttacks) {
    normalMaximumRange
} else {
    maxOf(
        normalMaximumRange,
        reachHitMaximumRange.takeIf { reachHitAvailable } ?: 0f,
        spearKillMaximumRange.takeIf { spearKillRunning } ?: 0f,
        maceKillMaximumRange.takeIf { maceKillRunning } ?: 0f,
    )
}

internal data class KillAuraSpearTargetSelectionSnapshot(
    val selectionEvaluated: Boolean,
    val trackedTargetPresent: Boolean,
    val trackedTargetValid: Boolean,
    val trackedTargetUsesSpearKill: Boolean,
    val trackedTargetOwnedByAnotherRoute: Boolean,
    val spearKillRouteActive: Boolean,
)

internal val KillAuraSpearTargetSelectionSnapshot.shouldReacquire: Boolean
    get() = when {
        spearKillRouteActive -> false
        !selectionEvaluated -> true
        !trackedTargetPresent -> false
        !trackedTargetValid -> true
        trackedTargetUsesSpearKill || trackedTargetOwnedByAnotherRoute -> false
        else -> true
    }
