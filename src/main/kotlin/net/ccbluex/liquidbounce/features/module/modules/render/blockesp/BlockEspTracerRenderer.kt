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

import net.ccbluex.liquidbounce.features.module.modules.render.tracers.drawTracerBatch
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

internal object BlockEspTracerRenderer {

    fun render(
        event: WorldRenderEvent,
        sources: Collection<BlockTracerSource>,
        maximumDistance: Double,
        lineWidth: Float,
        style: EspGlowStyle,
        colorProvider: (BlockPos, BlockState) -> Color4b,
    ) {
        val cameraPosition = event.camera.position()
        val batch = createBlockTracerBatch(
            targets = collectBlockTracerTargets(sources),
            eyePosition = Vec3f.eyeVector(event.camera),
            cameraPosition = cameraPosition,
            maximumDistanceSquared = maximumDistance * maximumDistance,
            lineWidth = lineWidth,
            colorProvider = colorProvider,
        )
        event.renderEnvironment {
            drawTracerBatch(batch, glowMask = false)
        }
        batch.contributeGlowIfPresent {
            EspShaderRenderer.contributeGlow(event, EspGlowSource.BLOCK_ESP_TRACERS, style) {
                drawTracerBatch(it, glowMask = true)
            }
        }
    }
}
