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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillVanillaVClipRouteTest {

    @Test
    fun `Vanilla VClip candidates stay within five blocks and prefer the target height`() {
        val upward = maceKillVanillaVClipCandidates(
            origin = Vec3(4.0, 64.0, -2.0),
            endpoint = Vec3(18.0, 80.0, -2.0),
        )
        val downward = maceKillVanillaVClipCandidates(
            origin = Vec3(4.0, 64.0, -2.0),
            endpoint = Vec3(18.0, 50.0, -2.0),
        )

        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0, -1.0, -2.0, -3.0, -4.0, -5.0), upward.map { it.y })
        assertEquals(listOf(-1.0, -2.0, -3.0, -4.0, -5.0, 1.0, 2.0, 3.0, 4.0, 5.0), downward.map { it.y })
        assertTrue(upward.all { it.x == 0.0 && it.z == 0.0 && kotlin.math.abs(it.y) <= 5.0 })
    }

    @Test
    fun `Vanilla VClip segment permits only its exact vertical five block round trip`() {
        val from = Vec3(4.0, 64.0, -2.0)
        val to = Vec3(4.0, 69.0, -2.0)
        val segment = MaceKillVanillaVClipSegment(from, to)

        assertEquals(Vec3(0.0, 5.0, 0.0), segment.movement)
        assertTrue(segment.matches(from, to))
        assertTrue(segment.matches(to, from))
        assertFalse(segment.matches(from, Vec3(4.0, 68.0, -2.0)))
        assertThrows(IllegalArgumentException::class.java) {
            MaceKillVanillaVClipSegment(from, Vec3(4.0, 69.1, -2.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaceKillVanillaVClipSegment(from, Vec3(4.1, 69.0, -2.0))
        }
    }

    @Test
    fun `Vanilla VClip accepts five block endpoint movement with floating point cancellation`() {
        val endpoint = Vec3(4.0, -63.999998, -2.0)
        val from = endpoint.subtract(Vec3(0.0, 5.0, 0.0))

        val segment = MaceKillVanillaVClipSegment(from, endpoint)

        assertTrue(segment.movement.y > MACE_KILL_MAX_VANILLA_VCLIP_DISTANCE)
        assertTrue(segment.matches(from, endpoint))
    }

    @Test
    fun `every remote route reaches bounded Vanilla VClip before ClipReach fallback`() {
        assertRoutePlanOrder(MaceKillRoutingMode.DIRECT, listOf("direct", "vclip"))
        assertRoutePlanOrder(MaceKillRoutingMode.A_STAR, listOf("astar", "vclip"))
        assertRoutePlanOrder(MaceKillRoutingMode.INSTANT, listOf("direct", "vclip"))

        val motionCalls = mutableListOf<String>()
        assertEquals(
            "vclip",
            selectMaceKillMotionRoutePlan(
                collisionPlan = { motionCalls += "motion"; null },
                vanillaVClipPlan = { motionCalls += "vclip"; "vclip" },
            ),
        )
        assertEquals(listOf("motion", "vclip"), motionCalls)
    }

    private fun assertRoutePlanOrder(routingMode: MaceKillRoutingMode, expectedCalls: List<String>) {
        val calls = mutableListOf<String>()

        val selected = selectMaceKillRoutePlan(
            routingMode = routingMode,
            directPlan = { calls += "direct"; null },
            aStarPlan = { calls += "astar"; null },
            vanillaVClipPlan = { calls += "vclip"; "vclip" },
            wallClipPlan = { calls += "clip"; "clip" },
        )

        assertEquals("vclip", selected)
        assertEquals(expectedCalls, calls)
    }
}
