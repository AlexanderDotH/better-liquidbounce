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

internal enum class SpearKillChargeDecision {
    WAIT_FOR_VANILLA,
    RESET,
    READY,
}

/** Leaves undercharged spear use on vanilla packet cadence instead of manufacturing movement ticks. */
internal fun resolveSpearKillChargeDecision(
    ticksUsingItem: Int,
    delayTicks: Int,
    isUsingSpear: Boolean,
    useRequested: Boolean,
): SpearKillChargeDecision = when {
    ticksUsingItem > delayTicks -> SpearKillChargeDecision.READY
    isUsingSpear && useRequested -> SpearKillChargeDecision.WAIT_FOR_VANILLA
    else -> SpearKillChargeDecision.RESET
}
