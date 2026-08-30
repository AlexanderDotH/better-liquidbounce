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

package net.ccbluex.liquidbounce.integration.theme.component.components.minimap

import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.math.fastCos
import net.ccbluex.liquidbounce.utils.math.fastSin
import net.minecraft.world.phys.Vec2
import kotlin.math.abs
import kotlin.math.max

internal data class MinimapMarkerViewport(
    val center: Vec2,
    val boundingBox: BoundingBox2f,
    val playerChunkX: Float,
    val playerChunkZ: Float,
    val mapScale: Float,
    val viewDistance: Float,
    val rotation: Float,
)

internal data class MinimapMarkerSource(
    val chunkX: Float,
    val chunkZ: Float,
    val color: Color4b,
)

internal data class MinimapMarkerQuad(
    val boundingBox: BoundingBox2f,
    val color: Color4b,
)

private const val MARKER_THICKNESS = 2.0F
private const val MARKER_LENGTH = 4.0F
private const val MARKER_HALF = MARKER_LENGTH * 0.5F

internal fun prepareMinimapOutOfBoundsMarkers(
    sources: Iterable<MinimapMarkerSource>,
    viewport: MinimapMarkerViewport,
): List<MinimapMarkerQuad> = sources.mapNotNull { source ->
    prepareMinimapOutOfBoundsMarker(source, viewport)
}

internal fun prepareMinimapOutOfBoundsMarker(
    source: MinimapMarkerSource,
    viewport: MinimapMarkerViewport,
): MinimapMarkerQuad? {
    val dx = source.chunkX - viewport.playerChunkX
    val dz = source.chunkZ - viewport.playerChunkZ
    val sin = viewport.rotation.fastSin()
    val cos = viewport.rotation.fastCos()
    val edgeX = dx * cos - dz * sin
    val edgeZ = dx * sin + dz * cos
    val maxAxis = max(abs(edgeX), abs(edgeZ))
    if (maxAxis <= viewport.viewDistance) {
        return null
    }

    val edgeFactor = viewport.viewDistance / maxAxis
    val boundedX = edgeX * edgeFactor
    val boundedZ = edgeZ * edgeFactor
    val markerBounds = if (abs(boundedX) >= abs(boundedZ)) {
        verticalMarkerBounds(boundedX, boundedZ, viewport)
    } else {
        horizontalMarkerBounds(boundedX, boundedZ, viewport)
    }
    return MinimapMarkerQuad(markerBounds, source.color)
}

private fun verticalMarkerBounds(
    edgeX: Float,
    edgeZ: Float,
    viewport: MinimapMarkerViewport,
): BoundingBox2f {
    val bounds = viewport.boundingBox
    val screenY = viewport.center.y + edgeZ * viewport.mapScale
    val centerY = screenY.coerceIn(bounds.yMin + MARKER_HALF, bounds.yMax - MARKER_HALF)
    val xMin = if (edgeX > 0.0F) bounds.xMax - MARKER_THICKNESS else bounds.xMin
    return BoundingBox2f(xMin, centerY - MARKER_HALF, xMin + MARKER_THICKNESS, centerY + MARKER_HALF)
}

private fun horizontalMarkerBounds(
    edgeX: Float,
    edgeZ: Float,
    viewport: MinimapMarkerViewport,
): BoundingBox2f {
    val bounds = viewport.boundingBox
    val screenX = viewport.center.x + edgeX * viewport.mapScale
    val centerX = screenX.coerceIn(bounds.xMin + MARKER_HALF, bounds.xMax - MARKER_HALF)
    val yMin = if (edgeZ > 0.0F) bounds.yMax - MARKER_THICKNESS else bounds.yMin
    return BoundingBox2f(centerX - MARKER_HALF, yMin, centerX + MARKER_HALF, yMin + MARKER_THICKNESS)
}
