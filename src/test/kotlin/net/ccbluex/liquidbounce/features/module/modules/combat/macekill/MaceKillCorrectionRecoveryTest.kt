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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillCorrectionRecoveryTest {

    @Test
    fun `target apex correction walks the complete confirmed prefix backwards`() {
        val plan = readyPlan()
        val targetApex = plan.outboundMovements.take(2).fold(plan.origin, Vec3::add)

        val recovery = requireNotNull(maceKillFullInverseRecovery(plan, targetApex))

        assertEquals(plan.outboundMovements.take(2).asReversed().map { it.scale(-1.0) }, recovery)
        assertEquals(plan.origin, recovery.fold(targetApex, Vec3::add))
    }

    @Test
    fun `endpoint correction keeps the configured ClipReach inverse instead of a straight reset`() {
        val plan = readyPlan()

        val recovery = requireNotNull(maceKillFullInverseRecovery(plan, plan.endpoint))

        assertEquals(plan.returnMovements, recovery)
        assertEquals(plan.origin, recovery.fold(plan.endpoint, Vec3::add))
    }

    @Test
    fun `small correction drift rejoins the nearest anchor before propagating backwards`() {
        val plan = readyPlan()
        val originApex = plan.origin.add(plan.outboundMovements.first())
        val authoritative = originApex.add(0.2, -0.1, 0.15)

        val recovery = requireNotNull(maceKillFullInverseRecovery(plan, authoritative))

        assertTrue(recovery.first().length() < 0.3)
        assertEquals(plan.origin.x, recovery.fold(authoritative, Vec3::add).x, 1.0E-9)
        assertEquals(plan.origin.y, recovery.fold(authoritative, Vec3::add).y, 1.0E-9)
        assertEquals(plan.origin.z, recovery.fold(authoritative, Vec3::add).z, 1.0E-9)
    }

    @Test
    fun `unrelated correction cannot invent a route through unvalidated space`() {
        assertNull(maceKillFullInverseRecovery(readyPlan(), Vec3(200.0, 200.0, 200.0)))
    }

    @Test
    fun `forced origin reset stays bounded and ends exactly at the captured origin`() {
        val authoritative = Vec3(16.0, 99.0, -12.0)
        val origin = Vec3(0.0, 64.0, 0.0)

        val recovery = maceKillForcedOriginRecovery(authoritative, origin)

        assertTrue(recovery.all { it.length() <= 3.0 + 1.0E-9 })
        assertEquals(origin.x, recovery.fold(authoritative, Vec3::add).x, 1.0E-9)
        assertEquals(origin.y, recovery.fold(authoritative, Vec3::add).y, 1.0E-9)
        assertEquals(origin.z, recovery.fold(authoritative, Vec3::add).z, 1.0E-9)
    }

    private fun readyPlan(): MaceClipReachPlan {
        val result = MaceClipReachPlanner.plan(
            MaceClipReachPlanRequest(
                origin = Vec3(0.0, 64.0, 0.0),
                endpoint = Vec3(30.0, 64.0, 0.0),
                dimensionBounds = MaceClipReachDimensionBounds(-64.0, 320.0),
                profile = MaceClipReachProfileTest.validatedProfile(),
                use = MaceClipReachUse.NORMAL,
                anchorValidator = MaceClipReachAnchorValidator { _, _ -> true },
            ),
        )
        return (result as MaceClipReachPlanResult.Ready).plan
    }
}
