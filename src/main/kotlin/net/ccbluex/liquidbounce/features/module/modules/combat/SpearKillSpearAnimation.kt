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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.InteractionHand

/**
 * SpearKill owns the local raised-spear pose. The server use window stays independent; this only
 * decides when first-/third-person rendering should show the charged spear.
 */
internal fun shouldRaiseSpearKillAnimation(
    spearKillRunning: Boolean,
    holdingSpear: Boolean,
    attackPathActive: Boolean,
    attackRequested: Boolean,
    isUsingSpear: Boolean,
): Boolean = spearKillRunning && holdingSpear && (attackPathActive || attackRequested || isUsingSpear)

/** Forces the first-person use pose whenever SpearKill wants the spear raised. */
internal fun shouldAnimateSpearKillUseItem(
    shouldRaise: Boolean,
    isUsingItem: Boolean,
): Boolean = shouldRaise || isUsingItem

internal fun spearKillRaisedHand(
    shouldRaise: Boolean,
    mainHandIsSpear: Boolean,
    offHandIsSpear: Boolean,
    isUsingItem: Boolean,
    usedHand: InteractionHand,
): InteractionHand? {
    if (!shouldRaise) return null
    if (isUsingItem) return usedHand
    return when {
        mainHandIsSpear -> InteractionHand.MAIN_HAND
        offHandIsSpear -> InteractionHand.OFF_HAND
        else -> null
    }
}

/** Snaps the spear use progress to the kinetic delay so the local pose looks charged. */
internal fun spearKillAnimationTicks(
    shouldRaise: Boolean,
    delayTicks: Int,
    originalTicks: Float,
): Float {
    if (!shouldRaise || delayTicks < 0) return originalTicks
    return delayTicks.toFloat()
}

/**
 * Idle keep-alive for the finite kinetic use window: release + re-use the same hand right before
 * expiry. Never refresh mid-path — that would drop ticks under delayTicks and abort Packet/A*.
 */
internal fun shouldRefreshSpearKillServerUse(
    attackPathActive: Boolean,
    isUseKeyDown: Boolean,
    ticksUsingItem: Int,
    damageUseDuration: Int,
): Boolean = !attackPathActive &&
    isUseKeyDown &&
    damageUseDuration > 0 &&
    ticksUsingItem >= damageUseDuration - 1

/**
 * After a keep-alive refresh, ticks drop below [delayTicks]. That is still a valid charge — do not
 * tear down preview/path state unless the player actually stopped using the spear.
 */
internal fun shouldResetSpearKillOnUndercharge(
    ticksUsingItem: Int,
    delayTicks: Int,
    isUsingSpear: Boolean,
    isUseKeyDown: Boolean,
): Boolean = ticksUsingItem <= delayTicks && !(isUsingSpear && isUseKeyDown)

/** Speeds the kinetic delay while idle-charging so keep-alive restarts become attack-ready quickly. */
internal fun shouldAccelerateSpearKillCharge(
    attackPathActive: Boolean,
    isUseKeyDown: Boolean,
    isUsingSpear: Boolean,
    ticksUsingItem: Int,
    delayTicks: Int,
): Boolean = !attackPathActive &&
    isUseKeyDown &&
    isUsingSpear &&
    delayTicks >= 0 &&
    ticksUsingItem <= delayTicks
