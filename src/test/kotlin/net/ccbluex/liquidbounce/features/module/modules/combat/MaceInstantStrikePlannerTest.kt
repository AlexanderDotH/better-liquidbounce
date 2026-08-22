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

}
