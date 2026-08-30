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

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.common.PlayerSimulationHooks
import net.ccbluex.liquidbounce.utils.math.plus
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape

internal fun SimulatedPlayer.moveSimulated(input: Vec3) {
    val movement = PlayerSimulationHooks.movement(MoverType.SELF, input)
    val backedOffMovement = backOffFromEdge(movement)
    val adjustedMovement = adjustMovementForCollisions(backedOffMovement)
    applyPosition(adjustedMovement)

    val xCollision = !Mth.equal(backedOffMovement.x, adjustedMovement.x)
    val zCollision = !Mth.equal(backedOffMovement.z, adjustedMovement.z)
    horizontalCollision = xCollision || zCollision
    verticalCollision = backedOffMovement.y != adjustedMovement.y
    onGround = verticalCollision && backedOffMovement.y < 0.0
    updateAfterMovement(backedOffMovement)
    stopCollidedVelocity(xCollision, zCollision)
}

private fun SimulatedPlayer.applyPosition(movement: Vec3) {
    if (movement.lengthSqr() > 1.0E-7) {
        pos += movement
        boundingBox = player.dimensions.makeBoundingBox(pos)
    }
}

private fun SimulatedPlayer.updateAfterMovement(movement: Vec3) {
    if (!isInWater()) {
        updateFluidInteraction()
    }
    if (onGround) {
        land()
    } else if (movement.y < 0) {
        fallDistance -= movement.y.toFloat()
    }
}

private fun SimulatedPlayer.stopCollidedVelocity(xCollision: Boolean, zCollision: Boolean) {
    if (!horizontalCollision && !verticalCollision) {
        return
    }
    deltaMovement = Vec3(
        if (xCollision) 0.0 else deltaMovement.x,
        if (onGround) 0.0 else deltaMovement.y,
        if (zCollision) 0.0 else deltaMovement.z,
    )
}

private fun SimulatedPlayer.adjustMovementForCollisions(movement: Vec3): Vec3 {
    val collisionBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3).move(pos)
    val entityCollisions = emptyList<VoxelShape>()
    val adjusted = if (movement.lengthSqr() == 0.0) {
        movement
    } else {
        collide(movement, collisionBox, entityCollisions)
    }
    val collidedX = movement.x != adjusted.x
    val collidedY = movement.y != adjusted.y
    val collidedZ = movement.z != adjusted.z
    val canStep = player.maxUpStep() > 0.0f &&
        (onGround || collidedY && movement.y < 0.0) &&
        (collidedX || collidedZ)
    return if (canStep) stepMovement(movement, adjusted, collisionBox, entityCollisions) else adjusted
}

private fun SimulatedPlayer.stepMovement(
    movement: Vec3,
    adjusted: Vec3,
    collisionBox: AABB,
    entityCollisions: List<VoxelShape>,
): Vec3 {
    val stepHeight = player.maxUpStep().toDouble()
    var stepped = collide(Vec3(movement.x, stepHeight, movement.z), collisionBox, entityCollisions)
    val stepUp = collide(
        Vec3(0.0, stepHeight, 0.0),
        collisionBox.expandTowards(movement.x, 0.0, movement.z),
        entityCollisions,
    )
    val stepDown = collide(Vec3(movement.x, 0.0, movement.z), collisionBox.move(stepUp), entityCollisions).add(stepUp)
    if (stepUp.y < stepHeight && stepDown.horizontalDistanceSqr() > stepped.horizontalDistanceSqr()) {
        stepped = stepDown
    }
    if (stepped.horizontalDistanceSqr() <= adjusted.horizontalDistanceSqr()) {
        return adjusted
    }
    return stepped.add(
        collide(Vec3(0.0, -stepped.y + movement.y, 0.0), collisionBox.move(stepped), entityCollisions),
    )
}

private fun SimulatedPlayer.collide(
    movement: Vec3,
    collisionBox: AABB,
    entityCollisions: List<VoxelShape>,
): Vec3 = Entity.collideBoundingBox(player, movement, collisionBox, player.level(), entityCollisions)
