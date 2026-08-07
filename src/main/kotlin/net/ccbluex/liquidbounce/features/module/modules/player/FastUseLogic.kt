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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.utils.item.isSpear
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack

internal fun isFastUseSpear(itemStack: ItemStack): Boolean = isFastUseSpearTag(itemStack.isSpear)

internal fun isFastUseSpearTag(isTaggedSpear: Boolean): Boolean = isTaggedSpear

internal fun shouldAccelerateFastUseFood(
    foodRunning: Boolean,
    hasBlockingCondition: Boolean,
    isUsingItem: Boolean,
    isConsumable: Boolean,
): Boolean = foodRunning && !hasBlockingCondition && isUsingItem && isConsumable

internal fun shouldStopFastUseFoodInput(foodRunning: Boolean, stopInput: Boolean): Boolean =
    foodRunning && stopInput

internal fun shouldRenderFastUseSpear(
    fastUseRunning: Boolean,
    spearRunning: Boolean,
    isUsingItem: Boolean,
    isUsingSpear: Boolean,
    usedHand: InteractionHand,
    renderedHand: InteractionHand,
    spearKillControlsAnimation: Boolean = false,
): Boolean = !spearKillControlsAnimation &&
    fastUseRunning &&
    spearRunning &&
    isUsingItem &&
    isUsingSpear &&
    usedHand == renderedHand

internal fun shouldReleaseFastUseSpear(
    spearKillRunning: Boolean,
    ticksUsingItem: Int,
    delayTicks: Int,
): Boolean = !spearKillRunning && ticksUsingItem > delayTicks

internal fun shouldRefreshFastUseSpear(
    isUseKeyDown: Boolean,
    ticksUsingItem: Int,
    damageUseDuration: Int,
): Boolean = isUseKeyDown && damageUseDuration > 0 && ticksUsingItem >= damageUseDuration - 1

internal fun adjustedSpearAnimationTicks(@Suppress("UNUSED_PARAMETER") originalTicks: Float, delayTicks: Int): Float =
    delayTicks.toFloat()
