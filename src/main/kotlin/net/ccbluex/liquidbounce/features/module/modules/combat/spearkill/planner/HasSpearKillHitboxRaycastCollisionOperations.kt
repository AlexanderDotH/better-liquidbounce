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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_HITBOX_RAYCAST_MAX_SPAN_LENGTH
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.hasSpearKillHitboxRaycastCollision
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.withVanillaSpearKillBlockShapes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/** Collects the live vanilla collision shapes, then casts the full player hitbox through them. */
internal fun SpearKillModuleState.hasSpearKillHitboxRaycastCollision(
    playerBoundingBox: AABB,
    movement: Vec3,
): Boolean = withVanillaSpearKillBlockShapes {
    if (playerBoundingBox.hasNaN() ||
        !movement.x.isFinite() || !movement.y.isFinite() || !movement.z.isFinite()
    ) {
        return@withVanillaSpearKillBlockShapes true
    }

    // This is broad-phase partitioning only: each span is still a continuous full-hitbox
    // raycast. It prevents a 500-block diagonal target check from asking the world for every
    // shape inside one huge square while retaining exact collision at every point on the ray.
    val movementLength = movement.length()
    if (!movementLength.isFinite()) {
        return@withVanillaSpearKillBlockShapes true
    }
    val spanCount = ceil(movementLength / SPEAR_KILL_HITBOX_RAYCAST_MAX_SPAN_LENGTH)
        .toInt()
        .coerceAtLeast(1)
    val spanMovement = movement.scale(1.0 / spanCount)
    (0 until spanCount).any { spanIndex ->
        val spanBox = playerBoundingBox.move(spanMovement.scale(spanIndex.toDouble()))
        val sweptBox = spanBox.expandTowards(spanMovement)
        val collisionBoxes = buildList {
            world.getBlockCollisions(player, sweptBox).forEach { shape ->
                addAll(shape.toAabbs())
            }
            world.getEntityCollisions(player, sweptBox).forEach { shape ->
                addAll(shape.toAabbs())
            }
            world.worldBorder.takeIf { it.isInsideCloseToBorder(player, sweptBox) }
                ?.collisionShape
                ?.toAabbs()
                ?.let(::addAll)
        }
        hasSpearKillHitboxRaycastCollision(spanBox, spanMovement, collisionBoxes)
    }
}
