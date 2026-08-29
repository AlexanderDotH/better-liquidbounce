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
import net.ccbluex.liquidbounce.features.module.modules.combat.shouldExcludeMaceKillWaterTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KillAuraMaceKillRouteTest {

    @Test
    fun `held AutoMace excludes water targets from KillAura instead of falling back to a normal hit`() {
        assertTrue(shouldExcludeMaceKillWaterTarget(
            maceKillEnabled = true,
            mainHandMace = true,
            targetInWater = true,
        ))
        assertFalse(shouldExcludeMaceKillWaterTarget(true, true, false))
        assertFalse(shouldExcludeMaceKillWaterTarget(true, false, true))
        assertFalse(shouldExcludeMaceKillWaterTarget(false, true, true))
    }

    @Test
    fun `held MaceKill owns an eligible target even when ordinary melee is possible`() {
        assertEquals(
            KillAuraAttackRoute.MACE_KILL,
            selectKillAuraRemoteKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = true,
                heldRemoteWeapon = KillAuraRemoteWeapon.MACE,
                maceKillAvailable = true,
                maceKillTargetPossible = true,
                spearKillAvailable = true,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `ordinary melee still wins for a held spear`() {
        assertEquals(
            KillAuraAttackRoute.NORMAL,
            selectKillAuraRemoteKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = true,
                heldRemoteWeapon = KillAuraRemoteWeapon.SPEAR,
                maceKillAvailable = false,
                maceKillTargetPossible = false,
                spearKillAvailable = true,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `held valid remote weapon wins remote arbitration`() {
        assertEquals(
            KillAuraAttackRoute.MACE_KILL,
            selectRemoteRoute(heldRemoteWeapon = KillAuraRemoteWeapon.MACE),
        )
        assertEquals(
            KillAuraAttackRoute.SPEAR_KILL,
            selectRemoteRoute(heldRemoteWeapon = KillAuraRemoteWeapon.SPEAR),
        )
    }

    @Test
    fun `KillAura arbitration boundary forwards MaceKill readiness`() {
        assertEquals(
            KillAuraAttackRoute.MACE_KILL,
            selectKillAuraAttackRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                heldRemoteWeapon = KillAuraRemoteWeapon.MACE,
                maceKillAvailable = true,
                maceKillTargetPossible = true,
                spearKillRunning = true,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `MaceKill never selects a hotbar candidate without a mainhand mace`() {
        assertEquals(
            KillAuraAttackRoute.SPEAR_KILL,
            selectRemoteRoute(heldRemoteWeapon = KillAuraRemoteWeapon.NONE),
        )
        assertEquals(
            KillAuraAttackRoute.REACH_HIT,
            selectRemoteRoute(
                heldRemoteWeapon = KillAuraRemoteWeapon.NONE,
                spearKillAvailable = false,
            ),
        )
    }

    @Test
    fun `invalid held route cannot select a different silent remote weapon`() {
        assertEquals(
            KillAuraAttackRoute.SPEAR_KILL,
            selectRemoteRoute(
                heldRemoteWeapon = KillAuraRemoteWeapon.MACE,
                maceKillTargetPossible = false,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.REACH_HIT,
            selectRemoteRoute(
                heldRemoteWeapon = KillAuraRemoteWeapon.SPEAR,
                spearKillTargetPossible = false,
            ),
        )
    }

    @Test
    fun `held mace cannot delegate when MaceKill is unavailable`() {
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraRemoteKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                heldRemoteWeapon = KillAuraRemoteWeapon.MACE,
                maceKillAvailable = false,
                maceKillTargetPossible = true,
                spearKillAvailable = false,
                spearKillTargetPossible = false,
                reachHitAvailable = false,
                reachHitTargetPossible = false,
            ),
        )
    }

    @Test
    fun `MaceKill route legality remains authoritative at the handoff boundary`() {
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraRemoteKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                heldRemoteWeapon = KillAuraRemoteWeapon.MACE,
                maceKillAvailable = true,
                maceKillTargetPossible = false,
                spearKillAvailable = false,
                spearKillTargetPossible = false,
                reachHitAvailable = false,
                reachHitTargetPossible = false,
            ),
        )
    }

    @Test
    fun `global delegation disables MaceKill together with other replacement routes`() {
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraRemoteKillRoute(
                delegateKillAuraAttacks = false,
                normalAttackPossible = false,
                heldRemoteWeapon = KillAuraRemoteWeapon.MACE,
                maceKillAvailable = true,
                maceKillTargetPossible = true,
                spearKillAvailable = true,
                spearKillTargetPossible = true,
                reachHitAvailable = true,
                reachHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `MaceKill ownership suppresses conflicting KillAura subsystems`() {
        val policy = selectKillAuraRemoteKillSuppressionPolicy(KillAuraAttackRoute.MACE_KILL)

        assertTrue(policy.suppressClicker)
        assertTrue(policy.suppressAutoBlock)
        assertTrue(policy.suppressAutoWeapon)
    }

    @Test
    fun `MaceKill candidate alone does not suppress AutoWeapon before route ownership`() {
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraSuppressionRoute(
                maceKillOwnsAttempt = false,
                maceFightBotReservation = false,
                spearKillOwnsAttempt = false,
                spearFightBotReservation = false,
                distantSpearKillTarget = false,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.MACE_KILL,
            selectKillAuraSuppressionRoute(
                maceKillOwnsAttempt = true,
                maceFightBotReservation = false,
                spearKillOwnsAttempt = false,
                spearFightBotReservation = false,
                distantSpearKillTarget = false,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.MACE_KILL,
            selectKillAuraSuppressionRoute(
                maceKillOwnsAttempt = false,
                maceFightBotReservation = true,
                spearKillOwnsAttempt = false,
                spearFightBotReservation = false,
                distantSpearKillTarget = false,
            ),
        )
    }

    @Test
    fun `SpearKill keeps its existing precharge suppression semantics`() {
        assertEquals(
            KillAuraAttackRoute.SPEAR_KILL,
            selectKillAuraSuppressionRoute(
                maceKillOwnsAttempt = false,
                maceFightBotReservation = false,
                spearKillOwnsAttempt = false,
                spearFightBotReservation = false,
                distantSpearKillTarget = true,
            ),
        )
    }

    @Test
    fun `MaceKill expands target acquisition range only while available`() {
        assertEquals(
            7f,
            calculateKillAuraTargetingRange(
                delegateKillAuraAttacks = false,
                normalMaximumRange = 7f,
                reachHitAvailable = false,
                reachHitMaximumRange = 100f,
                maceKillRunning = true,
                maceKillMaximumRange = 400f,
            ),
        )
        assertEquals(
            400f,
            calculateKillAuraTargetingRange(
                delegateKillAuraAttacks = true,
                normalMaximumRange = 7f,
                reachHitAvailable = true,
                reachHitMaximumRange = 100f,
                spearKillRunning = true,
                spearKillMaximumRange = 300f,
                maceKillRunning = true,
                maceKillMaximumRange = 400f,
            ),
        )
        assertEquals(
            300f,
            calculateKillAuraTargetingRange(
                delegateKillAuraAttacks = true,
                normalMaximumRange = 7f,
                reachHitAvailable = true,
                reachHitMaximumRange = 100f,
                spearKillRunning = true,
                spearKillMaximumRange = 300f,
                maceKillRunning = false,
                maceKillMaximumRange = 400f,
            ),
        )
    }

    @Test
    fun `MaceKill route never invokes KillAura attacks or success bookkeeping`() = runTest {
        var successfulAttacks = 0

        val success = executeKillAuraAttack(
            route = KillAuraAttackRoute.MACE_KILL,
            normalAttack = { error("normal attack must remain suppressed") },
            reachHitAttack = { error("Reach Hit must remain suppressed") },
            onSuccess = { successfulAttacks++ },
        )

        assertFalse(success)
        assertEquals(0, successfulAttacks)
    }

    @Test
    fun `MaceKill route explicitly launches once without a KillAura click`() {
        var launches = 0

        val started = dispatchKillAuraRemoteKillRoute(KillAuraAttackRoute.MACE_KILL) {
            launches++
            true
        }

        assertTrue(started)
        assertEquals(1, launches)
    }

    @Test
    fun `failed MaceKill launch immediately falls back to the next attack route`() {
        var launches = 0
        var fallbacks = 0

        val resolved = resolveKillAuraMaceLaunch(
            selectedRoute = KillAuraAttackRoute.MACE_KILL,
            launchMaceKill = {
                launches++
                false
            },
            fallbackRoute = {
                fallbacks++
                KillAuraAttackRoute.NORMAL
            },
        )

        assertEquals(KillAuraAttackRoute.NORMAL, resolved)
        assertEquals(1, launches)
        assertEquals(1, fallbacks)
    }

    @Test
    fun `successful MaceKill launch never evaluates a fallback route`() {
        val resolved = resolveKillAuraMaceLaunch(
            selectedRoute = KillAuraAttackRoute.MACE_KILL,
            launchMaceKill = { true },
            fallbackRoute = { error("fallback must not run after route ownership transfers") },
        )

        assertEquals(KillAuraAttackRoute.MACE_KILL, resolved)
    }

    @Test
    fun `non MaceKill routes never invoke the MaceKill launcher`() {
        listOf(
            KillAuraAttackRoute.NONE,
            KillAuraAttackRoute.NORMAL,
            KillAuraAttackRoute.SPEAR_KILL,
            KillAuraAttackRoute.REACH_HIT,
        ).forEach { route ->
            assertFalse(
                dispatchKillAuraRemoteKillRoute(route) {
                    error("MaceKill launcher must remain isolated")
                },
            )
        }
    }

    private fun selectRemoteRoute(
        heldRemoteWeapon: KillAuraRemoteWeapon,
        maceKillAvailable: Boolean = true,
        maceKillTargetPossible: Boolean = true,
        spearKillAvailable: Boolean = true,
        spearKillTargetPossible: Boolean = true,
    ) = selectKillAuraRemoteKillRoute(
        delegateKillAuraAttacks = true,
        normalAttackPossible = false,
        heldRemoteWeapon = heldRemoteWeapon,
        maceKillAvailable = maceKillAvailable,
        maceKillTargetPossible = maceKillTargetPossible,
        spearKillAvailable = spearKillAvailable,
        spearKillTargetPossible = spearKillTargetPossible,
        reachHitAvailable = true,
        reachHitTargetPossible = true,
    )
}
