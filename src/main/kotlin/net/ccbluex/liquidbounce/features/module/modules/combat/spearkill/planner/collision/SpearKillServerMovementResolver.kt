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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision


import net.ccbluex.liquidbounce.utils.entity.resolveStepUpMovement
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Mirrors the step-up part of [Entity.move] without mutating the real player. */
internal fun resolveSpearKillServerPacketMovement(
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
    if (!landed) {
        stepSearchBox = stepSearchBox.expandTowards(0.0, -SPEAR_KILL_STEP_SUPPORT_EPSILON, 0.0)
    }

    val colliders = buildList {
        addAll(entityColliders)
        world.worldBorder.takeIf { it.isInsideCloseToBorder(player, stepSearchBox) }
            ?.let { add(it.collisionShape) }
        addAll(world.getBlockCollisions(player, stepSearchBox))
    }
    return resolveStepUpMovement(
        movement = movement,
        directMovement = directMovement,
        boundingBox = boundingBox,
        groundedBox = groundedBox,
        maxUpStep = player.maxUpStep(),
        colliders = colliders,
    )
}

private const val SPEAR_KILL_STEP_SUPPORT_EPSILON = 1.0E-5
