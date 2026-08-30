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

package net.ccbluex.liquidbounce.render.wireframe

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WireframeSwimmingPoseTest {

    @Test
    fun `swimming parts keep body arms legs and head draw order`() {
        val parts = swimmingWireframePartPoses(xRot = 30f, swimAmount = 0.5f)

        assertEquals(6, parts.size)
        assertSame(RENDER_BODY, parts[0].box)
        assertSame(RENDER_LEFT_ARM, parts[1].box)
        assertSame(RENDER_RIGHT_ARM, parts[2].box)
        assertSame(RENDER_LEFT_LEG, parts[3].box)
        assertSame(RENDER_RIGHT_LEG, parts[4].box)
        assertSame(RENDER_HEAD, parts[5].box)
    }

    @Test
    fun `swimming parts keep pivots rolls and interpolated head pitch`() {
        val parts = swimmingWireframePartPoses(xRot = 30f, swimAmount = 0.5f)

        assertEquals(RENDER_LEFT_ARM.center, parts[1].pivot)
        assertEquals(SWIM_LEFT_ARM_ROLL, parts[1].zRot)
        assertEquals(SWIM_RIGHT_ARM_ROLL, parts[2].zRot)
        assertEquals(SWIM_LEFT_LEG_ROLL, parts[3].zRot)
        assertEquals(SWIM_RIGHT_LEG_ROLL, parts[4].zRot)
        assertEquals(RENDER_HEAD.bottomCenter, parts[5].pivot)
        assertEquals(-7.5f, parts[5].xRot)
    }

    @Test
    fun `non positive swim amount keeps fully swimming head pitch`() {
        val head = swimmingWireframePartPoses(xRot = 30f, swimAmount = 0f).last()

        assertEquals(SWIM_HEAD_TARGET_ROTATION, head.xRot)
    }

    private fun swimmingWireframePartPoses(xRot: Float, swimAmount: Float): List<RecordedPartPose> = buildList {
        val headRotation = swimmingWireframeHeadRotation(xRot, swimAmount)
        forEachSwimmingWireframePart(headRotation) { box, pivot, partXRot, partYRot, partZRot ->
            add(RecordedPartPose(box, pivot, partXRot, partYRot, partZRot))
        }
    }

    private data class RecordedPartPose(
        val box: AABB,
        val pivot: Vec3,
        val xRot: Float,
        val yRot: Float,
        val zRot: Float,
    )
}
