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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleKillAuraReachHitTest {

    @Test
    fun `normal KillAura attack wins when both routes are available`() {
        assertEquals(
            KillAuraAttackRoute.NORMAL,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = true,
                spearKillRunning = true,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `SpearKill owns a distant target before Reach Hit`() {
        assertEquals(
            KillAuraAttackRoute.SPEAR_KILL,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                spearKillRunning = true,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.REACH_HIT,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                spearKillRunning = false,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `global opt in gates every inherited KillAura attack route`() {
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = false,
                normalAttackPossible = false,
                spearKillRunning = true,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `distant target uses Reach Hit only while its integration is available`() {
        assertEquals(
            KillAuraAttackRoute.REACH_HIT,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                reachHitAvailable = false,
                reachHitTargetPossible = true,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                reachHitAvailable = true,
                reachHitTargetPossible = false,
            ),
        )
    }

    @Test
    fun `Reach Hit expands acquisition range only while its integration is available`() {
        assertEquals(7f, calculateKillAuraTargetingRange(false, 7f, true, 100f, true, 500f))
        assertEquals(7f, calculateKillAuraTargetingRange(true, 7f, false, 100f))
        assertEquals(100f, calculateKillAuraTargetingRange(true, 7f, true, 100f))
        assertEquals(7f, calculateKillAuraTargetingRange(true, 7f, true, 5f))
        assertEquals(500f, calculateKillAuraTargetingRange(true, 7f, true, 100f, true, 500f))
    }

    @Test
    fun `delegated attacks bypass KillAura continuous aiming and range-exit prediction`() {
        assertTrue(shouldUseKillAuraAimPipeline(false, false))
        assertFalse(shouldUseKillAuraAimPipeline(true, false))
        assertFalse(shouldUseKillAuraAimPipeline(false, true))
        assertTrue(shouldPredictKillAuraRangeExit(delegatedReachHit = false))
        assertFalse(shouldPredictKillAuraRangeExit(delegatedReachHit = true))
    }

    @Test
    fun `Reach Hit dispatch uses stable center rotation without pitching vertically`() {
        val rotation = calculateKillAuraDelegatedAttackRotation(
            eyes = Vec3(0.0, 65.62, 0.0),
            targetBox = AABB(9.7, 64.0, -0.3, 10.3, 65.8, 0.3),
        )

        assertEquals(-90f, rotation.yaw, 0.001f)
        assertTrue(rotation.pitch in 0f..10f, "pitch=${rotation.pitch}")
    }

    @Test
    fun `SpearKill route never invokes KillAura attacks or success bookkeeping`() = runTest {
        var successfulAttacks = 0

        val success = executeKillAuraAttack(
            route = KillAuraAttackRoute.SPEAR_KILL,
            normalAttack = { error("normal attack must remain suppressed") },
            reachHitAttack = { error("Reach Hit must remain suppressed") },
            onSuccess = { successfulAttacks++ },
        )

        assertFalse(success)
        assertEquals(0, successfulAttacks)
    }

    @Test
    fun `failed Reach Hit does not fall back or run success bookkeeping`() = runTest {
        var normalAttacks = 0
        var reachHitAttacks = 0
        var successfulAttacks = 0

        val success = executeKillAuraAttack(
            route = KillAuraAttackRoute.REACH_HIT,
            normalAttack = {
                normalAttacks++
                true
            },
            reachHitAttack = {
                reachHitAttacks++
                false
            },
            onSuccess = { successfulAttacks++ },
        )

        assertFalse(success)
        assertEquals(0, normalAttacks)
        assertEquals(1, reachHitAttacks)
        assertEquals(0, successfulAttacks)
    }

    @Test
    fun `successful selected route runs bookkeeping exactly once`() = runTest {
        var successfulAttacks = 0

        val success = executeKillAuraAttack(
            route = KillAuraAttackRoute.NORMAL,
            normalAttack = { true },
            reachHitAttack = { error("Reach Hit must not run for a normal target") },
            onSuccess = { successfulAttacks++ },
        )

        assertTrue(success)
        assertEquals(1, successfulAttacks)
    }
}
