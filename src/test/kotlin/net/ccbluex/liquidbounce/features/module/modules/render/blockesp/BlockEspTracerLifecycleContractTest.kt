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
package net.ccbluex.liquidbounce.features.module.modules.render.blockesp

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockEspTracerLifecycleContractTest {

    @Test
    fun `module keeps public identity settings and guarded tracer delegation`() {
        val source = read(MODULE)
        val moduleBody = source.substringAfter("object ModuleBlockESP")
        val handler = source.substringAfter("private val tracerRenderHandler")
            .substringBefore("private fun forEachTrackedBlocks")

        assertTrue(source.contains("object ModuleBlockESP : ClientModule(\"BlockESP\", ModuleCategories.RENDER)"))
        assertOrdered(
            moduleBody,
            "choices(\"Mode\", 0)",
            "private val targets by blocks(",
            "choices(\"ColorMode\", 0)",
            "DistanceFadeUniformValueGroup",
            "boolean(\"MergeAdjacent\"",
            "BlockEspTracerSettings",
        )
        assertOrdered(handler, "if (!tracers.running || BlockTracker.isEmpty())", "BlockEspTracerRenderer.render(")
    }

    @Test
    fun `tracer collaborator preserves normal draw before deferred glow draw`() {
        val source = read(TRACER_RENDERER)

        assertOrdered(
            source,
            "createBlockTracerBatch(",
            "drawTracerBatch(batch, glowMask = false)",
            "batch.contributeGlowIfPresent",
            "EspShaderRenderer.contributeGlow",
            "drawTracerBatch(it, glowMask = true)",
        )
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        val positions = markers.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing ordered marker in ${markers.toList()}")
        assertEquals(positions.sorted(), positions)
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val MODULE = "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleBlockESP.kt"
        const val TRACER_RENDERER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/blockesp/BlockEspTracerRenderer.kt"
    }
}
