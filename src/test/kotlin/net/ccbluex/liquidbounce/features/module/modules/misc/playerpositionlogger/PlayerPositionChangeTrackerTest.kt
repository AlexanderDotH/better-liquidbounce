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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerPositionChangeTrackerTest {

    @Test
    fun `tracker emits initial changed and removed states without unchanged duplicates`() {
        val tracker = PlayerPositionChangeTracker()
        val initial = sample(LoggedVector(1.0, 64.0, 2.0))

        assertEquals(PlayerPositionLogKind.STATE_INITIAL, tracker.update(listOf(initial)).single().kind)
        assertTrue(tracker.update(listOf(initial)).isEmpty())

        val moved = sample(LoggedVector(1.25, 64.0, 2.0))
        val changed = tracker.update(listOf(moved)).single()
        assertEquals(PlayerPositionLogKind.STATE_CHANGED, changed.kind)
        assertEquals(initial.state, changed.previousState)
        assertEquals(moved.state, changed.sample.state)

        val removed = tracker.update(emptyList()).single()
        assertEquals(PlayerPositionLogKind.STATE_REMOVED, removed.kind)
        assertEquals(moved, removed.sample)
        assertTrue(tracker.update(emptyList()).isEmpty())
    }

    private fun sample(position: LoggedVector) = PlayerPositionSample(
        PlayerPositionIdentity(
            entityId = 7,
            uuid = "00000000-0000-0000-0000-000000000007",
            name = "Remote",
            local = false,
        ),
        PlayerPositionState(
            position = position,
            previousPosition = position,
            trackingPosition = position,
            positionCodecBase = position,
            velocity = LoggedVector.ZERO,
            rotation = LoggedPlayerRotation(0f, 0f, 0f, 0f),
            onGround = true,
            horizontalCollision = false,
            verticalCollision = true,
            fallDistance = 0.0,
            passenger = false,
            vehicleEntityId = null,
            pose = "standing",
        ),
    )
}
