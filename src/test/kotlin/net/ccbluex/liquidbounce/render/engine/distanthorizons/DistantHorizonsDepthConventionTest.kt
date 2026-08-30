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

package net.ccbluex.liquidbounce.render.engine.distanthorizons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DistantHorizonsDepthConventionTest {

    @Test
    fun `raw OpenGL depth uses the standard clear value`() {
        assertEquals(1f, DistantHorizonsDepthConvention.forEngine("OPEN_GL")?.clearDepth)
        assertEquals(false, DistantHorizonsDepthConvention.forEngine("OPEN_GL")?.zeroToOneDepth)
    }

    @Test
    fun `Blaze depth follows Minecraft reverse z`() {
        assertEquals(0f, DistantHorizonsDepthConvention.forEngine("BLAZE_3D")?.clearDepth)
        assertEquals(true, DistantHorizonsDepthConvention.forEngine("BLAZE_3D")?.zeroToOneDepth)
    }

    @Test
    fun `unknown renderers fail closed`() {
        assertNull(DistantHorizonsDepthConvention.forEngine("VULKAN"))
        assertNull(DistantHorizonsDepthConvention.forEngine(""))
    }
}
