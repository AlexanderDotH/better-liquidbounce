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

import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockCollisions
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.EntityCollisionContext

@Suppress("LongParameterList")
class FallingPlayer(
    private val player: LocalPlayer,
    var x: Double,
    var y: Double,
    var z: Double,
    motionX: Double,
    motionY: Double,
    motionZ: Double,
    yRot: Float
) {
    companion object {
        private const val SUPPORT_EPSILON = 1.0E-6

        @JvmStatic
        @JvmOverloads
        fun fromPlayer(player: LocalPlayer, movementYaw: Float = player.yRot): FallingPlayer {
            return FallingPlayer(
                player,
                player.x,
                player.y,
                player.z,
                player.deltaMovement.x,
                player.deltaMovement.y,
                player.deltaMovement.z,
                movementYaw
            )
        }
    }

    private val motion = FallingPlayerMotion(player, motionX, motionY, motionZ, yRot)
    private var boundingBox = player.getDimensions(player.pose).makeBoundingBox(x, y, z)
    private var lastResolvedMovement = Vec3.ZERO

    fun findCollision(ticks: Int): CollisionResult? {
        val rotationVec = player.lookAngle

        for (i in 0 until ticks) {
            val positionBeforeMovement = Vec3(x, y, z)
            if (advanceSimulation(rotationVec, forceDescending = false)) {
                return CollisionResult(
                    findSupportingBlock(boundingBox, lastResolvedMovement),
                    i,
                    positionBeforeMovement,
                )
            }
        }
        return null
    }

    /**
     * Checks whether a future movement tick starts with the player's feet in [targetPos] before landing.
     * This matches the block-position lookup in Minecraft 26.2 {@code LivingEntity.onClimbable()}.
     */
    fun willStartTickInBlockBeforeCollision(
        targetPos: BlockPos,
        ticks: Int,
        forceDescending: Boolean = false,
    ): Boolean {
        val rotationVec = player.lookAngle

        repeat(ticks) {
            if (BlockPos.containing(x, y, z) == targetPos) {
                return true
            }

            if (advanceSimulation(rotationVec, forceDescending)) {
                return false
            }
        }

        return false
    }

    private fun advanceSimulation(rotationVec: Vec3, forceDescending: Boolean): Boolean {
        val intendedMovement = motion.calculateMovementForTick(rotationVec)
        val resolvedMovement = if (forceDescending) {
            val collisionContext = EntityCollisionContext(
                true,
                false,
                y,
                player.mainHandItem,
                false,
                player,
            )
            Entity.collideBoundingBox(collisionContext, intendedMovement, boundingBox, world, emptyList())
        } else {
            collidePlayer(intendedMovement)
        }
        val collidedDownwards = intendedMovement.y < 0.0 && resolvedMovement.y != intendedMovement.y

        lastResolvedMovement = resolvedMovement
        x += resolvedMovement.x
        y += resolvedMovement.y
        z += resolvedMovement.z
        boundingBox = boundingBox.move(resolvedMovement)

        if (collidedDownwards) {
            return true
        }

        motion.finishTick(intendedMovement, resolvedMovement)
        return false
    }

    /**
     * Mirrors Minecraft 26.2's private {@code Entity.collide(Vec3)} step-up path.
     */
    private fun collidePlayer(movement: Vec3): Vec3 {
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
            stepSearchBox = stepSearchBox.expandTowards(0.0, -1.0E-5, 0.0)
        }

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

    /**
     * Mirrors Minecraft 26.2 {@code Entity.checkSupportingBlock()} including its moved-back fallback.
     */
    private fun findSupportingBlock(boundingBox: AABB, movement: Vec3): BlockPos? {
        val testArea = AABB(
            boundingBox.minX,
            boundingBox.minY - SUPPORT_EPSILON,
            boundingBox.minZ,
            boundingBox.maxX,
            boundingBox.minY,
            boundingBox.maxZ,
        )

        return findSupportingBlock(testArea)
            ?: findSupportingBlock(testArea.move(-movement.x, 0.0, -movement.z))
    }

    private fun findSupportingBlock(testArea: AABB): BlockPos? {
        val candidates = BlockCollisions(world, player, testArea, false) { pos, _ -> pos }
        return selectSupportingBlock(candidates, Vec3(x, y, z))
    }

    /**
     * [positionBeforeMovement] matches the position from which Minecraft 26.2 performs item use
     * before the movement step represented by [tick].
     */
    class CollisionResult(
        val pos: BlockPos?,
        val tick: Int,
        val positionBeforeMovement: Vec3,
    )
}
