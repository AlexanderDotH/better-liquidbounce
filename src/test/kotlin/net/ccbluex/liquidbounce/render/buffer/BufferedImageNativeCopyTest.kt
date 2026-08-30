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

package net.ccbluex.liquidbounce.render.buffer

import com.mojang.blaze3d.platform.NativeImage
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class BufferedImageNativeCopyTest {

    @Test
    fun `packed ARGB rows keep channel order and caller-owned scratch`() {
        val parent = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val expected = intArrayOf(0x10203040, 0x50607080, 0x90A0B0C0.toInt(), 0xD0E0F001.toInt())
        parent.setRGB(1, 1, 2, 2, expected, 0, 2)
        val source = parent.getSubimage(1, 1, 2, 2)
        val scratch = IntArray(0)

        NativeImage(2, 2, true).use { target ->
            val returned = BufferedImageNativeCopy.copy(
                source,
                target,
                NativeImageCopyRegion(sourceX = 0, sourceY = 0, targetX = 0, targetY = 0, width = 2, height = 2),
                scratch,
            )

            assertSame(scratch, returned)
            assertContentEquals(expected, target.pixels)
        }
    }

    @Test
    fun `non RGBA target is rejected before native memory is written`() {
        val source = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

        NativeImage(NativeImage.Format.LUMINANCE, 1, 1, true).use { target ->
            assertFailsWith<IllegalArgumentException> {
                BufferedImageNativeCopy.copy(
                    source,
                    target,
                    NativeImageCopyRegion(sourceX = 0, sourceY = 0, targetX = 0, targetY = 0, width = 1, height = 1),
                    IntArray(0),
                )
            }
        }
    }
}
