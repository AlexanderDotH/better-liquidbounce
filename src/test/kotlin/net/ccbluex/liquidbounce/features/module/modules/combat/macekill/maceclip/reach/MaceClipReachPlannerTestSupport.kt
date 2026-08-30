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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*

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

internal fun assertPhaseContract(plan: MaceClipReachPlan) {
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












internal fun request(
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

internal fun readyPlan(request: MaceClipReachPlanRequest): MaceClipReachPlan = assertInstanceOf(
    MaceClipReachPlanResult.Ready::class.java,
    MaceClipReachPlanner.plan(request),
).plan

internal fun assertBlocked(request: MaceClipReachPlanRequest, reason: MaceClipReachBlockReason) {
    val blocked = assertInstanceOf(
        MaceClipReachPlanResult.Blocked::class.java,
        MaceClipReachPlanner.plan(request),
    )
    assertEquals(reason, blocked.reason)
}

internal fun validatedProfile(
    parameters: MaceClipReachResearchParameters = parameters(),
) = MaceClipReachProfileTest.validatedProfile(parameters)

@Suppress("LongParameterList")
internal fun parameters(
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
