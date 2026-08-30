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
package net.ccbluex.liquidbounce.render.engine.esp

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EspShaderRendererLifecycleContractTest {

    @Test
    fun `public facade keeps capture and protected composite order`() {
        val source = read("EspShaderRenderer.kt")

        listOf("beginFrame", "capture", "composite").forEach { function ->
            assertTrue(Regex("""@JvmStatic\s+fun\s+$function\b""").containsMatchIn(source))
        }
        val capture = source.substringAfter("fun capture(").substringBefore("private fun captureOutline")
        assertOrdered(
            capture,
            "preparedGlowCapturer.capture",
            "protectedMaskRenderer.capture",
            "chamsCapturer.capture",
            "captureOutline",
        )

        val composite = source.substringAfter("fun composite(").substringBefore("private fun compositeGlow")
        assertOrdered(
            composite,
            "chamsCapturer.composite(target)",
            "protectedMaskRenderer.prepare(source, maskSources)",
            "compositeGlow(target, source, exclusion)",
            "EspCompositePassRenderer.outline",
        )
    }

    @Test
    fun `blur collaborator preserves pass and buffer lifetime order`() {
        val source = read("EspGlowBlurRenderer.kt")

        assertOrdered(
            source,
            "downsample(mask, ping)",
            "blurHorizontal(ping, pong, resources, size, style)",
            "blurVertical(pong, ping, resources, size, style)",
            "return ping",
        )
        assertTrue(source.contains("ClientRenderPipelines.EspDownsample"))
        assertTrue(source.contains("ClientRenderPipelines.EspGaussianBlur"))
        assertOrdered(source.substringAfter("override fun close()"), "blurPing.close()", "blurPong.close()")
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        val positions = markers.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing ordered marker in ${markers.toList()}")
        assertEquals(positions.sorted(), positions)
    }

    private fun read(file: String): String = Files.readString(Path.of(ESP_PATH, file))

    private companion object {
        const val ESP_PATH = "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp"
    }
}
