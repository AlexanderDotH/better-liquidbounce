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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ObserverViolationBufferTest {

    @Test
    fun `repeated confident flags cross notification threshold once cooldown allows it`() {
        val buffer = ObserverViolationBuffer()
        val flag = flag(confidence = 90)

        assertNull(buffer.submit(flag, DetectorStrictness.NORMAL, 70, 1_000, 0))
        assertNull(buffer.submit(flag, DetectorStrictness.NORMAL, 70, 1_000, 100))

        val notice = buffer.submit(flag, DetectorStrictness.NORMAL, 70, 1_000, 1_100)

        assertNotNull(notice)
        assertEquals("ObservedMovementPrediction", notice!!.flag.checkName)
    }

    @Test
    fun `low confidence flags do not build violation level`() {
        val buffer = ObserverViolationBuffer()
        val flag = flag(confidence = 40)

        repeat(10) {
            assertNull(buffer.submit(flag, DetectorStrictness.STRICT, 70, 0, it * 100L))
        }

        assertEquals(0.0, buffer.violationLevel(ObserverViolationBuffer.Key(flag.playerId, flag.checkName)))
    }

    @Test
    fun `reward decays an existing violation level`() {
        val buffer = ObserverViolationBuffer()
        val flag = flag(confidence = 90)
        val key = ObserverViolationBuffer.Key(flag.playerId, flag.checkName)

        buffer.submit(flag, DetectorStrictness.NORMAL, 70, 0, 0)
        buffer.reward(key, amount = 0.4)

        assertEquals(0.5, buffer.violationLevel(key), 1e-9)
    }

    private fun flag(confidence: Int) = DetectionFlag(
        playerId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        playerName = "Target",
        check = PlayerCheatCheck.MOVEMENT,
        checkName = "ObservedMovementPrediction",
        sourceStableKey = "grim.movement.prediction",
        confidence = confidence,
        severity = DetectionSeverity.INFO,
        verbose = "test",
        observedAtTick = 1,
    )
}
