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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.CriticalsMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CriticalsAnimationMathTest {

    @Test
    fun `micro hop reaches the configured height halfway through`() {
        assertEquals(
            0.12f,
            CriticalsAnimationMath.verticalOffset(CriticalsMode.MICRO_HOP, 0.12f, 0.06f, 0.5f),
            1.0E-6f,
        )
    }

    @Test
    fun `packet critical keeps its historical three-step jitter`() {
        assertEquals(0.06f, CriticalsAnimationMath.packetOffset(0.06f, 0.10f), 1.0E-6f)
        assertEquals(0f, CriticalsAnimationMath.packetOffset(0.06f, 0.25f), 1.0E-6f)
        assertEquals(0.03f, CriticalsAnimationMath.packetOffset(0.06f, 0.45f), 1.0E-6f)
        assertEquals(0f, CriticalsAnimationMath.packetOffset(0.06f, 0.75f), 1.0E-6f)
    }

    @Test
    fun `swing reaches full progress halfway through`() {
        assertEquals(1f, CriticalsAnimationMath.swingProgress(0.5f), 1.0E-6f)
    }
}
