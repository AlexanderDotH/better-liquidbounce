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
import java.nio.file.Files
import java.nio.file.Path

class KillAuraMaceKillOwnershipSuppressesConflictingSubsystemsTest {

    @Test
    fun `KillAura orchestration stays explicit without structural suppressions`() {
        val module = source("ModuleKillAura.kt")
        val clicker = source("KillAuraClicker.kt")
        val autoBlock = source("features/KillAuraAutoBlock.kt")

        listOf(module, clicker, autoBlock).forEach { source ->
            assertFalse(source.contains("CognitiveComplexMethod"))
        }
        assertFalse(module.contains("TooManyFunctions"))
        assertFalse(module.contains("LargeClass"))

        assertOrdered(
            clicker,
            "prepareBlockingState()",
            "network.sendCloseInventory()",
            "sendAttackRotation(rotation)",
            "clickSuspending(attack)",
            "restoreRotation(rotation)",
            "KillAuraAutoBlock.startBlocking()",
            "network.sendLegacyOpenInventory()",
        )
        assertOrdered(
            module,
            "KillAuraAutoBlock.makeSeemBlock()",
            "clicker.prepareForAttack(rotation)",
            "executeKillAuraAttack(",
            "range.update()",
            "KillAuraAutoBlock.hasBlockedSinceAttack = false",
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

    private fun source(relativePath: String): String = Files.readString(SOURCE_ROOT.resolve(relativePath))

    private fun assertOrdered(source: String, vararg fragments: String) {
        var previous = -1
        fragments.forEach { fragment ->
            val index = source.indexOf(fragment)
            assertTrue(index > previous, "$fragment must remain after the preceding KillAura step")
            previous = index
        }
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/killaura",
        )
    }
}
