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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillServerSneakTest {

    @Test
    fun `active route keeps Packet sneak after the crouching pose reaches the client`() {
        assertTrue(
            SpearKillServerSneak.shouldMaintain(
                requestedByRoute = true,
                serverSneaking = true,
                isFallFlying = false,
                currentHeight = 1.5,
                crouchingHeight = 1.5,
            ),
        )
    }

    @Test
    fun `completed route releases Packet sneak even while the client is crouched`() {
        assertFalse(
            SpearKillServerSneak.shouldMaintain(
                requestedByRoute = false,
                serverSneaking = true,
                isFallFlying = false,
                currentHeight = 1.5,
                crouchingHeight = 1.5,
            ),
        )
    }

    @Test
    fun `server sneak sends only the transitions needed to bracket a packet route`() {
        assertEquals(
            SpearKillServerSneak.Action.START,
            SpearKillServerSneak.nextAction(serverSneaking = false, shouldSneak = true),
        )
        assertEquals(
            SpearKillServerSneak.Action.NONE,
            SpearKillServerSneak.nextAction(serverSneaking = true, shouldSneak = true),
        )
        assertEquals(
            SpearKillServerSneak.Action.STOP,
            SpearKillServerSneak.nextAction(serverSneaking = true, shouldSneak = false),
        )
        assertEquals(
            SpearKillServerSneak.Action.NONE,
            SpearKillServerSneak.nextAction(serverSneaking = false, shouldSneak = false),
        )
    }
}
