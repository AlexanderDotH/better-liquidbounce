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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class MaceClipReachPlannerTest {

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

    private fun assertPhaseContract(plan: MaceClipReachPlan) {
        assertEquals(
            listOf(
                MaceClipReachEvidencePhase.PRIME,
                MaceClipReachEvidencePhase.ASCEND,
                MaceClipReachEvidencePhase.TRANSFER,
                MaceClipReachEvidencePhase.DESCEND,
                MaceClipReachEvidencePhase.STRIKE,
                MaceClipReachEvidencePhase.RETURN_ASCEND,
                MaceClipReachEvidencePhase.RETURN_TRANSFER,
                MaceClipReachEvidencePhase.RETURN_DESCEND,
            ),
            plan.steps.map(MaceClipReachStep::evidencePhase),
        )
        assertEquals(
            listOf(
                MaceClipReachPhase.PRIME,
                MaceClipReachPhase.ASCEND,
                MaceClipReachPhase.TRANSFER,
                MaceClipReachPhase.DESCEND,
                MaceClipReachPhase.STRIKE,
                MaceClipReachPhase.ASCEND,
                MaceClipReachPhase.RETURN,
                MaceClipReachPhase.DESCEND,
            ),
            plan.steps.map(MaceClipReachStep::phase),
        )
        assertEquals(
            listOf(
                MaceClipReachLeg.PREPARATION,
                MaceClipReachLeg.OUTBOUND,
                MaceClipReachLeg.OUTBOUND,
                MaceClipReachLeg.OUTBOUND,
                MaceClipReachLeg.ATTACK,
                MaceClipReachLeg.RETURN,
                MaceClipReachLeg.RETURN,
                MaceClipReachLeg.RETURN,
            ),
            plan.steps.map(MaceClipReachStep::leg),
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

    private fun request(
        origin: Vec3 = Vec3(0.0, 64.0, 0.0),
        endpoint: Vec3 = Vec3(30.0, 64.0, 0.0),
        bounds: MaceClipReachDimensionBounds = MaceClipReachDimensionBounds(-64.0, 320.0),
        profile: MaceClipReachProfile = validatedProfile(),
        use: MaceClipReachUse = MaceClipReachUse.NORMAL,
        anchorValidator: MaceClipReachAnchorValidator = MaceClipReachAnchorValidator { _, _ -> true },
    ) = MaceClipReachPlanRequest(
        origin = origin,
        endpoint = endpoint,
        dimensionBounds = bounds,
        profile = profile,
        use = use,
        anchorValidator = anchorValidator,
    )

    private fun readyPlan(request: MaceClipReachPlanRequest): MaceClipReachPlan = assertInstanceOf(
        MaceClipReachPlanResult.Ready::class.java,
        MaceClipReachPlanner.plan(request),
    ).plan

    private fun assertBlocked(request: MaceClipReachPlanRequest, reason: MaceClipReachBlockReason) {
        val blocked = assertInstanceOf(
            MaceClipReachPlanResult.Blocked::class.java,
            MaceClipReachPlanner.plan(request),
        )
        assertEquals(reason, blocked.reason)
    }

    private fun validatedProfile(
        parameters: MaceClipReachResearchParameters = parameters(),
    ) = MaceClipReachProfileTest.validatedProfile(parameters)

    @Suppress("LongParameterList")
    private fun parameters(
        primingPacketCount: Int = 9,
        clearanceHeight: Double = 99.0,
        maxTargetDistance: Double = 500.0,
        maxMovementPackets: Int = 128,
        timeoutTicks: Int = 40,
    ) = MaceClipReachResearchParameters(
        primingPacketCount = primingPacketCount,
        clearanceHeight = clearanceHeight,
        maxTargetDistance = maxTargetDistance,
        maxMovementPackets = maxMovementPackets,
        timeoutTicks = timeoutTicks,
    )
}
