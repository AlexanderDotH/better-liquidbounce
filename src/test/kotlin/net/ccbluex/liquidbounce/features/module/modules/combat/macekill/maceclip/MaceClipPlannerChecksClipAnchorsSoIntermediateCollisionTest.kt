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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip
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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
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

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class MaceClipPlannerChecksClipAnchorsSoIntermediateCollisionTest {

    @Test
    fun `planner checks only clip anchors so intermediate collision bypass cannot leak into other routes`() {
        val validatedRoles = mutableListOf<MaceClipReachPositionRole>()
        val request = request(
            anchorValidator = MaceClipReachAnchorValidator { role, _ ->
                validatedRoles += role
                true
            },
        )

        readyPlan(request)

        assertEquals(
            listOf(
                MaceClipReachPositionRole.ORIGIN,
                MaceClipReachPositionRole.ORIGIN_APEX,
                MaceClipReachPositionRole.TARGET_APEX,
                MaceClipReachPositionRole.ENDPOINT,
                MaceClipReachPositionRole.FINAL,
            ),
            validatedRoles,
        )
    }

    @Test
    fun `each invalid anchor fails closed with its role-specific reason`() {
        val reasons = mapOf(
            MaceClipReachPositionRole.ORIGIN to MaceClipReachBlockReason.INVALID_ORIGIN,
            MaceClipReachPositionRole.ORIGIN_APEX to MaceClipReachBlockReason.INVALID_APEX,
            MaceClipReachPositionRole.TARGET_APEX to MaceClipReachBlockReason.INVALID_APEX,
            MaceClipReachPositionRole.ENDPOINT to MaceClipReachBlockReason.INVALID_ENDPOINT,
            MaceClipReachPositionRole.FINAL to MaceClipReachBlockReason.INVALID_FINAL_POSITION,
        )

        reasons.forEach { (blockedRole, expectedReason) ->
            assertBlocked(
                request(anchorValidator = MaceClipReachAnchorValidator { role, _ -> role != blockedRole }),
                expectedReason,
            )
        }
    }

    @Test
    fun `dimension target distance and packet budget limits reject the whole plan`() {
        assertBlocked(
            request(bounds = MaceClipReachDimensionBounds(0.0, 80.0)),
            MaceClipReachBlockReason.OUT_OF_DIMENSION,
        )
        assertBlocked(
            request(endpoint = Vec3(501.0, 64.0, 0.0)),
            MaceClipReachBlockReason.DISTANCE_EXCEEDED,
        )
        assertBlocked(
            request(profile = validatedProfile(parameters(maxMovementPackets = 14))),
            MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED,
        )
        assertBlocked(
            request(endpoint = Vec3(0.0, 90.0, 0.0)),
            MaceClipReachBlockReason.HORIZONTAL_DISTANCE_REQUIRED,
        )
    }

    @Test
    fun `target distance admits floating point noise but rejects meaningful excess`() {
        val profile = validatedProfile(parameters(maxTargetDistance = 50.0))
        readyPlan(request(endpoint = Vec3(50.0 + 0.0000005, 64.0, 0.0), profile = profile))

        assertBlocked(
            request(endpoint = Vec3(50.0 + 0.000002, 64.0, 0.0), profile = profile),
            MaceClipReachBlockReason.DISTANCE_EXCEEDED,
        )
    }

    @Test
    fun `experimental transfer rejects targets outside the primed movement sphere`() {
        assertBlocked(
            request(endpoint = Vec3(100.0, 64.0, 0.0)),
            MaceClipReachBlockReason.DISTANCE_EXCEEDED,
        )
    }

    @Test
    fun `experimental route keeps both transfer anchors inside the primed movement sphere`() {
        val origin = Vec3(0.0, 64.0, 0.0)
        val endpoint = Vec3(50.0, 64.0, 0.0)

        val plan = readyPlan(request(origin = origin, endpoint = endpoint))
        val originApex = plan.steps.first { it.evidencePhase == MaceClipReachEvidencePhase.ASCEND }.position
        val targetApex = plan.steps.first { it.evidencePhase == MaceClipReachEvidencePhase.TRANSFER }.position

        assertEquals(origin.y + plan.profile.parameters.clearanceHeight, originApex.y, 1.0E-9)
        assertEquals(plan.profile.parameters.clearanceHeight, origin.distanceTo(targetApex), 1.0E-9)
    }

    @Test
    fun `vertical target is rejected when its endpoint descent cannot fit the primed allowance`() {
        assertBlocked(
            request(
                origin = Vec3(0.0, 101.0, 0.0),
                endpoint = Vec3(70.0, 53.0, 0.0),
            ),
            MaceClipReachBlockReason.DISTANCE_EXCEEDED,
        )
    }

    @Test
    fun `airborne origin admits a nearby endpoint slightly below the primed sphere`() {
        val plan = readyPlan(
            request(
                origin = Vec3(0.0, 104.9, 0.0),
                endpoint = Vec3(13.0, 102.0, 0.0),
            ),
        )

        assertEquals(plan.origin, plan.returnMovements.fold(plan.endpoint, Vec3::add))
        assertTrue(plan.outboundMovements.last().length() < 115.0)
    }

    @Test
    fun `malformed coordinates bounds and research constants fail closed`() {
        val invalidRequests = listOf(
            request(origin = Vec3(Double.NaN, 64.0, 0.0)),
            request(endpoint = Vec3(Double.POSITIVE_INFINITY, 64.0, 0.0)),
            request(bounds = MaceClipReachDimensionBounds(100.0, 100.0)),
            request(profile = validatedProfile(parameters(primingPacketCount = -1))),
            request(profile = validatedProfile(parameters(clearanceHeight = 0.0))),
            request(profile = validatedProfile(parameters(maxTargetDistance = Double.NaN))),
            request(profile = validatedProfile(parameters(maxMovementPackets = 0))),
            request(profile = validatedProfile(parameters(timeoutTicks = 0))),
        )

        invalidRequests.forEach { invalid ->
            val result = MaceClipReachPlanner.plan(invalid)
            assertTrue(result is MaceClipReachPlanResult.Blocked, invalid.toString())
        }
    }
}
