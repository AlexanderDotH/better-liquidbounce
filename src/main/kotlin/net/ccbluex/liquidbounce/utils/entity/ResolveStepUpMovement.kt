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
@file:JvmName("FallingPlayerKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.entity

import it.unimi.dsi.fastutil.floats.FloatArraySet
import it.unimi.dsi.fastutil.floats.FloatArrays
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.sqrt

/**
 * Follows Minecraft 26.2 {@code Entity.collectCandidateStepUpHeights()} and
 * {@code Entity.collideWithShapes()} when selecting a step-up movement.
 */
internal fun resolveStepUpMovement(
    movement: Vec3,
    directMovement: Vec3,
    boundingBox: AABB,
    groundedBox: AABB,
    maxUpStep: Float,
    colliders: List<VoxelShape>,
): Vec3 {
    val sortedCandidates = collectStepUpCandidates(
        colliders,
        groundedBox.minY,
        directMovement.y.toFloat(),
        maxUpStep,
    )
    for (candidate in sortedCandidates) {
        val steppedMovement = collideWithShapes(
            Vec3(movement.x, candidate.toDouble(), movement.z),
            groundedBox,
            colliders,
        )
        if (steppedMovement.horizontalDistanceSqr() > directMovement.horizontalDistanceSqr()) {
            return steppedMovement.subtract(0.0, boundingBox.minY - groundedBox.minY, 0.0)
        }
    }

    return directMovement
}

private fun collectStepUpCandidates(
    colliders: List<VoxelShape>,
    groundedMinY: Double,
    directY: Float,
    maxUpStep: Float,
): FloatArray {
    val candidates = FloatArraySet(4)
    for (collider in colliders) {
        val coords = collider.getCoords(Direction.Axis.Y)
        for (i in coords.indices) {
            val relativeCoordinate = (coords.getDouble(i) - groundedMinY).toFloat()
            if (relativeCoordinate < 0f || relativeCoordinate == directY) {
                continue
            }
            if (relativeCoordinate > maxUpStep) {
                break
            }
            candidates.add(relativeCoordinate)
        }
    }

    return candidates.toFloatArray().also { FloatArrays.unstableSort(it) }
}

/**
 * Mirrors Minecraft 26.2 {@code Entity.collideWithShapes()}.
 */
internal fun collideWithShapes(movement: Vec3, boundingBox: AABB, shapes: List<VoxelShape>): Vec3 {
    if (shapes.isEmpty()) {
        return movement
    }

    var resolvedMovement = Vec3.ZERO
    for (axis in Direction.axisStepOrder(movement)) {
        val axisMovement = movement.get(axis)
        if (axisMovement != 0.0) {
            val collision = Shapes.collide(axis, boundingBox.move(resolvedMovement), shapes, axisMovement)
            resolvedMovement = resolvedMovement.with(axis, collision)
        }
    }
    return resolvedMovement
}

/**
 * Mirrors Minecraft 26.2 {@code CollisionGetter.findSupportingBlock()}: nearest first,
 * then the greater {@code BlockPos.compareTo()} position on an exact distance tie.
 */
internal fun selectSupportingBlock(candidates: Iterator<BlockPos>, position: Vec3): BlockPos? {
    val support = BlockPos.MutableBlockPos()
    var supportDistance = Double.POSITIVE_INFINITY

    for (candidate in candidates) {
        val distance = candidate.distToCenterSqr(position)
        if (distance < supportDistance ||
            distance == supportDistance && support.compareTo(candidate) < 0
        ) {
            support.set(candidate)
            supportDistance = distance
        }
    }

    return support.takeIf { supportDistance.isFinite() }
}
