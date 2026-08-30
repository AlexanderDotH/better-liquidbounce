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
package net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.render

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.drawPlane
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.math.toFixed
import net.ccbluex.liquidbounce.utils.world.stronghold.EyeMeasurement
import net.ccbluex.liquidbounce.utils.world.stronghold.PosteriorSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

private const val RAY_RENDER_LENGTH = 2048.0

@JvmRecord
internal data class StrongholdRenderSettings(
    val showTopCandidates: Int,
    val renderRays: Boolean,
    val renderBestChunk: Boolean,
    val renderTopChunks: Boolean,
)

internal object PredictionRenderer {

    fun renderWorld(
        environment: WorldRenderEnvironment,
        measurements: List<EyeMeasurement>,
        snapshot: PosteriorSnapshot?,
        drawY: Double,
        settings: StrongholdRenderSettings,
    ) = with(environment) {
        if (settings.renderRays) {
            renderRays(measurements)
        }

        val candidates = snapshot?.candidates?.take(settings.showTopCandidates) ?: return@with
        candidates.forEachIndexed { index, candidate ->
            val chunkPos = candidate.chunkPos
            val alpha = (45 + candidate.probability * 170).toInt().coerceIn(30, 200)
            val color = if (index == 0) {
                Color4b(0, 170, 255, alpha)
            } else {
                Color4b(255, 170, 0, alpha)
            }

            if ((index == 0 && settings.renderBestChunk) || (index > 0 && settings.renderTopChunks)) {
                withPositionRelativeToCamera(chunkPos.minBlockX.toDouble(), drawY, chunkPos.minBlockZ.toDouble()) {
                    drawPlane(16f, 16f, color, color.darker())
                }
            }
        }
    }

    fun renderOverlay(
        event: OverlayRenderEvent,
        minecraft: Minecraft,
        moduleName: String,
        snapshot: PosteriorSnapshot,
        sigma: Float,
    ) {
        val best = snapshot.candidates.firstOrNull() ?: return
        val bestChunk = best.chunkPos
        val lines = arrayOf(
            moduleName,
            "Samples: ${snapshot.sampleCount} | Sigma: ${sigma.toFixed(3)}°",
            "Best chunk: ${bestChunk.x}, ${bestChunk.z} (${(best.probability * 100.0).toFixed(1)}%)",
            "/tp ${bestChunk.middleBlockX} ~ ${bestChunk.middleBlockZ}",
        )

        val centerX = minecraft.window.guiScaledWidth / 2
        val startY = minecraft.window.guiScaledHeight / 2 + 10
        lines.forEachIndexed { index, line ->
            val lineX = centerX - minecraft.font.width(line) / 2
            event.context.text(
                minecraft.font,
                line,
                lineX,
                startY + index * (minecraft.font.lineHeight + 1),
                Color4b.WHITE.argb,
            )
        }
    }

    private fun WorldRenderEnvironment.renderRays(measurements: List<EyeMeasurement>) {
        val color = Color4b.WHITE.alpha(170).argb
        withPositionRelativeToCamera {
            for ((start, angleDeg) in measurements) {
                val direction = Vec3.directionFromRotation(0f, angleDeg)
                drawLine(start, start.add(direction.scale(RAY_RENDER_LENGTH)), color)
            }
        }
    }
}
