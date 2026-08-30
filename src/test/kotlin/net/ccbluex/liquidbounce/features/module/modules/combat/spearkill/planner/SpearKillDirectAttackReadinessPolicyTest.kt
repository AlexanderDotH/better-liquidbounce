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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner


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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.SpearKillDirectAttackPreparation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.resolveSpearKillDirectAttackReadiness
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SpearKillDirectAttackReadinessPolicyTest {

    @Test
    fun `failed fall safety preserves the canonical rejection`() {
        val readiness = resolveSpearKillDirectAttackReadiness(
            plan = directPacketRoutePlan(),
            instantBurst = null,
            hitTicks = 9,
            fallSafetyStarted = false,
        )

        assertEquals(
            SpearKillDirectAttackPreparation.Rejected(
                stage = "fall-safety-plan",
                result = SpearKillAttackStartResult.REJECTED,
            ),
            readiness,
        )
    }

    @Test
    fun `successful fall safety retains plan identity timing and outbound tick count`() {
        val plan = directPacketRoutePlan()
        val readiness = resolveSpearKillDirectAttackReadiness(
            plan = plan,
            instantBurst = null,
            hitTicks = 9,
            fallSafetyStarted = true,
        ) as SpearKillDirectAttackPreparation.Ready

        assertSame(plan, readiness.plan)
        assertNull(readiness.instantBurst)
        assertEquals(9, readiness.hitTicks)
        assertEquals(1, readiness.outboundTickCount)
    }

    private fun directPacketRoutePlan(): DirectPacketRoutePlan {
        val first = Vec3(3.0, 0.0, 0.0)
        val second = Vec3(2.0, 0.0, 0.0)
        return DirectPacketRoutePlan(
            route = SpearKillAStarPacketRoute(
                outboundMovements = listOf(first, second),
                roundTripMovements = listOf(first, second, second.scale(-1.0), first.scale(-1.0), Vec3.ZERO),
                terminalBurstSteps = 2,
            ),
            targetSnapshot = SpearKillRouteTargetSnapshot(
                observedPosition = Vec3.ZERO,
                eyeOffset = Vec3.ZERO,
                boundingBox = AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0),
                velocity = Vec3.ZERO,
                predictedPositions = listOf(Vec3.ZERO),
            ),
        )
    }
}
