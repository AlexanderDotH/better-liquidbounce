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

class MaceClipValidatedPlanEmitsBoundedEightPhaseClipTest {

    @Test
    fun `validated plan emits the bounded eight-phase clip and exact inverse`() {
        val origin = Vec3(10.0, 64.0, -4.0)
        val endpoint = Vec3(34.0, 70.0, 8.0)

        val plan = readyPlan(request(origin = origin, endpoint = endpoint))

        val horizontalDistanceSquared = 24.0 * 24.0 + 12.0 * 12.0
        val allowance = plan.profile.parameters.clearanceHeight
        val originApex = Vec3(10.0, origin.y + allowance, -4.0)
        val endpointApex = Vec3(
            34.0,
            origin.y + sqrt(allowance * allowance - horizontalDistanceSquared),
            8.0,
        )
        assertPhaseContract(plan)
        assertEquals(
            listOf(
                origin,
                originApex,
                endpointApex,
                endpoint,
                endpoint,
                endpointApex.add(0.0, 0.01, 0.0),
                originApex.add(0.0, 0.01, 0.0),
                origin,
            ),
            plan.steps.map(MaceClipReachStep::position),
        )
        assertEquals(originApex.subtract(origin), plan.outboundMovements.first())
        assertEquals(endpointApex, plan.outboundMovements.take(2).fold(origin, Vec3::add))
        assertEquals(allowance, origin.distanceTo(originApex), 1.0E-9)
        assertEquals(allowance, origin.distanceTo(endpointApex), 1.0E-9)
        assertEquals(endpoint, plan.outboundMovements.fold(origin, Vec3::add))
        assertEquals(origin, plan.returnMovements.fold(plan.endpoint, Vec3::add))
        assertNotEquals(plan.outboundMovements.asReversed().map { it.scale(-1.0) }, plan.returnMovements)
        assertEquals(plan.requiredMovementPackets, plan.steps.sumOf { it.packetCount })
        assertEquals(origin, plan.finalPosition)

        val routeRequest = RemoteKillRouteRequest(
            origin = plan.origin,
            outboundMovements = plan.outboundMovements,
            returnMovements = plan.returnMovements,
        )
        assertEquals(plan.endpoint, routeRequest.endpoint)
        assertEquals(plan.returnMovements, routeRequest.returnMovements)
    }

    @Test
    fun `ground spoof uses one endpoint descent so no intermediate packet lands inside a ceiling`() {
        val plan = readyPlan(
            request(
                origin = Vec3(0.0, 64.0, 0.0),
                endpoint = Vec3(30.0, 64.0, 0.0),
            ),
        )

        val outboundDescents = plan.outboundMovements.drop(2)
        val returnDescents = plan.returnMovements.takeLast(1)
        assertEquals(1, outboundDescents.size)
        assertEquals(1, returnDescents.size)
        val expectedDescent = -sqrt(99.0 * 99.0 - 30.0 * 30.0)
        assertEquals(0.0, outboundDescents.single().x, 1.0E-9)
        assertEquals(expectedDescent, outboundDescents.single().y, 1.0E-9)
        assertEquals(0.0, outboundDescents.single().z, 1.0E-9)
        assertEquals(-99.01, returnDescents.single().y, 1.0E-9)
        assertEquals(15, plan.requiredMovementPackets)
        assertEquals(plan.requiredMovementPackets, plan.profile.parameters.primingPacketCount +
            plan.outboundMovements.size + plan.returnMovements.size)
    }

    @Test
    fun `correction recovery climbs to the retained apex and returns safely through the sealed route`() {
        val authoritativePosition = Vec3(163.3, 214.0, 160.5)
        val origin = Vec3(170.5, 127.0, 160.5)

        val result = MaceClipReachPlanner.planCorrectionRecovery(
            MaceClipReachRecoveryRequest(
                authoritativePosition = authoritativePosition,
                origin = origin,
                preferredApexY = 226.0,
                dimensionBounds = MaceClipReachDimensionBounds(-64.0, 320.0),
                maxMovementPackets = 128,
                anchorValidator = MaceClipReachAnchorValidator { _, _ -> true },
            ),
        )
        val movements = assertInstanceOf(MaceClipReachRecoveryResult.Ready::class.java, result).movements

        assertTrue(movements.take(4).all { it == Vec3(0.0, 3.0, 0.0) })
        assertEquals(7.2, movements[4].x, 1.0E-12)
        assertEquals(0.0, movements[4].y, 1.0E-12)
        assertEquals(0.0, movements[4].z, 1.0E-12)
        assertEquals(38, movements.size)
        assertTrue(movements.takeLast(33).all { it == Vec3(0.0, -3.0, 0.0) })
        assertEquals(origin, movements.fold(authoritativePosition, Vec3::add))
    }

    @Test
    fun `correction recovery rejects an invalid apex and an exhausted packet budget`() {
        val request = MaceClipReachRecoveryRequest(
            authoritativePosition = Vec3(163.3, 214.0, 160.5),
            origin = Vec3(170.5, 127.0, 160.5),
            preferredApexY = 226.0,
            dimensionBounds = MaceClipReachDimensionBounds(-64.0, 320.0),
            maxMovementPackets = 34,
            anchorValidator = MaceClipReachAnchorValidator { _, _ -> true },
        )

        assertEquals(
            MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED,
            (MaceClipReachPlanner.planCorrectionRecovery(request) as MaceClipReachRecoveryResult.Blocked).reason,
        )
        assertEquals(
            MaceClipReachBlockReason.INVALID_APEX,
            (MaceClipReachPlanner.planCorrectionRecovery(
                request.copy(
                    maxMovementPackets = 128,
                    anchorValidator = MaceClipReachAnchorValidator { role, _ ->
                        role != MaceClipReachPositionRole.ORIGIN_APEX
                    },
                ),
            ) as MaceClipReachRecoveryResult.Blocked).reason,
        )
    }

    @Test
    fun `unvalidated reference profile is admitted only for explicit experimental or research requests`() {
        val normal = request(profile = MaceClipReachProfile.REFERENCE_UNVALIDATED)
        val experimental = normal.copy(use = MaceClipReachUse.EXPERIMENTAL)
        val research = normal.copy(use = MaceClipReachUse.RESEARCH)

        assertBlocked(normal, MaceClipReachBlockReason.PROFILE_NOT_VALIDATED)
        assertInstanceOf(MaceClipReachPlanResult.Ready::class.java, MaceClipReachPlanner.plan(experimental))
        assertInstanceOf(MaceClipReachPlanResult.Ready::class.java, MaceClipReachPlanner.plan(research))
    }

    @Test
    fun `experimental use never admits a structurally invalid unvalidated profile`() {
        val invalidProfile = MaceClipReachProfile.experimental(
            parameters(maxMovementPackets = 0),
        )

        assertBlocked(
            request(profile = invalidProfile, use = MaceClipReachUse.EXPERIMENTAL),
            MaceClipReachBlockReason.INVALID_PROFILE,
        )
    }
}
