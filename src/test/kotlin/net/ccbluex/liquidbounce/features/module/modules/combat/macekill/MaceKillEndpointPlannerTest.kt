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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillEndpointPlannerTest {

    @Test
    fun `blocked nearest side selects a clear endpoint around the target`() {
        val request = request(origin = Vec3(0.0, 64.0, 0.0))

        val endpoint = requireNotNull(MaceKillEndpointPlanner.find(request) { candidate ->
            candidate.z > 0.75
        })

        assertTrue(endpoint.z > 0.75)
        assertTrue(endpoint.distanceTo(request.targetPosition) <= request.maximumRadius + 1.0E-9)
    }

    @Test
    fun `waist high obstruction selects a vertical melee endpoint`() {
        val request = request(origin = Vec3(0.0, 64.0, 0.0))

        val endpoint = requireNotNull(MaceKillEndpointPlanner.find(request) { candidate ->
            candidate.y >= request.targetPosition.y + 1.0
        })

        assertEquals(request.targetPosition.y + 1.0, endpoint.y, 1.0E-9)
    }

    @Test
    fun `planner fails closed when every body and attack ray candidate is blocked`() {
        assertNull(MaceKillEndpointPlanner.find(request()) { false })
    }

    @Test
    fun `candidate order is deterministic and never enters target body clearance`() {
        val request = request()

        val first = MaceKillEndpointPlanner.candidates(request)
        val second = MaceKillEndpointPlanner.candidates(request)

        assertEquals(first, second)
        assertTrue(first.isNotEmpty())
        assertTrue(first.all { candidate ->
            val horizontal = Vec3(
                candidate.x - request.targetPosition.x,
                0.0,
                candidate.z - request.targetPosition.z,
            )
            horizontal.length() + 1.0E-9 >= request.minimumClearance
        })
    }

    private fun request(
        origin: Vec3 = Vec3(0.0, 64.0, -8.0),
    ) = MaceKillEndpointSearchRequest(
        origin = origin,
        targetPosition = Vec3(8.0, 64.0, 0.0),
        minimumClearance = 0.8,
        maximumRadius = 3.6,
    )
}
