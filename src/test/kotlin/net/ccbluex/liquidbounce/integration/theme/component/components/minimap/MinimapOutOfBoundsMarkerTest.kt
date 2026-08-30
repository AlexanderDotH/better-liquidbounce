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

import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.Vec2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MinimapOutOfBoundsMarkerTest {

    private val viewport = MinimapMarkerViewport(
        center = Vec2(50.0F, 50.0F),
        boundingBox = BoundingBox2f(0.0F, 0.0F, 100.0F, 100.0F),
        playerChunkX = 0.0F,
        playerChunkZ = 0.0F,
        mapScale = 50.0F / 3.0F,
        viewDistance = 3.0F,
        rotation = 0.0F,
    )

    @Test
    fun `markers retain source order while in-bounds entities are omitted`() {
        val colors = listOf(Color4b.RED, Color4b.BLUE, Color4b.GREEN, Color4b.YELLOW)
        val markers = prepareMinimapOutOfBoundsMarkers(
            sources = listOf(
                MinimapMarkerSource(4.0F, 0.0F, colors[0]),
                MinimapMarkerSource(3.0F, 3.0F, Color4b.WHITE),
                MinimapMarkerSource(0.0F, -4.0F, colors[1]),
                MinimapMarkerSource(0.0F, 4.0F, colors[2]),
                MinimapMarkerSource(-4.0F, 0.0F, colors[3]),
            ),
            viewport = viewport,
        )

        assertEquals(colors, markers.map(MinimapMarkerQuad::color))
        assertEquals(BoundingBox2f(98.0F, 48.0F, 100.0F, 52.0F), markers[0].boundingBox)
        assertEquals(BoundingBox2f(48.0F, 0.0F, 52.0F, 2.0F), markers[1].boundingBox)
        assertEquals(BoundingBox2f(48.0F, 98.0F, 52.0F, 100.0F), markers[2].boundingBox)
        assertEquals(BoundingBox2f(0.0F, 48.0F, 2.0F, 52.0F), markers[3].boundingBox)
    }

    @Test
    fun `diagonal ties stay on the vertical edge and clamp to the corner`() {
        val marker = prepareMinimapOutOfBoundsMarkers(
            sources = listOf(MinimapMarkerSource(4.0F, 4.0F, Color4b.WHITE)),
            viewport = viewport,
        ).single()

        assertEquals(BoundingBox2f(98.0F, 96.0F, 100.0F, 100.0F), marker.boundingBox)
    }

    @Test
    fun `map rotation is applied before selecting the marker edge`() {
        val rotatedViewport = viewport.copy(rotation = (Math.PI * 0.5).toFloat())
        val marker = prepareMinimapOutOfBoundsMarkers(
            sources = listOf(MinimapMarkerSource(4.0F, 0.0F, Color4b.WHITE)),
            viewport = rotatedViewport,
        ).single().boundingBox

        assertEquals(48.0F, marker.xMin, 0.001F)
        assertEquals(52.0F, marker.xMax, 0.001F)
        assertEquals(98.0F, marker.yMin, 0.001F)
        assertEquals(100.0F, marker.yMax, 0.001F)
    }
}
