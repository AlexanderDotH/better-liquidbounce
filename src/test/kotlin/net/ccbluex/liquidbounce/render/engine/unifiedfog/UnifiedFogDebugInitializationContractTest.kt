/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.render.engine.unifiedfog

import net.ccbluex.liquidbounce.render.engine.UnifiedFogDebug
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedFogDebugInitializationContractTest {

    @Test
    fun `inactive debug state is safe before CustomAmbience has initialized`() {
        val debugSource = read(DEBUG_SOURCE)
        val inactive = region(debugSource, "private fun inactive()", "private fun publish(")

        assertTrue(inactive.contains("engine = \"Legacy\""))
        assertFalse(inactive.contains("ModuleCustomAmbience"))
        assertFalse(inactive.contains("FogValueGroup"))
        assertEquals("Legacy", UnifiedFogDebug.state().engine)
    }

    @Test
    fun `out of game deactivation does not publish through the module owner`() {
        val debugSource = read(DEBUG_SOURCE)
        val rendererSource = read(RENDERER_SOURCE)
        val reset = declaration(debugSource, "internal fun reset(")
        val beginFrame = declaration(rendererSource, "fun beginFrame()")
        val deactivate = declaration(rendererSource, "private fun deactivate(")

        assertTrue(reset.contains("if (publish)"))
        assertTrue(beginFrame.contains("deactivate(publishDebug = false)"))
        assertTrue(beginFrame.contains("deactivate(publishDebug = true)"))
        assertInOrder(
            beginFrame,
            "val activity = CustomFogRenderBridge.activity()",
            "!activity.customAmbienceRunning",
            "!activity.shouldRenderUnified",
        )
        assertTrue(deactivate.contains("UnifiedFogDebug.reset(publishDebug)"))
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private fun declaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing declaration: $marker" }
        val openingBrace = source.indexOf('{', markerIndex)
        require(openingBrace >= 0) { "Missing declaration body: $marker" }

        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(markerIndex, index + 1)
            }
        }
        error("Unclosed declaration: $marker")
    }

    private fun region(source: String, startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        require(start >= 0) { "Missing region start: $startMarker" }
        val end = source.indexOf(endMarker, start + startMarker.length)
        require(end > start) { "Missing region end: $endMarker" }
        return source.substring(start, end)
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val DEBUG_SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/UnifiedFogDebugState.kt"
        const val RENDERER_SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/UnifiedFogRenderer.kt"
    }
}
