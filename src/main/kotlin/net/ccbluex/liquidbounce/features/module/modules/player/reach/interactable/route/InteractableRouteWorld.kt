/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * Client-thread world view injected by the live Interactable controller.
 *
 * [isPassable] represents the full two-block player clearance. [isSupported] represents a safe
 * floor stance. Implementations must not load chunks and should return false for unavailable data.
 */
internal interface InteractableRouteWorld {
    fun isWithinBuildHeight(y: Int): Boolean
    fun isLoaded(position: BlockPos): Boolean
    fun isPassable(position: BlockPos): Boolean
    fun isSupported(position: BlockPos): Boolean
    fun isSurface(position: BlockPos): Boolean
    fun isBedrock(position: BlockPos): Boolean
    fun isSegmentClear(from: Vec3, to: Vec3): Boolean
}
