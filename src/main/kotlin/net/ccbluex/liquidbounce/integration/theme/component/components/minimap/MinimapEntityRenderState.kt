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

import net.ccbluex.liquidbounce.features.module.modules.render.esp.ModuleESP
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawTriangle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentRotation
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec2

internal data class MinimapEntityRenderState(
    val entities: Iterable<LivingEntity>,
    val tickDelta: Float,
    val baseX: Float,
    val baseZ: Float,
    val scale: Float,
)

internal fun GuiGraphicsExtractor.drawMinimapEntities(state: MinimapEntityRenderState) {
    for (entity in state.entities) {
        drawMinimapEntity(entity, state)
    }
}

private fun GuiGraphicsExtractor.drawMinimapEntity(
    entity: LivingEntity,
    state: MinimapEntityRenderState,
) {
    val color = ModuleESP.getColor(entity)
    val pos = entity.interpolateCurrentPosition(state.tickDelta)
    val rot = entity.interpolateCurrentRotation(state.tickDelta)

    pose().pushMatrix()
    pose().translate(pos.x.toFloat() / 16.0F - state.baseX, pos.z.toFloat() / 16.0F - state.baseZ)
    pose().rotate(rot.yaw.toRadians())
    pose().scale(state.scale)

    val width = 2.0F
    val height = width * 1.618F
    val p1 = Vec2(-width * 0.5F / 16.0F, -height * 0.5F / 16.0F)
    val p2 = Vec2(0.0F, height * 0.5F / 16.0F)
    val p3 = Vec2(width * 0.5F / 16.0F, -height * 0.5F / 16.0F)

    pose().pushMatrix()
    pose().translate(
        -width / 5.0F * ChunkRenderer.SUN_DIRECTION.x() / 16.0F,
        -width / 5.0F * ChunkRenderer.SUN_DIRECTION.y() / 16.0F,
    )
    drawTriangle(p1, p2, p3, shadowColor(color))
    pose().popMatrix()

    drawTriangle(p1, p2, p3, color)
    pose().popMatrix()
}

private fun shadowColor(color: Color4b) = Color4b(
    (color.r * 0.1).toInt(),
    (color.g * 0.1).toInt(),
    (color.b * 0.1).toInt(),
    200,
)

internal fun GuiGraphicsExtractor.drawMinimapOutOfBoundsEntityMarkers(
    entities: Iterable<LivingEntity>,
    tickDelta: Float,
    viewport: MinimapMarkerViewport,
) {
    for (entity in entities) {
        val color = ModuleESP.getColor(entity)
        val pos = entity.interpolateCurrentPosition(tickDelta)
        val source = MinimapMarkerSource(
            chunkX = pos.x.toFloat() / 16.0F,
            chunkZ = pos.z.toFloat() / 16.0F,
            color = color,
        )
        val marker = prepareMinimapOutOfBoundsMarker(source, viewport) ?: continue
        val bounds = marker.boundingBox
        drawQuad(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, marker.color)
    }
}
