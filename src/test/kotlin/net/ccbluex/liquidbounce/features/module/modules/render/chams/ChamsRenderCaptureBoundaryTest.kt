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
package net.ccbluex.liquidbounce.features.module.modules.render.chams

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChamsRenderCaptureBoundaryTest {

    @Test
    fun `capture receives entity visibility from its module owner`() {
        val capture = readSource("features/module/modules/render/chams/ChamsRenderCapture.kt")
        val module = readSource("features/module/modules/render/ModuleChams.kt")

        assertFalse(capture.contains("features.combat.runtime"))
        assertTrue(capture.contains("private val shouldRenderEntity: (Entity?) -> Boolean"))
        assertEquals(2, capture.split("shouldRenderEntity(entity)").size - 1)
        assertTrue(module.contains("shouldRenderEntity = { entity -> entity.shouldBeShown() }"))
    }

    private fun readSource(relativePath: String) = Files.readString(
        Path.of("src/main/kotlin/net/ccbluex/liquidbounce/$relativePath")
    )
}
