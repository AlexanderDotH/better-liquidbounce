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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt



/** Action required to maintain the spear use owned or borrowed by KillAura inheritance. */
internal enum class SpearKillInheritedUseAction {
    NONE,
    KEEP_CURRENT_USE,
    START_MAIN_HAND,
    START_OFF_HAND,
}

internal fun resolveSpearKillInheritedUseAction(
    requestActive: Boolean,
    mainHandSpear: Boolean,
    offhandSpear: Boolean,
    isUsingItem: Boolean,
    isUsingSpear: Boolean,
): SpearKillInheritedUseAction = when {
    !requestActive -> SpearKillInheritedUseAction.NONE
    isUsingSpear -> SpearKillInheritedUseAction.KEEP_CURRENT_USE
    isUsingItem -> SpearKillInheritedUseAction.NONE
    mainHandSpear -> SpearKillInheritedUseAction.START_MAIN_HAND
    offhandSpear -> SpearKillInheritedUseAction.START_OFF_HAND
    else -> SpearKillInheritedUseAction.NONE
}

internal fun shouldStopSpearKillInheritedUse(
    startedUse: Boolean,
    isUsingItem: Boolean,
    isSameHand: Boolean,
    isUsingSpear: Boolean,
): Boolean = shouldPreserveSpearKillInheritedUse(startedUse, isUsingItem, isSameHand, isUsingSpear)

/** True only while the continuous spear use was started by KillAura inheritance and is still intact. */
internal fun shouldPreserveSpearKillInheritedUse(
    startedUse: Boolean,
    isUsingItem: Boolean,
    isSameHand: Boolean,
    isUsingSpear: Boolean,
): Boolean = startedUse && isUsingItem && isSameHand && isUsingSpear
