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

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import kotlinx.coroutines.test.runTest
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.shouldExcludeMaceKillWaterTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KillAuraHeldAutoMaceExcludesWaterTargetsInsteadFallingTest {

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
}
