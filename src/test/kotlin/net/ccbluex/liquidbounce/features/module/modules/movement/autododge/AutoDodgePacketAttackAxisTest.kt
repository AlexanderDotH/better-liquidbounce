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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AutoDodgePacketAttackAxisTest {

    @Test
    fun `projectile lateral plan follows its trajectory axis`() {
        val axis = assertNotNull(
            AutoDodgePacketProjectileThreat(
                entityId = 1,
                tickDelta = 0,
                previousPosition = Vec3.ZERO,
                velocity = Vec3(2.0, -0.5, 0.0),
            ).toPacketAttackAxis(),
        )

        assertEquals(AutoDodgePacketThreatType.PROJECTILE, axis.threatType)
        assertEquals(Vec3(2.0, 64.0, SAFE_DISTANCE), plan(axis, Vec3(2.0, 64.0, 0.0)))
    }

    @Test
    fun `future projectile exposes its trajectory axis for precomputation`() {
        val axis = assertNotNull(
            AutoDodgePacketProjectileThreat(
                entityId = 1,
                tickDelta = 8,
                previousPosition = Vec3.ZERO,
                velocity = Vec3(1.0, 0.0, 0.0),
            ).toPacketAttackAxis(),
        )

        assertEquals(AutoDodgePacketThreatType.PROJECTILE, axis.threatType)
    }

    @Test
    fun `mace lateral plan uses attacker to player direction`() {
        val playerOrigin = Vec3(2.0, 64.0, 0.0)
        val axis = maceThreat(Vec3.ZERO).toPacketAttackAxis(playerOrigin)

        assertEquals(AutoDodgePacketThreatType.MACE, axis.threatType)
        assertEquals(Vec3(2.0, 64.0, SAFE_DISTANCE), plan(axis, playerOrigin))
    }

    @Test
    fun `trusted spear lateral plan uses attacker aim`() {
        val playerOrigin = Vec3(2.0, 64.0, 0.0)
        val axis = spearThreat(
            position = Vec3.ZERO,
            eyePosition = Vec3(0.0, 1.62, 0.0),
            lookDirection = Vec3(1.0, -0.2, 0.0),
            kind = SpearThreatKind.HOLDING_AIMED,
            response = SpearThreatResponse.FEINT,
        ).toPacketAttackAxis(playerOrigin)

        assertEquals(AutoDodgePacketThreatType.SPEAR, axis.threatType)
        assertEquals(Vec3(2.0, 64.0, SAFE_DISTANCE), plan(axis, playerOrigin))
    }

    @Test
    fun `untrusted monitor-level spear lateral plan uses attacker to player direction`() {
        val playerOrigin = Vec3(2.0, 64.0, 0.0)
        val axis = spearThreat(
            position = Vec3.ZERO,
            eyePosition = Vec3(0.0, 1.62, 0.0),
            lookDirection = Vec3(0.0, 0.0, 1.0),
            kind = SpearThreatKind.HOLDING_NEWLY_VISIBLE,
            response = SpearThreatResponse.MONITOR,
        ).toPacketAttackAxis(playerOrigin)

        assertEquals(Vec3(2.0, 64.0, SAFE_DISTANCE), plan(axis, playerOrigin))
    }

    @Test
    fun `degenerate trusted spear aim falls back to attacker to player direction`() {
        val playerOrigin = Vec3(2.0, 64.0, 0.0)
        val axis = spearThreat(
            position = Vec3.ZERO,
            eyePosition = Vec3(0.0, 1.62, 0.0),
            lookDirection = Vec3.ZERO,
            kind = SpearThreatKind.HOLDING_AIMED,
            response = SpearThreatResponse.FEINT,
        ).toPacketAttackAxis(playerOrigin)

        assertEquals(Vec3(2.0, 64.0, SAFE_DISTANCE), plan(axis, playerOrigin))
    }

    private fun plan(axis: AutoDodgePacketAttackAxis, playerOrigin: Vec3) = AutoDodgePacketPlanner.plan(
        origin = playerOrigin,
        attackAxisOrigin = axis.origin,
        attackAxisDirection = axis.direction,
        fallbackDirection = axis.fallbackDirection,
        isSafe = { true },
    )

    private fun maceThreat(position: Vec3) = MaceThreat(
        candidate = MaceThreatCandidate(
            entityId = 1,
            name = "mace",
            position = position,
            lookDirection = Vec3.ZERO,
            isHoldingMace = true,
        ),
        kind = MaceThreatKind.PACKET_CAPABLE,
        distanceSquared = 4.0,
    )

    private fun spearThreat(
        position: Vec3,
        eyePosition: Vec3,
        lookDirection: Vec3,
        kind: SpearThreatKind,
        response: SpearThreatResponse,
    ) = SpearThreat(
        candidate = SpearThreatCandidate(
            entityId = 2,
            name = "spear",
            position = position,
            eyePosition = eyePosition,
            lookDirection = lookDirection,
            isHoldingSpear = true,
            isUsingSpear = false,
        ),
        kind = kind,
        response = response,
        distanceSquared = 4.0,
    )

    private companion object {
        const val SAFE_DISTANCE = DodgePlanner.SAFE_DISTANCE_WITH_PADDING
    }
}
