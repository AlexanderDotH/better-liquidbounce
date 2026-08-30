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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.interactableSweepWaypoints
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteWorld
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

internal class MinecraftInteractableRouteWorld(
    private val level: net.minecraft.client.multiplayer.ClientLevel,
    private val player: Entity,
) : InteractableRouteWorld {
    private val dimensions = player.getDimensions(Pose.STANDING)

    override fun isWithinBuildHeight(y: Int): Boolean =
        !level.isOutsideBuildHeight(y) && !level.isOutsideBuildHeight(y + ceil(dimensions.height).toInt() - 1)

    override fun isLoaded(position: BlockPos): Boolean =
        level.isLoaded(position) && level.isLoaded(position.above(ceil(dimensions.height).toInt()))

    override fun isPassable(position: BlockPos): Boolean {
        val stance = position.stancePosition()
        val box = dimensions.makeBoundingBox(stance).deflate(COLLISION_EPSILON)
        return level.worldBorder.isWithinBounds(box) && level.noCollision(player, box)
    }

    override fun isSupported(position: BlockPos): Boolean {
        val box = dimensions.makeBoundingBox(position.stancePosition())
            .move(0.0, -SUPPORT_DEPTH, 0.0)
        return level.getBlockCollisions(player, box).anyNotEmpty()
    }

    override fun isSurface(position: BlockPos): Boolean = level.canSeeSky(position.above())

    override fun isBedrock(position: BlockPos): Boolean = level.getBlockState(position).block === Blocks.BEDROCK

    override fun isSegmentClear(from: Vec3, to: Vec3): Boolean {
        val sweep = listOf(from) + interactableSweepWaypoints(from, to)
        val collisionFree = sweep.zipWithNext().all { (start, end) -> isCollisionFreeSegment(start, end) }
        if (!collisionFree) return false
        if (from.y != to.y) return isSupportedAt(from) && isSupportedAt(to)
        val distance = from.distanceTo(to)
        val samples = ceil(distance / SWEEP_SAMPLE_DISTANCE).toInt().coerceAtLeast(1)
        return (0..samples).all { index ->
            isSupportedAt(from.lerp(to, index.toDouble() / samples))
        }
    }

    private fun isCollisionFreeSegment(from: Vec3, to: Vec3): Boolean {
        val samples = ceil(from.distanceTo(to) / SWEEP_SAMPLE_DISTANCE).toInt().coerceAtLeast(1)
        return (0..samples).all { index ->
            val point = from.lerp(to, index.toDouble() / samples)
            isLoaded(BlockPos.containing(point)) && isPassableAt(point)
        }
    }

    private fun isPassableAt(point: Vec3): Boolean {
        val box = dimensions.makeBoundingBox(point).deflate(COLLISION_EPSILON)
        return level.worldBorder.isWithinBounds(box) && level.getBlockCollisions(player, box).allEmpty()
    }

    private fun isSupportedAt(point: Vec3): Boolean = level.getBlockCollisions(
        player,
        dimensions.makeBoundingBox(point).move(0.0, -SUPPORT_DEPTH, 0.0),
    ).anyNotEmpty()
}

internal const val COLLISION_EPSILON = 1.0E-7
private const val SUPPORT_DEPTH = 0.05
private const val SWEEP_SAMPLE_DISTANCE = 0.25
