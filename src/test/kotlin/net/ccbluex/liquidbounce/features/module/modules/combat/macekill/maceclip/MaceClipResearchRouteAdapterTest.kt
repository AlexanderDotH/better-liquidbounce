/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.*

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceClipResearchRouteAdapterTest {

    @Test
    fun `bounded request becomes an unvalidated research-only exact inverse descriptor`() {
        val result = MaceClipResearchRouteAdapter.plan(
            routeRequest(
                MaceClipResearchProbeRequest.Move(
                    distance = 10.0,
                    primingPackets = 9,
                    packetShape = MaceClipResearchPacketShape.POSITION,
                    clearance = 99.0,
                    phaseDelayTicks = 1,
                    terminalHoldTicks = 2,
                )
            )
        )

        val descriptor = assertInstanceOf(MaceClipResearchRouteResult.Ready::class.java, result).descriptor
        assertEquals(MaceClipReachProfileValidation.UNVALIDATED, descriptor.plan.profile.validation)
        assertTrue(descriptor.plan.profile.permits(MaceClipReachUse.RESEARCH))
        assertEquals(9, descriptor.primingPackets)
        assertEquals(128, descriptor.packetBudget)
        assertEquals(50, descriptor.timeoutTicks)
        val expectedPhases = listOf(
            MaceClipResearchPhase.PRIME,
            MaceClipResearchPhase.ASCEND,
            MaceClipResearchPhase.TRANSFER,
            MaceClipResearchPhase.DESCEND,
            MaceClipResearchPhase.STRIKE,
            MaceClipResearchPhase.RETURN_ASCEND,
            MaceClipResearchPhase.RETURN_TRANSFER,
            MaceClipResearchPhase.RETURN_DESCEND,
        )
        assertEquals(expectedPhases, MaceClipResearchPhase.entries)
        assertEquals(expectedPhases, descriptor.steps.map { it.phase })
        assertEquals(
            descriptor.plan.origin,
            descriptor.returnDeltas.fold(descriptor.plan.endpoint, Vec3::add),
        )
        assertEquals(MaceClipResearchPhase.ASCEND, descriptor.phaseForMovement(outbound = true, index = 0))
        assertEquals(MaceClipResearchPhase.TRANSFER, descriptor.phaseForMovement(outbound = true, index = 1))
        assertEquals(MaceClipResearchPhase.DESCEND, descriptor.phaseForMovement(outbound = true, index = 2))
        assertEquals(MaceClipResearchPhase.RETURN_ASCEND, descriptor.phaseForMovement(outbound = false, index = 0))
        val returnAscendPackets = descriptor.steps
            .first { it.phase == MaceClipResearchPhase.RETURN_ASCEND }
            .packetCount
        assertEquals(
            MaceClipResearchPhase.RETURN_TRANSFER,
            descriptor.phaseForMovement(outbound = false, index = returnAscendPackets),
        )
        assertEquals(
            MaceClipResearchPhase.RETURN_DESCEND,
            descriptor.phaseForMovement(outbound = false, index = returnAscendPackets + 1),
        )
    }

    @Test
    fun `move distance mismatch and invalid values fail before route execution`() {
        val validRequest = MaceClipResearchProbeRequest.Move(
            distance = 9.0,
            primingPackets = 9,
            packetShape = MaceClipResearchPacketShape.POSITION,
            clearance = 99.0,
            phaseDelayTicks = 1,
            terminalHoldTicks = 2,
        )
        val mismatch = MaceClipResearchRouteAdapter.plan(routeRequest(validRequest))
        val invalid = MaceClipResearchRouteAdapter.plan(
            routeRequest(validRequest.copy(clearance = Double.NaN))
        )

        assertEquals(
            MaceClipResearchRouteRejection.DISTANCE_MISMATCH,
            (mismatch as MaceClipResearchRouteResult.Rejected).reason,
        )
        assertEquals(
            MaceClipResearchRouteRejection.INVALID_REQUEST,
            (invalid as MaceClipResearchRouteResult.Rejected).reason,
        )
    }

    private fun routeRequest(request: MaceClipResearchProbeRequest) = MaceClipResearchRouteRequest(
        request = request,
        origin = Vec3(0.0, 64.0, 0.0),
        endpoint = Vec3(10.0, 64.0, 0.0),
        dimensionBounds = MaceClipReachDimensionBounds(-64.0, 320.0),
        anchorValidator = MaceClipReachAnchorValidator { _, _ -> true },
    )
}
