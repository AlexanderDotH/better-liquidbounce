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

/** Route-task-local cache. It never outlives the locked interaction attempt. */
internal class CachedInteractableRouteWorld(
    private val delegate: InteractableRouteWorld,
) {

    private val buildHeight = HashMap<Int, Boolean>()
    private val loaded = HashMap<BlockPos, Boolean>()
    private val passable = HashMap<BlockPos, Boolean>()
    private val supported = HashMap<BlockPos, Boolean>()
    private val surface = HashMap<BlockPos, Boolean>()
    private val bedrock = HashMap<BlockPos, Boolean>()
    private val segments = HashMap<RouteSegmentKey, Boolean>()

    fun isWithinBuildHeight(y: Int): Boolean = buildHeight.getOrPut(y) {
        delegate.isWithinBuildHeight(y)
    }

    fun isLoaded(position: BlockPos): Boolean = loaded.getOrPut(position.immutableCopy()) {
        delegate.isLoaded(position)
    }

    fun isPassable(position: BlockPos): Boolean = passable.getOrPut(position.immutableCopy()) {
        delegate.isPassable(position)
    }

    fun isSupported(position: BlockPos): Boolean = supported.getOrPut(position.immutableCopy()) {
        delegate.isSupported(position)
    }

    fun isSurface(position: BlockPos): Boolean = surface.getOrPut(position.immutableCopy()) {
        delegate.isSurface(position)
    }

    fun isBedrock(position: BlockPos): Boolean = bedrock.getOrPut(position.immutableCopy()) {
        delegate.isBedrock(position)
    }

    fun isSegmentClearBothWays(from: Vec3, to: Vec3): Boolean =
        isSegmentClear(from, to) && isSegmentClear(to, from)

    private fun isSegmentClear(from: Vec3, to: Vec3): Boolean = segments.getOrPut(RouteSegmentKey(from, to)) {
        delegate.isSegmentClear(from, to)
    }

    private data class RouteSegmentKey(val from: Vec3, val to: Vec3)
}

private fun BlockPos.immutableCopy() = BlockPos(x, y, z)
