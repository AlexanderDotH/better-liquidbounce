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

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillTargetPredictionTest {

    @Test
    fun `direct terminal keeps a stable linear target prediction`() {
        assertFalse(shouldReplanSpearKillDirectTerminal(
            plannedPosition = Vec3(10.0, 64.0, 10.0),
            currentPosition = Vec3(10.6, 64.0, 10.0),
            ticksSincePlan = 2,
            plannedVelocity = Vec3(0.3, 0.0, 0.0),
        ))
    }

    @Test
    fun `direct terminal replans after a one tick lateral direction change`() {
        assertTrue(shouldReplanSpearKillDirectTerminal(
            plannedPosition = Vec3(10.0, 64.0, 10.0),
            currentPosition = Vec3(10.0, 64.0, 10.3),
            ticksSincePlan = 1,
            plannedVelocity = Vec3.ZERO,
        ))
    }

    @Test
    fun `direct terminal commits after one fresh replan instead of chasing lag jitter forever`() {
        assertFalse(shouldReplanSpearKillDirectTerminal(
            plannedPosition = Vec3(10.0, 64.0, 10.0),
            currentPosition = Vec3(10.0, 64.0, 10.3),
            ticksSincePlan = 1,
            plannedVelocity = Vec3.ZERO,
            terminalReplanInstalled = true,
        ))
    }

    @Test
    fun `direct terminal fails safe on invalid prediction input`() {
        assertTrue(shouldReplanSpearKillDirectTerminal(
            plannedPosition = Vec3(Double.NaN, 64.0, 10.0),
            currentPosition = Vec3(10.0, 64.0, 10.0),
            ticksSincePlan = 1,
            plannedVelocity = Vec3.ZERO,
        ))
    }
}
