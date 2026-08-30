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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.entity.set
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Prevents you from bumping into corners when chasing.
 */
object SpeedAntiCornerBump : MinecraftShortcuts {
    /**
     * Called when the speed mode might jump. Decides if the jump should be delayed.
     */
    fun shouldDelayJump(): Boolean {
        val input = SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(DirectionalInput(player.input))

        input.set(
            jump = true
        )

        val simulatedPlayer = SimulatedPlayer.fromClientPlayer(input)

        return getSuggestedJumpDelay(simulatedPlayer) != null
    }

    /**
     * Is called while the player stands on the ground to decide if he should jump now or later.
     * Does the following steps n times:
     * 1. Jumps
     * 2. Wait until the player hits an edge. If we don't hit an edge before being on ground.
     *    We don't suggest a jump delay
     *
     * When we hit a wall on the second jump, we suggest delaying the jump so that the second jump will be able to
     * jump on the block.
     *
     * A delay is not suggested...
     * - if we can’t jump on the block because there is not enough space
     *
     * @param n number of jumps to simulate
     *
     * @return suggested delay in ticks, if we don't suggest a delay, null
     */
    private fun getSuggestedJumpDelay(
        simulatedPlayer: SimulatedPlayer,
        n: Int = 2,
    ): Int? {
        var jumpCount = 1
        var lastGroundPos = simulatedPlayer.pos
        for (tickIdx in 0..65) {
            simulatedPlayer.tick()
            if (simulatedPlayer.onGround && jumpCount++ >= n) return null
            if (simulatedPlayer.onGround) lastGroundPos = simulatedPlayer.pos
            if (!simulatedPlayer.horizontalCollision) continue
            return suggestedDelayAtCollision(simulatedPlayer, jumpCount, lastGroundPos, tickIdx)
        }
        return null
    }

    private fun suggestedDelayAtCollision(
        simulatedPlayer: SimulatedPlayer,
        jumpCount: Int,
        lastGroundPos: Vec3,
        tickIdx: Int,
    ): Int? {
        if (jumpCount == 1 && simulatedPlayer.deltaMovement.y > 0.0) return null
        if (!canJumpOnBlock(simulatedPlayer.pos, lastGroundPos)) return null
        return tickIdx
    }

    /**
     * @param lastGroundPos the last position where the player was on ground
     */
    fun canJumpOnBlock(
        collidingPos: Vec3,
        lastGroundPos: Vec3,
    ): Boolean {
        val playerDims = player.getDimensions(Pose.STANDING)
        val box: AABB = playerDims.makeBoundingBox(collidingPos)
        val blockPos = BlockPos.containing(box.minX - 1.0E-7, collidingPos.y, box.minZ - 1.0E-7)
        val blockPos2 = BlockPos.containing(box.maxX + 1.0E-7, collidingPos.y, box.maxZ + 1.0E-7)

        if (!world.hasChunksAt(blockPos, blockPos2)) {
            return false
        }

        val jumpOnPos = BlockPos.MutableBlockPos(0, blockPos.y, 0)
        for (x in blockPos.x..blockPos2.x) {
            for (z in blockPos.z..blockPos2.z) {
                jumpOnPos.x = x
                jumpOnPos.z = z
                if (canJumpAt(jumpOnPos, lastGroundPos, collidingPos, box, playerDims)) return true
            }
        }
        return false
    }

    private fun canJumpAt(
        jumpOnPos: BlockPos,
        lastGroundPos: Vec3,
        collidingPos: Vec3,
        collisionBox: AABB,
        playerDimensions: EntityDimensions,
    ): Boolean {
        val jumpOnState = jumpOnPos.stateOrEmpty
        if (jumpOnPos.y + 1 - lastGroundPos.y > 1.3) return false
        if (!shouldJumpOnBlock(jumpOnPos, jumpOnState, collisionBox)) return false
        val oneAbove = jumpOnPos.above(1)
        val twoAbove = jumpOnPos.above(2)
        val standingBox = playerDimensions.makeBoundingBox(collidingPos.x, jumpOnPos.y + 1.0, collidingPos.z)
        return canPlayerEnterBlockPos(oneAbove, oneAbove.stateOrEmpty, standingBox, true) &&
            canPlayerEnterBlockPos(twoAbove, twoAbove.stateOrEmpty, standingBox, false)
    }

    /**
     * Would we come to a stop at the given block due to collision?
     *
     * @param playerBox the player box at the moment he collides with the given block
     */
    fun shouldJumpOnBlock(
        pos: BlockPos,
        blockState: BlockState,
        playerBox: AABB,
    ): Boolean {
        val collisionShape = blockState.getCollisionShape(mc.level!!, pos)

        // The player is currently colliding with the wall, but not inside of it. So we need to expand the
        // player box a bit so that we can check if we collide with that box.
        val extendedPlayerBox = playerBox.inflate(0.01, 0.01, 0.01)

        collisionShape.toAabbs().forEach {
            // Does the player collide with that box? If he collides, and we cannot step the block,
            // we would collide with that block, so we should jump on it.
            if (it.move(pos).intersects(extendedPlayerBox) && it.maxY > 0.5) {
                return true
            }
        }

        return false
    }

    /**
     * Used for blocks that are above the block we try to jump on. Checks if the player can enter the block pos
     * or if he would collide with the block and fall down.
     *
     * @param playerBox the player box. it's minY should be the top of the block we want to jump on.
     * @param tolerateLowBoundingBoxes if true, bounding boxes whose height is 0.2 or lower are ignored. This should be
     * set if this block is directly above the block the player currently tries to jump on. Since in that case
     * the player can also jump on that block (because of the jump height), regardless if there is a block two blocks
     * above (because `1.8 (player height) + 0.2 = 2.0`).
     */
    fun canPlayerEnterBlockPos(
        pos: BlockPos,
        blockState: BlockState,
        playerBox: AABB,
        tolerateLowBoundingBoxes: Boolean,
    ): Boolean {
        val collisionShape = blockState.getCollisionShape(mc.level!!, pos)

        // The player is currently colliding with the wall, but not inside of it. So we need to expand the
        // player box a bit so that we can check if we collide with that box.
        val extendedPlayerBox = playerBox.inflate(0.01, 0.01, 0.01)

        collisionShape.toAabbs().forEach {
            if (tolerateLowBoundingBoxes && it.maxY <= 0.2) {
                return@forEach
            }
            // The player collides with that part of the bounding box. Thus, he would just slide down the block.
            if (it.move(pos).intersects(extendedPlayerBox)) {
                return false
            }
        }

        return true
    }
}
