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
package net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ObserverChecksTest {

    @Test
    fun `normal sprint movement does not flag prediction check`() {
        val check = ObservedMovementPredictionCheck()

        val flag = check.handleMovement(
            movement(delta = Vec3(0.28, 0.0, 0.0), sprinting = true),
            DetectorStrictness.CONSERVATIVE,
        )

        assertNull(flag)
    }

    @Test
    fun `impossible horizontal movement flags prediction check`() {
        val check = ObservedMovementPredictionCheck()

        val flag = check.handleMovement(
            movement(delta = Vec3(1.4, 0.0, 0.0), sprinting = true),
            DetectorStrictness.CONSERVATIVE,
        )

        assertNotNull(flag)
        assertEquals(PlayerCheatCheck.MOVEMENT, flag!!.check)
    }

    @Test
    fun `teleport-like movement is exempt from prediction check`() {
        val check = ObservedMovementPredictionCheck()

        val flag = check.handleMovement(
            movement(delta = Vec3(20.0, 0.0, 0.0), teleportLike = true),
            DetectorStrictness.STRICT,
        )

        assertNull(flag)
    }

    @Test
    fun `ground spoof symptoms flag after repeated impossible ground states`() {
        val check = ObservedGroundSpoofSymptomsCheck()
        val frame = movement(onGround = true, nearGround = false)

        assertNull(check.handleMovement(frame.copy(tick = 1), DetectorStrictness.NORMAL))
        assertNull(check.handleMovement(frame.copy(tick = 2), DetectorStrictness.NORMAL))

        val flag = check.handleMovement(frame.copy(tick = 3), DetectorStrictness.NORMAL)

        assertNotNull(flag)
        assertEquals("grim.groundspoof.no_fall", flag!!.sourceStableKey)
    }

    @Test
    fun `reach flags when target box is beyond conservative observer reach`() {
        val check = ObservedReachCheck()
        val action = action(
            type = ObservedActionType.DAMAGE,
            targetBoundingBox = AABB(5.0, 0.0, 0.0, 5.6, 1.8, 0.6),
            targetName = "Victim",
        )

        val flag = check.handleAction(action, DetectorStrictness.CONSERVATIVE)

        assertNotNull(flag)
        assertEquals(PlayerCheatCheck.REACH, flag!!.check)
    }

    @Test
    fun `far place flags when attributed block is too far away`() {
        val check = ObservedFarPlaceCheck()
        val action = action(type = ObservedActionType.BLOCK_PLACE, blockPos = BlockPos(8, 0, 0))

        val flag = check.handleAction(action, DetectorStrictness.CONSERVATIVE)

        assertNotNull(flag)
        assertEquals("grim.scaffolding.far_place", flag!!.sourceStableKey)
    }

    private fun movement(
        delta: Vec3 = Vec3(0.0, 0.0, 0.0),
        sprinting: Boolean = false,
        onGround: Boolean = false,
        nearGround: Boolean = true,
        teleportLike: Boolean = false,
    ) = ObservedMovementFrame(
        playerId = PLAYER_ID,
        playerName = "Target",
        entityId = 2,
        tick = 1,
        position = Vec3.ZERO.add(delta),
        previousPosition = Vec3.ZERO,
        delta = delta,
        boundingBox = AABB(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
        eyeY = 1.62,
        yaw = 0f,
        pitch = 0f,
        onGround = onGround,
        nearGround = nearGround,
        inFluid = false,
        swimming = false,
        fallFlying = false,
        passenger = false,
        sprinting = sprinting,
        crouching = false,
        hurtTime = 0,
        swingTime = 0,
        teleportLike = teleportLike,
    )

    private fun action(
        type: ObservedActionType,
        targetBoundingBox: AABB? = null,
        targetName: String? = null,
        blockPos: BlockPos? = null,
    ) = ObservedActionFrame(
        playerId = PLAYER_ID,
        playerName = "Target",
        entityId = 2,
        tick = 1,
        type = type,
        position = Vec3.ZERO,
        eyeY = 1.62,
        targetName = targetName,
        targetBoundingBox = targetBoundingBox,
        blockPos = blockPos,
    )

    private companion object {
        val PLAYER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
