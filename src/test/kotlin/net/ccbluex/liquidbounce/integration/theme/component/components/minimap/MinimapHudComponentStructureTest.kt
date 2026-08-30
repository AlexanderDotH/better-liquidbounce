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

package net.ccbluex.liquidbounce.integration.theme.component.components.minimap

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MinimapHudComponentStructureTest {

    @Test
    fun `settings and subscription lifecycle remain owned by the component facade`() {
        val source = readSource("MinimapHudComponent.kt")

        assertContainsInOrder(
            source,
            "tree(TextureValueGroup)",
            "tree(EntityValueGroup)",
            "extraElements.forEach(::tree)",
            "\n        ChunkRenderer\n",
            "registerComponentListen(this)",
        )
        assertContainsInOrder(
            source,
            "RenderedEntities.subscribe(this)",
            "RenderedEntities.onUpdated",
            "RenderedEntities.filterTo(entities) { it !== player }",
            "entities.sortWith(MINIMAP_ENTITY_ORDER)",
            "RenderedEntities.unsubscribe(this)",
        )
        listOf(
            "int(\"Size\", 96, 1..256)",
            "float(\"ViewDistance\", 3.0F, 1.0F..8.0F)",
            "boolean(\"FixedDirection\", false)",
            "color(\"VertexColor\", Color4b.WHITE)",
            "float(\"Scale\", 1f, 0.25F..4F)",
            "enumChoice(\"OutOfBounds\", OutOfBounds.NONE)",
        ).forEach { setting -> assertTrue(setting in source, "Missing setting: $setting") }
    }

    @Test
    fun `render phases retain terrain entity marker extras and chrome order`() {
        val source = readSource("MinimapHudComponent.kt")

        assertContainsInOrder(
            source,
            "drawMinimapTerrain(",
            "drawMinimapEntities(",
            "drawMinimapOutOfBoundsEntityMarkers(",
            "element.render(this, boundingBox)",
            "drawMinimapHudChrome(boundingBox, bounds, chrome)",
        )
        assertContainsInOrder(
            source,
            "val scale = minimapSize / (2.0F * viewDistance)",
            "if (mapRotation != 0F) rotate(mapRotation)",
            "translate(-playerOffX.toFloat(), -playerOffZ.toFloat())",
        )
    }

    private fun assertContainsInOrder(source: String, vararg snippets: String) {
        val positions = snippets.map { snippet ->
            source.indexOf(snippet).also { index ->
                assertTrue(index >= 0, "Missing snippet: $snippet")
            }
        }
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
    }

    private fun readSource(name: String): String = Files.readString(Path.of(SOURCE_ROOT, name))

    private companion object {
        const val SOURCE_ROOT =
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/components/minimap"
    }
}
