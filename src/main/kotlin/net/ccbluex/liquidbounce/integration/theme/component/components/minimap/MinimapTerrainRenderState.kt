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

import net.ccbluex.liquidbounce.render.drawCustomElement
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.level.ChunkPos

internal data class MinimapTerrainRenderState(
    val enabled: Boolean,
    val vertexColor: () -> Color4b,
    val baseX: Int,
    val baseZ: Int,
    val chunksToRenderAround: Int,
    val viewDistance: Float,
)

internal fun GuiGraphicsExtractor.drawMinimapTerrain(
    bounds: ScreenRectangle,
    state: MinimapTerrainRenderState,
) {
    if (!state.enabled) {
        return
    }

    drawCustomElement(
        pipeline = RenderPipelines.GUI_TEXTURED,
        textureSetup = ChunkRenderer.prepareRendering(),
        bounds = bounds,
    ) { pose ->
        val color = state.vertexColor().argb
        for (x in -state.chunksToRenderAround..state.chunksToRenderAround) {
            for (z in -state.chunksToRenderAround..state.chunksToRenderAround) {
                if (x * x + z * z > (state.viewDistance + 3).sq()) {
                    continue
                }

                val chunkPos = ChunkPos.pack(state.baseX + x, state.baseZ + z)
                val texture = ChunkRenderer.getAtlasPosition(chunkPos).uv
                val fromX = x.toFloat()
                val fromY = z.toFloat()
                val toX = fromX + 1.0F
                val toY = fromY + 1.0F
                addVertexWith2DPose(pose, fromX, fromY).setUv(texture.xMin, texture.yMin).setColor(color)
                addVertexWith2DPose(pose, fromX, toY).setUv(texture.xMin, texture.yMax).setColor(color)
                addVertexWith2DPose(pose, toX, toY).setUv(texture.xMax, texture.yMax).setColor(color)
                addVertexWith2DPose(pose, toX, fromY).setUv(texture.xMax, texture.yMin).setColor(color)
            }
        }
    }
}
