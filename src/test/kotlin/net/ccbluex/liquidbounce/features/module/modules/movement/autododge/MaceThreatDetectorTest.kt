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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaceThreatDetectorTest {

    @Test
    fun `held mace inside packet range is an immediate threat without visible falling`() {
        val candidate = MaceThreatCandidate(
            entityId = 7,
            name = "packet-attacker",
            position = Vec3(-0.5, 0.0001, -0.5),
            lookDirection = Vec3.ZERO,
            isHoldingMace = true,
        )

        val threat = MaceThreatDetector().update(
            targetPosition = Vec3(-14.5, 92.0, 26.7),
            candidates = listOf(candidate),
            packetThreatRange = 512.0,
            threatMemoryTicks = 0,
        )

        assertEquals(7, threat?.candidate?.entityId)
        assertEquals(MaceThreatKind.PACKET_CAPABLE, threat?.kind)
    }

    @Test
    fun `selected mace holder survives brief equipment synchronization gaps`() {
        val detector = MaceThreatDetector()
        val candidate = MaceThreatCandidate(
            entityId = 7,
            name = "packet-attacker",
            position = Vec3.ZERO,
            lookDirection = Vec3.ZERO,
            isHoldingMace = true,
        )

        detector.update(Vec3.ZERO, listOf(candidate), packetThreatRange = 512.0, threatMemoryTicks = 2)
        val firstGap = detector.update(Vec3.ZERO, emptyList(), packetThreatRange = 512.0, threatMemoryTicks = 2)
        val secondGap = detector.update(Vec3.ZERO, emptyList(), packetThreatRange = 512.0, threatMemoryTicks = 2)
        val expired = detector.update(Vec3.ZERO, emptyList(), packetThreatRange = 512.0, threatMemoryTicks = 2)

        assertEquals(7, firstGap?.candidate?.entityId)
        assertEquals(7, secondGap?.candidate?.entityId)
        assertNull(expired)
    }
}
