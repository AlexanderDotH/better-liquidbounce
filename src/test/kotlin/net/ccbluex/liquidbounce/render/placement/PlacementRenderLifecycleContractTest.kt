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
package net.ccbluex.liquidbounce.render.placement

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacementRenderLifecycleContractTest {

    @Test
    fun `handler preserves public surface and delegates one frame`() {
        val source = read("PlacementRenderHandler.kt")

        assertTrue(source.contains("class PlacementRenderHandler("))
        assertTrue(source.contains("val id: Int = 0"))
        listOf("render", "isFinished", "addBlock", "removeBlock", "updateAll", "updateBox", "clearSilently", "clear")
            .forEach { function -> assertTrue(Regex("""fun\s+$function\b""").containsMatchIn(source)) }
        assertTrue(source.contains("frameRenderer.render(event, time)"))
        assertFalse(source.contains("TooManyFunctions"))
    }

    @Test
    fun `frame preserves incoming current outgoing and neighbor refresh order`() {
        val source = read("PlacementFrameRenderer.kt")
        val render = source.substringAfter("fun render(").substringBefore("private fun renderIncoming")

        assertOrdered(
            render,
            "event.renderEnvironment",
            "renderIncoming(time)",
            "renderCurrent()",
            "expiredPositions.clear()",
            "renderOutgoing(time)",
            "refreshExpiredNeighbors()",
        )
        assertTrue(source.contains("withPositionRelativeToCamera"))
        assertTrue(source.contains("drawBox("))
        assertTrue(source.contains("currentList.put(pos, value.toCurrent())"))
        assertTrue(source.contains("outList.put(pos, value.copy(startTime = time))"))
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        val positions = markers.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing ordered marker in ${markers.toList()}")
        assertEquals(positions.sorted(), positions)
    }

    private fun read(file: String): String = Files.readString(Path.of(PLACEMENT_PATH, file))

    private companion object {
        const val PLACEMENT_PATH = "src/main/kotlin/net/ccbluex/liquidbounce/render/placement"
    }
}
