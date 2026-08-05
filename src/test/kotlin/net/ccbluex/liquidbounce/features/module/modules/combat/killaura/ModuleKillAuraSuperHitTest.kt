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

package net.ccbluex.liquidbounce.features.module.modules.combat.killaura

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleKillAuraSuperHitTest {

    @Test
    fun `normal KillAura attack wins when both routes are available`() {
        assertEquals(
            KillAuraAttackRoute.NORMAL,
            selectKillAuraAttackRoute(
                normalAttackPossible = true,
                superHitRunning = true,
                superHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `distant target uses SuperHit only while both modules can handle it`() {
        assertEquals(
            KillAuraAttackRoute.SUPER_HIT,
            selectKillAuraAttackRoute(
                normalAttackPossible = false,
                superHitRunning = true,
                superHitTargetPossible = true,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraAttackRoute(
                normalAttackPossible = false,
                superHitRunning = false,
                superHitTargetPossible = true,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraAttackRoute(
                normalAttackPossible = false,
                superHitRunning = true,
                superHitTargetPossible = false,
            ),
        )
    }

    @Test
    fun `SuperHit expands acquisition range only while it is running`() {
        assertEquals(7f, calculateKillAuraTargetingRange(7f, false, 100f))
        assertEquals(100f, calculateKillAuraTargetingRange(7f, true, 100f))
        assertEquals(7f, calculateKillAuraTargetingRange(7f, true, 5f))
    }

    @Test
    fun `failed SuperHit does not fall back or run success bookkeeping`() = runTest {
        var normalAttacks = 0
        var superHitAttacks = 0
        var successfulAttacks = 0

        val success = executeKillAuraAttack(
            route = KillAuraAttackRoute.SUPER_HIT,
            normalAttack = {
                normalAttacks++
                true
            },
            superHitAttack = {
                superHitAttacks++
                false
            },
            onSuccess = { successfulAttacks++ },
        )

        assertFalse(success)
        assertEquals(0, normalAttacks)
        assertEquals(1, superHitAttacks)
        assertEquals(0, successfulAttacks)
    }

    @Test
    fun `successful selected route runs bookkeeping exactly once`() = runTest {
        var successfulAttacks = 0

        val success = executeKillAuraAttack(
            route = KillAuraAttackRoute.NORMAL,
            normalAttack = { true },
            superHitAttack = { error("SuperHit must not run for a normal target") },
            onSuccess = { successfulAttacks++ },
        )

        assertTrue(success)
        assertEquals(1, successfulAttacks)
    }
}
