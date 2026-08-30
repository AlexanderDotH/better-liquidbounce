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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill


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

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class MaceInstantStrikePlannerTest {

    @Test
    fun `height search uses the bounding box at the virtual endpoint`() {
        val checkedBoxes = mutableListOf<AABB>()
        val result = MaceInstantStrikePlanner.plan(request(maximumFallHeight = 3)) { box ->
            checkedBoxes += box
            box.minY == 72.0
        }

        val plan = assertInstanceOf(MaceInstantStrikePlanResult.Ready::class.java, result).plan
        assertEquals(2, plan.fallHeight)
        assertEquals(listOf(73.0, 72.0), checkedBoxes.map { it.minY })
        assertEquals(listOf(9.7, 9.7), checkedBoxes.map { it.minX })
        assertEquals(listOf(-5.3, -5.3), checkedBoxes.map { it.minZ })
    }

    @Test
    fun `height twenty two keeps the current long-height packet order`() {
        val endpoint = Vec3(10.0, 70.0, -5.0)
        val result = MaceInstantStrikePlanner.plan(request(maximumFallHeight = 22)) { true }

        val plan = assertInstanceOf(MaceInstantStrikePlanResult.Ready::class.java, result).plan
        assertEquals(22, plan.fallHeight)
        assertEquals(
            listOf(
                MaceInstantStrikePacket.StatusOnly(onGround = false),
                MaceInstantStrikePacket.StatusOnly(onGround = false),
                MaceInstantStrikePacket.StatusOnly(onGround = false),
                MaceInstantStrikePacket.Position(endpoint.add(0.0, 22.0, 0.0), onGround = false),
                MaceInstantStrikePacket.Position(endpoint, onGround = false),
            ),
            plan.packets,
        )
    }

    @Test
    fun `height ten keeps two collision-grounded status packets before positions`() {
        val endpoint = Vec3(10.0, 70.0, -5.0)
        val result = MaceInstantStrikePlanner.plan(
            request(maximumFallHeight = 10, endpointOnGround = true),
        ) { true }

        val plan = assertInstanceOf(MaceInstantStrikePlanResult.Ready::class.java, result).plan
        assertEquals(
            listOf(
                MaceInstantStrikePacket.StatusOnly(onGround = true),
                MaceInstantStrikePacket.StatusOnly(onGround = true),
                MaceInstantStrikePacket.Position(endpoint.add(0.0, 10.0, 0.0), onGround = false),
                MaceInstantStrikePacket.Position(endpoint, onGround = false),
            ),
            plan.packets,
        )
    }

    @Test
    fun `no collision-free height rejects the strike without packets`() {
        val result = MaceInstantStrikePlanner.plan(request(maximumFallHeight = 3)) { false }

        assertEquals(MaceInstantStrikePlanResult.NoUsableHeight, result)
    }

    @Test
    fun `post attack reset rises before grounding so a rejected Instant hit cannot deal fall damage`() {
        val endpoint = Vec3(10.0, 70.0, -5.0)
        val endpointBoundingBox = AABB(9.7, 70.0, -5.3, 10.3, 71.8, -4.7)
        val checkedRises = mutableListOf<Double>()
        val result = MacePostAttackFallResetPlanner.plan(
            MacePostAttackFallResetRequest(endpoint, endpointBoundingBox),
        ) { box ->
            checkedRises += box.minY - endpointBoundingBox.minY
            box.minY <= endpointBoundingBox.minY + 0.03125
        }

        val plan = assertInstanceOf(MacePostAttackFallResetPlanResult.Ready::class.java, result).plan
        assertEquals(listOf(0.0625, 0.03125), checkedRises)
        assertEquals(0.03125, plan.rise)
        assertEquals(
            listOf(
                MaceInstantStrikePacket.Position(endpoint.add(0.0, 0.03125, 0.0), onGround = false),
                MaceInstantStrikePacket.Position(endpoint, onGround = true),
            ),
            plan.packets,
        )
        assertEquals(
            0,
            countSimulatedFallDamage(
                startingY = endpoint.y,
                initialFallDistance = 170.0,
                packets = plan.packets,
            ),
        )
    }

    private fun request(
        maximumFallHeight: Int,
        endpointOnGround: Boolean = false,
    ) = MaceInstantStrikeRequest(
        physicalPosition = Vec3(0.0, 64.0, 0.0),
        physicalBoundingBox = AABB(-0.3, 64.0, -0.3, 0.3, 65.8, 0.3),
        virtualEndpoint = Vec3(10.0, 70.0, -5.0),
        maximumFallHeight = maximumFallHeight,
        endpointOnGround = endpointOnGround,
    )

    /** Mirrors the vanilla 26.2 move-handler order relevant to the reset contract. */
    private fun countSimulatedFallDamage(
        startingY: Double,
        initialFallDistance: Double,
        packets: List<MaceInstantStrikePacket.Position>,
    ): Int {
        var y = startingY
        var fallDistance = initialFallDistance
        var damageEvents = 0
        packets.forEach { packet ->
            val movementY = packet.position.y - y
            if (movementY < 0.0) fallDistance -= movementY
            if (packet.onGround && fallDistance > 3.0) damageEvents++
            if (packet.onGround || movementY > 0.0) fallDistance = 0.0
            y = packet.position.y
        }
        return damageEvents
    }

}
