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
 */
package net.ccbluex.liquidbounce.features.module.modules.render.trajectories

import net.ccbluex.liquidbounce.utils.entity.handItems
import net.ccbluex.liquidbounce.utils.render.trajectory.HeldItemTrajectoryResolver
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryShotDescriptor
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.ItemStack

internal fun shouldFilterHeldFishingRod(
    hasFishingHook: Boolean,
    activeTrajectoryOther: Boolean,
    fishingBobberEnabled: Boolean,
) = hasFishingHook && activeTrajectoryOther && fishingBobberEnabled

internal fun findHeldTrajectory(
    player: Player,
    filterFishingRod: Boolean,
    alwaysShowBow: Boolean,
    showMultiShot: Boolean,
): Pair<List<TrajectoryShotDescriptor>, ItemStack>? = player.handItems.firstNotNullOfOrNull { stack ->
    if (filterFishingRod && stack.item is FishingRodItem) return@firstNotNullOfOrNull null
    HeldItemTrajectoryResolver.resolveHeldItemShots(
        player,
        stack,
        alwaysShowBow,
        includeMultiShot = showMultiShot,
    )?.let { it to stack }
}
