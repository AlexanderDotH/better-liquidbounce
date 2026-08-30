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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillDamageEvidenceTest {

    @Test
    fun `damage at either inclusive window boundary produces evidence`() {
        val tracker = SpearKillDamageEvidenceTracker(windowTicks = 2)

        tracker.arm(targetEntityId = 41, predictedHitTick = 100)
        assertEquals(
            SpearKillDamageEvidence(targetEntityId = 41, predictedHitTick = 100, observedTick = 98),
            tracker.observe(entityId = 41, observedTick = 98),
        )

        tracker.arm(targetEntityId = 41, predictedHitTick = 200)
        assertEquals(
            SpearKillDamageEvidence(targetEntityId = 41, predictedHitTick = 200, observedTick = 202),
            tracker.observe(entityId = 41, observedTick = 202),
        )
    }

    @Test
    fun `different target produces no evidence and leaves the window armed`() {
        val tracker = SpearKillDamageEvidenceTracker(windowTicks = 2)
        tracker.arm(targetEntityId = 41, predictedHitTick = 100)

        assertNull(tracker.observe(entityId = 42, observedTick = 101))
        assertTrue(tracker.isArmed)
        assertEquals(
            SpearKillDamageEvidence(targetEntityId = 41, predictedHitTick = 100, observedTick = 101),
            tracker.observe(entityId = 41, observedTick = 101),
        )
    }

    @Test
    fun `expiry clears only after the inclusive window ends`() {
        val tracker = SpearKillDamageEvidenceTracker(windowTicks = 2)
        tracker.arm(targetEntityId = 41, predictedHitTick = 100)

        assertFalse(tracker.expire(currentTick = 102))
        assertTrue(tracker.isArmed)
        assertTrue(tracker.expire(currentTick = 103))
        assertFalse(tracker.isArmed)
        assertNull(tracker.observe(entityId = 41, observedTick = 103))
    }

    @Test
    fun `clear and rearm discard prior correlation without declaring a miss`() {
        val tracker = SpearKillDamageEvidenceTracker(windowTicks = 2)
        tracker.arm(targetEntityId = 41, predictedHitTick = 100)

        tracker.clear()
        assertFalse(tracker.isArmed)
        assertNull(tracker.observe(entityId = 41, observedTick = 100))

        tracker.arm(targetEntityId = 42, predictedHitTick = 200)
        assertEquals(
            SpearKillDamageEvidence(targetEntityId = 42, predictedHitTick = 200, observedTick = 201),
            tracker.observe(entityId = 42, observedTick = 201),
        )
    }

    @Test
    fun `rearming replaces the previous target correlation`() {
        val tracker = SpearKillDamageEvidenceTracker(windowTicks = 2)
        tracker.arm(targetEntityId = 41, predictedHitTick = 100)
        tracker.arm(targetEntityId = 42, predictedHitTick = 200)

        assertNull(tracker.observe(entityId = 41, observedTick = 200))
        assertEquals(
            SpearKillDamageEvidence(targetEntityId = 42, predictedHitTick = 200, observedTick = 200),
            tracker.observe(entityId = 42, observedTick = 200),
        )
    }

    @Test
    fun `one network optimized attempt may widen only its own evidence window`() {
        val tracker = SpearKillDamageEvidenceTracker(windowTicks = 2)

        tracker.arm(targetEntityId = 41, predictedHitTick = 100, windowTicks = 6)
        assertEquals(
            SpearKillDamageEvidence(targetEntityId = 41, predictedHitTick = 100, observedTick = 106),
            tracker.observe(entityId = 41, observedTick = 106),
        )

        tracker.arm(targetEntityId = 42, predictedHitTick = 200)
        assertNull(tracker.observe(entityId = 42, observedTick = 203))
        assertFalse(tracker.isArmed)
    }
}
