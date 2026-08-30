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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.utils.entity.resolveStepUpMovement
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun createMaceKillServerPacketSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
    hasDestinationCollision: (AABB) -> Boolean,
    resolveMovement: (AABB, Vec3) -> Vec3,
): SpearKillAStarSegmentValidator {
    val results = HashMap<MaceKillPacketSegment, Boolean>()
    return SpearKillAStarSegmentValidator { from, to ->
        if (!from.hasFiniteMaceKillRouteCoordinates() || !to.hasFiniteMaceKillRouteCoordinates()) {
            false
        } else {
            val segment = MaceKillPacketSegment(from, to)
            results[segment] ?: isMaceKillServerPacketSegmentAccepted(
                origin,
                playerBoundingBox,
                from,
                to,
                hasDestinationCollision,
                resolveMovement,
            ).also { results[segment] = it }
        }
    }
}

private fun isMaceKillServerPacketSegmentAccepted(
    origin: Vec3,
    playerBoundingBox: AABB,
    from: Vec3,
    to: Vec3,
    hasDestinationCollision: (AABB) -> Boolean,
    resolveMovement: (AABB, Vec3) -> Vec3,
): Boolean {
    val destinationBox = playerBoundingBox
        .move(to.subtract(origin))
        .deflate(MACE_KILL_SERVER_DESTINATION_EPSILON)
    if (hasDestinationCollision(destinationBox)) return false
    val requestedMovement = to.subtract(from)
    if (requestedMovement.lengthSqr() <= MACE_KILL_ROUTE_MOVEMENT_EPSILON_SQUARED) return true
    val rawMovementBox = playerBoundingBox.move(from.subtract(origin))
    val movementBox = AABB(
        rawMovementBox.minX,
        rawMovementBox.minY + MACE_KILL_SERVER_DESTINATION_EPSILON,
        rawMovementBox.minZ,
        rawMovementBox.maxX,
        rawMovementBox.maxY,
        rawMovementBox.maxZ,
    )
    val resolvedMovement = resolveMovement(movementBox, requestedMovement)
    if (!resolvedMovement.hasFiniteMaceKillRouteCoordinates()) return false
    val residualX = requestedMovement.x - resolvedMovement.x
    val residualZ = requestedMovement.z - resolvedMovement.z
    return residualX * residualX + residualZ * residualZ <= MACE_KILL_MOVED_WRONGLY_THRESHOLD_SQUARED
}

internal fun resolveMaceKillServerPacketMovement(
    player: LocalPlayer,
    boundingBox: AABB,
    movement: Vec3,
): Vec3 {
    val world = player.level()
    val entityColliders = world.getEntityCollisions(player, boundingBox.expandTowards(movement))
    val directMovement = if (movement.lengthSqr() == 0.0) {
        movement
    } else {
        Entity.collideBoundingBox(player, movement, boundingBox, world, entityColliders)
    }
    val collidedHorizontally = movement.x != directMovement.x || movement.z != directMovement.z
    val landed = movement.y < 0.0 && movement.y != directMovement.y
    if (player.maxUpStep() <= 0f || (!landed && !player.onGround()) || !collidedHorizontally) {
        return directMovement
    }
    val groundedBox = if (landed) boundingBox.move(0.0, directMovement.y, 0.0) else boundingBox
    var stepSearchBox = groundedBox.expandTowards(movement.x, player.maxUpStep().toDouble(), movement.z)
    if (!landed) stepSearchBox = stepSearchBox.expandTowards(0.0, -MACE_KILL_STEP_SUPPORT_EPSILON, 0.0)
    val colliders = buildList {
        addAll(entityColliders)
        world.worldBorder.takeIf { it.isInsideCloseToBorder(player, stepSearchBox) }
            ?.let { add(it.collisionShape) }
        addAll(world.getBlockCollisions(player, stepSearchBox))
    }
    return resolveStepUpMovement(
        movement,
        directMovement,
        boundingBox,
        groundedBox,
        player.maxUpStep(),
        colliders,
    )
}

private fun Vec3.hasFiniteMaceKillRouteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
private data class MaceKillPacketSegment(val from: Vec3, val to: Vec3)

private const val MACE_KILL_MOVED_WRONGLY_THRESHOLD_SQUARED = 0.0625
private const val MACE_KILL_SERVER_DESTINATION_EPSILON = 1.0E-5
private const val MACE_KILL_ROUTE_MOVEMENT_EPSILON_SQUARED = 1.0E-12
private const val MACE_KILL_STEP_SUPPORT_EPSILON = 1.0E-5
