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
package net.ccbluex.liquidbounce.render.utils

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceFadeUniformValueGroupLifecycleTest {

    @BeforeEach
    fun bootstrapMinecraft() {
        MinecraftBootstrap.ensureInitialized()
    }

    @Test
    fun `configuration values are available before the render device`() {
        val distanceFade = DistanceFadeUniformValueGroup()

        assertEquals(
            listOf("NearStart", "NearEnd", "FarStart", "FarEnd"),
            distanceFade.inner.map { it.name },
        )
        assertEquals(0f, distanceFade.nearStart)
        assertEquals(0f, distanceFade.nearEnd)
        assertEquals(512f, distanceFade.farStart)
        assertEquals(512f, distanceFade.farEnd)
    }

    @Test
    fun `gpu buffer allocation remains behind update and bind`() {
        val source = Files.readString(Path.of(SOURCE))

        assertTrue(source.contains("private val ubo by lazy"))
        assertTrue(
            source.substringAfter("fun updateIfDirty()")
                .substringBefore("fun bindUniform")
                .contains("ubo.writeStd140"),
        )
        assertTrue(
            source.substringAfter("fun bindUniform")
                .contains("pass.setUniform(ClientUniformDefine.DISTANCE_FADE.uboName, ubo)"),
        )
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/utils/DistanceFadeUniformValueGroup.kt"
    }
}
