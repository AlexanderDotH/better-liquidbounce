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
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoDodgePacketSelectionTest {

    @Test
    fun `imminent projectile takes precedence without evaluating lower priority threats`() {
        val evaluated = mutableListOf<String>()

        val selected = selectAutoDodgePacketCandidate(
            projectile = {
                evaluated += "projectile"
                "projectile"
            },
            mace = {
                evaluated += "mace"
                "mace"
            },
            spear = {
                evaluated += "spear"
                "spear"
            },
        )

        assertEquals("projectile", selected)
        assertEquals(listOf("projectile"), evaluated)
    }

    @Test
    fun `unsafe projectile falls through to mace before spear`() {
        val evaluated = mutableListOf<String>()

        val selected = selectAutoDodgePacketCandidate(
            projectile = {
                evaluated += "projectile"
                null
            },
            mace = {
                evaluated += "mace"
                "mace"
            },
            spear = {
                evaluated += "spear"
                "spear"
            },
        )

        assertEquals("mace", selected)
        assertEquals(listOf("projectile", "mace"), evaluated)
    }

    @Test
    fun `future projectile remains armed until its predicted dodge tick`() {
        val schedule = requireNotNull(
            AutoDodgePacketProjectileThreat(
                entityId = 9,
                tickDelta = 5,
                previousPosition = net.minecraft.world.phys.Vec3.ZERO,
                velocity = net.minecraft.world.phys.Vec3(1.0, 0.0, 0.0),
            ).predictImpact(observedAtTick = 20, postImpactHoldTicks = 2)
        )

        assertTrue(schedule.dodgeAtTick > 20)
        assertFalse(schedule.isDodgeDue(20))
    }

    @Test
    fun `future projectile does not block a mace whose impact window is due now`() {
        val projectile = candidate(
            AutoDodgePacketThreatType.PROJECTILE,
            entityId = 1,
            dodgeAtTick = 15,
        )
        val mace = candidate(
            AutoDodgePacketThreatType.MACE,
            entityId = 2,
            dodgeAtTick = 10,
        )

        assertEquals(mace, selectDueAutoDodgePacketCandidate(listOf(projectile, mace), tick = 10))
    }

    @Test
    fun `due projectile keeps priority over due melee threats`() {
        val projectile = candidate(AutoDodgePacketThreatType.PROJECTILE, entityId = 1, dodgeAtTick = 10)
        val mace = candidate(AutoDodgePacketThreatType.MACE, entityId = 2, dodgeAtTick = 10)
        val spear = candidate(AutoDodgePacketThreatType.SPEAR, entityId = 3, dodgeAtTick = 10)

        assertEquals(
            projectile,
            selectDueAutoDodgePacketCandidate(listOf(spear, mace, projectile), tick = 10),
        )
    }

    @Test
    fun `earliest future collision-safe candidate is armed deterministically`() {
        val laterProjectile = candidate(
            AutoDodgePacketThreatType.PROJECTILE,
            entityId = 8,
            dodgeAtTick = 18,
        )
        val earlierProjectile = candidate(
            AutoDodgePacketThreatType.PROJECTILE,
            entityId = 7,
            dodgeAtTick = 15,
        )

        assertEquals(
            earlierProjectile,
            selectArmedAutoDodgePacketCandidate(listOf(laterProjectile, earlierProjectile), tick = 10),
        )
    }

    @Test
    fun `Packet mode has no local movement or Movement fallback surface`() {
        val moduleSource = Files.readString(MODULE_SOURCE)
        val defenseSource = Files.readString(DEFENSE_RUNTIME_SOURCE)
        val packetUpdate = bracedDeclaration(defenseSource, "private fun updatePacketDefense(")
        val packetSuppression = expressionDeclaration(defenseSource, "fun shouldSuppressPacket(")
        val holdHandler = bracedDeclaration(moduleSource, "private val packetHoldHandler")
        val packetController = Files.readString(PACKET_CONTROLLER_SOURCE)
        val packetSurface = packetUpdate + packetSuppression + holdHandler + packetController

        assertTrue(packetUpdate.contains("updateThreatOnly("))
        assertTrue(packetSuppression.contains("shouldSuppressAutoDodgePacketHoldMovement("))
        assertTrue(holdHandler.contains("defense.shouldSuppressPacket(event)"))
        assertTrue(holdHandler.contains("event.cancelEvent()"))
        assertTrue(packetController.contains("toPacketThreatPrediction("))
        assertTrue(packetController.contains("selectArmedAutoDodgePacketCandidate("))
        assertTrue(packetController.contains("runtime.extendHold("))
        assertFalse(packetController.contains("requiresJuke"), "Every detected spear threat must remain eligible")
        FORBIDDEN_PACKET_MODE_OPERATIONS.forEach { operation ->
            assertFalse(packetSurface.contains(operation), "Packet mode must not use $operation")
        }
    }

    @Test
    fun `Movement mode retains the existing projectile spear mace and action pipeline`() {
        val source = Files.readString(DEFENSE_RUNTIME_SOURCE)
        val movementUpdate = bracedDeclaration(source, "private fun updateMovementDefense(")

        listOf(
            "planEvasion(",
            "spearMovementController.update(",
            "maceMovementController.update(",
            "AutoDodgeMovementArbitrator.chooseAction(",
            "AutoDodgeMovementExecutor.execute(",
        ).forEach { operation ->
            assertTrue(movementUpdate.contains(operation), "Movement mode lost $operation")
        }
    }

    private fun bracedDeclaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing declaration: $marker" }
        val bodyStart = source.indexOf('{', markerIndex)
        require(bodyStart >= 0) { "Missing declaration body: $marker" }

        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(markerIndex, index + 1)
            }
        }
        error("Unclosed declaration: $marker")
    }

    private fun expressionDeclaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing declaration: $marker" }
        val declarationEnd = source.indexOf("\n\n", markerIndex)
        require(declarationEnd >= 0) { "Missing declaration end: $marker" }
        return source.substring(markerIndex, declarationEnd)
    }

    private fun candidate(
        threatType: AutoDodgePacketThreatType,
        entityId: Int,
        dodgeAtTick: Long,
    ) = AutoDodgePacketCandidate(
        threatKey = AutoDodgePacketThreatKey(threatType, entityId),
        impactSchedule = AutoDodgePacketImpactSchedule(
            predictedImpactTick = dodgeAtTick + AUTO_DODGE_PACKET_IMPACT_LEAD_TICKS,
            dodgeAtTick = dodgeAtTick,
            returnNotBeforeTick = dodgeAtTick + AUTO_DODGE_PACKET_IMPACT_LEAD_TICKS + 2,
        ),
        destination = Vec3(entityId.toDouble(), 64.0, 0.0),
    )

    private companion object {
        val MODULE_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/autododge/ModuleAutoDodge.kt",
        )
        val DEFENSE_RUNTIME_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/autododge/" +
                "AutoDodgeDefenseRuntime.kt",
        )
        val PACKET_CONTROLLER_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/autododge/" +
                "AutoDodgePacketController.kt",
        )
        val FORBIDDEN_PACKET_MODE_OPERATIONS = listOf(
            "MovementInputEvent",
            "updateMovementDefense(",
            "performSpearTeleport(",
            "performMaceTeleport(",
            "setPos(",
            "deltaMovement",
            "directionalInput",
            "Timer.requestTimerSpeed",
            "once<MovementInputEvent>",
            ".yRot",
        )
    }
}
