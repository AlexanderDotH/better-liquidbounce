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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KillAuraSpearKillRouteTest {

    @Test
    fun `normal route has first preference`() {
        assertEquals(
            KillAuraAttackRoute.NORMAL,
            selectKillAuraSpearKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = true,
                spearKillRunning = true,
                spearKillTargetPossible = true,
                superHitAvailable = true,
                superHitTargetPossible = true,
            ),
        )
        assertTrue(killAuraAttackRoutePriority(9.0, 16.0) < killAuraAttackRoutePriority(100.0, 16.0))
    }

    @Test
    fun `SpearKill route takes precedence over SuperHit without a normal attack`() {
        assertEquals(
            KillAuraAttackRoute.SPEAR_KILL,
            selectKillAuraSpearKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                spearKillRunning = true,
                spearKillTargetPossible = true,
                superHitAvailable = true,
                superHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `SuperHit route is selected only when SpearKill cannot own the attempt`() {
        assertEquals(
            KillAuraAttackRoute.SUPER_HIT,
            selectKillAuraSpearKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                spearKillRunning = false,
                spearKillTargetPossible = true,
                superHitAvailable = true,
                superHitTargetPossible = true,
            ),
        )
        assertEquals(
            KillAuraAttackRoute.SUPER_HIT,
            selectKillAuraSpearKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                spearKillRunning = true,
                spearKillTargetPossible = false,
                superHitAvailable = true,
                superHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `disabled global delegation rejects every replacement route`() {
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraSpearKillRoute(
                delegateKillAuraAttacks = false,
                normalAttackPossible = false,
                spearKillRunning = true,
                spearKillTargetPossible = true,
                superHitAvailable = true,
                superHitTargetPossible = true,
            ),
        )
    }

    @Test
    fun `none route is selected when no attack owner is available`() {
        assertEquals(
            KillAuraAttackRoute.NONE,
            selectKillAuraSpearKillRoute(
                delegateKillAuraAttacks = true,
                normalAttackPossible = false,
                spearKillRunning = false,
                spearKillTargetPossible = false,
                superHitAvailable = false,
                superHitTargetPossible = false,
            ),
        )
    }

    @Test
    fun `SpearKill ownership suppresses every conflicting KillAura subsystem`() {
        val policy = selectKillAuraSpearKillSuppressionPolicy(KillAuraAttackRoute.SPEAR_KILL)

        assertTrue(policy.suppressClicker)
        assertTrue(policy.suppressAutoBlock)
        assertTrue(policy.suppressAutoWeapon)
    }

    @Test
    fun `non SpearKill routes leave KillAura subsystems enabled`() {
        listOf(
            KillAuraAttackRoute.NORMAL,
            KillAuraAttackRoute.SUPER_HIT,
            KillAuraAttackRoute.NONE,
        ).forEach { route ->
            val policy = selectKillAuraSpearKillSuppressionPolicy(route)

            assertFalse(policy.suppressClicker)
            assertFalse(policy.suppressAutoBlock)
            assertFalse(policy.suppressAutoWeapon)
        }
    }

    @Test
    fun `SpearKill precharges only after selection and never over a normal target`() {
        assertFalse(shouldPrechargeKillAuraSpear(
            acquisitionAvailable = true,
            targetSelectionEvaluated = false,
            hasTrackedTarget = false,
            trackedTargetUsesSpearKill = false,
        ))
        assertTrue(shouldPrechargeKillAuraSpear(
            acquisitionAvailable = true,
            targetSelectionEvaluated = true,
            hasTrackedTarget = false,
            trackedTargetUsesSpearKill = false,
        ))
        assertFalse(shouldPrechargeKillAuraSpear(
            acquisitionAvailable = true,
            targetSelectionEvaluated = true,
            hasTrackedTarget = true,
            trackedTargetUsesSpearKill = false,
        ))
        assertTrue(shouldPrechargeKillAuraSpear(
            acquisitionAvailable = true,
            targetSelectionEvaluated = true,
            hasTrackedTarget = true,
            trackedTargetUsesSpearKill = true,
        ))
        assertFalse(shouldPrechargeKillAuraSpear(
            acquisitionAvailable = false,
            targetSelectionEvaluated = true,
            hasTrackedTarget = false,
            trackedTargetUsesSpearKill = false,
        ))
    }
}
