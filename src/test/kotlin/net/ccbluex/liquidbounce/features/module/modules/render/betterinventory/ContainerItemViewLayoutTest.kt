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

package net.ccbluex.liquidbounce.features.module.modules.render.betterinventory

import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContainerItemViewLayoutTest {

    @Test
    fun `shulker grid overlapping a wide tooltip moves to its right`() {
        val preview = BoundingBox2f(159F, 50F, 321F, 104F)
        val tooltip = BoundingBox2f(91F, 58F, 388F, 275F)

        val placed = ContainerItemViewLayout.avoidTooltip(
            preview = preview,
            tooltip = tooltip,
            viewportWidth = 625F,
            viewportHeight = 298F,
        )

        assertEquals(392F, placed.xMin)
        assertEquals(preview.yMin, placed.yMin)
        assertFalse(placed intersects tooltip)
    }

    @Test
    fun `shulker grid falls back to the left when the tooltip reaches the right edge`() {
        val preview = BoundingBox2f(440F, 70F, 602F, 124F)
        val tooltip = BoundingBox2f(330F, 58F, 610F, 275F)

        val placed = ContainerItemViewLayout.avoidTooltip(
            preview = preview,
            tooltip = tooltip,
            viewportWidth = 625F,
            viewportHeight = 298F,
        )

        assertEquals(164F, placed.xMin)
        assertEquals(preview.yMin, placed.yMin)
        assertFalse(placed intersects tooltip)
    }

    @Test
    fun `shulker grid uses a vertical side when neither horizontal side fits`() {
        val preview = BoundingBox2f(90F, 105F, 252F, 159F)
        val tooltip = BoundingBox2f(20F, 70F, 300F, 180F)

        val placed = ContainerItemViewLayout.avoidTooltip(
            preview = preview,
            tooltip = tooltip,
            viewportWidth = 320F,
            viewportHeight = 300F,
        )

        assertEquals(184F, placed.yMin)
        assertFalse(placed intersects tooltip)
    }

    @Test
    fun `shulker grid outside the tooltip keeps its configured position`() {
        val preview = BoundingBox2f(400F, 50F, 562F, 104F)
        val tooltip = BoundingBox2f(91F, 58F, 388F, 275F)

        val placed = ContainerItemViewLayout.avoidTooltip(
            preview = preview,
            tooltip = tooltip,
            viewportWidth = 625F,
            viewportHeight = 298F,
        )

        assertEquals(preview, placed)
    }
}
