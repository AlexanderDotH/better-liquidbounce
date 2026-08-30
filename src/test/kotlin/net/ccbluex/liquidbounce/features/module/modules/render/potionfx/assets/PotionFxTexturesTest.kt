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

package net.ccbluex.liquidbounce.features.module.modules.render.potionfx.assets

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PotionFxTexturesTest {

    @Test
    fun `main preset tags and paths remain stable`() {
        assertEquals(
            listOf(
                "Dashed" to "potion_fx/main/dashed.png",
                "Solid" to "potion_fx/main/solid.png",
                "Runes" to "potion_fx/main/runes.png",
                "Atlas" to "potion_fx/main/atlas.png",
            ),
            PresetTexture.entries.map { it.tag to it.path },
        )
    }

    @Test
    fun `secondary preset tags and paths remain stable`() {
        assertEquals(
            listOf(
                "Cracked" to "potion_fx/secondary/cracked.png",
                "Neuron" to "potion_fx/secondary/neuron.png",
                "Hexagon" to "potion_fx/secondary/hexagon.png",
                "Stardust" to "potion_fx/secondary/stardust.png",
            ),
            SecondaryPresetTexture.entries.map { it.tag to it.path },
        )
    }
}
