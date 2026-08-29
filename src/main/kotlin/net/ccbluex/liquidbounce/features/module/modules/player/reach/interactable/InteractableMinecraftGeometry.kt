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

import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableEntityKind
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Collision sweep used for a normal one-block step instead of intersecting its support block diagonally. */
internal fun interactableSweepWaypoints(from: Vec3, to: Vec3): List<Vec3> {
    val hasHorizontalTravel = abs(from.x - to.x) > POSITION_EPSILON || abs(from.z - to.z) > POSITION_EPSILON
    val verticalDistance = abs(from.y - to.y)
    if (!hasHorizontalTravel || verticalDistance < STEP_HEIGHT_THRESHOLD ||
        verticalDistance > MAXIMUM_STEP_HEIGHT
    ) {
        return listOf(to)
    }
    return if (to.y > from.y) {
        listOf(Vec3(from.x, to.y, from.z), to)
    } else {
        listOf(Vec3(to.x, from.y, to.z), to)
    }
}

internal fun interactionOutlinePoints(position: BlockPos, boxes: List<AABB>): List<Vec3> = boxes.flatMap { box ->
    val minimum = Vec3(position.x + box.minX, position.y + box.minY, position.z + box.minZ)
    val maximum = Vec3(position.x + box.maxX, position.y + box.maxY, position.z + box.maxZ)
    val center = minimum.add(maximum).scale(0.5)
    listOf(
        Vec3(minimum.x, center.y, center.z),
        Vec3(maximum.x, center.y, center.z),
        Vec3(center.x, minimum.y, center.z),
        Vec3(center.x, maximum.y, center.z),
        Vec3(center.x, center.y, minimum.z),
        Vec3(center.x, center.y, maximum.z),
    )
}.distinct()

internal fun isInteractableMenuAvailable(
    hasMenuProvider: Boolean,
    opensMenuWithoutProvider: Boolean,
): Boolean = hasMenuProvider || opensMenuWithoutProvider

internal fun requiresSecondaryUse(kind: InteractableEntityKind): Boolean =
    kind == InteractableEntityKind.CHEST_BOAT || kind == InteractableEntityKind.CHEST_RAFT

private const val POSITION_EPSILON = 1.0E-6
private const val STEP_HEIGHT_THRESHOLD = 0.5
private const val MAXIMUM_STEP_HEIGHT = 1.0 + POSITION_EPSILON
