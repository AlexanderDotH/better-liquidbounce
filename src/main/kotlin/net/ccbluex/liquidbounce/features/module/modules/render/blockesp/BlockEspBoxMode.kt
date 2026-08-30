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

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.CachedMeshStorage
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.addShapeFaces
import net.ccbluex.liquidbounce.render.addShapeOutlines
import net.ccbluex.liquidbounce.render.buildMesh
import net.ccbluex.liquidbounce.render.drawGenericBlockESP
import net.ccbluex.liquidbounce.render.translate
import net.ccbluex.liquidbounce.render.withPush
import org.joml.Matrix4f

internal open class BlockEspBoxMode(
    private val runtime: BlockEspRuntime,
) : BlockEspMode("Box") {
    private val outline by boolean("Outline", true).onChanged {
        if (!it && running) outlinesRenderState.clearStates()
    }
    private val facesRenderState by lazy { CachedMeshStorage("BlockESP $name Faces") }
    private val outlinesRenderState by lazy { CachedMeshStorage("BlockESP $name Outlines") }

    override fun disable() {
        facesRenderState.clearStates()
        facesRenderState.clearBuffers()
        outlinesRenderState.clearStates()
        outlinesRenderState.clearBuffers()
        super.disable()
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (outline) {
            mc.gameRenderer.mainRenderTarget().drawGenericBlockESP(
                outlinesRenderState,
                ClientRenderPipelines.relativeLines(useColor),
                runtime.distanceFadeSettings,
            ) { runtime.modeTransforms(useColor, Matrix4f(event.modelViewMatrix), 150) }
        }
        mc.gameRenderer.mainRenderTarget().drawGenericBlockESP(
            facesRenderState,
            ClientRenderPipelines.relativeQuads(useColor),
            runtime.distanceFadeSettings,
        ) { runtime.modeTransforms(useColor, Matrix4f(event.modelViewMatrix)) }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (runtime.trackerIsEmpty()) {
            facesRenderState.clearStates()
            outlinesRenderState.clearStates()
            return@handler
        }
        if (!dirtyFlag.compareAndSet(expect = true, update = false)) return@handler

        val colorMode = runtime.activeBlockColorMode
        useColor = colorMode.isParamSensitive
        val mergedShapes = runtime.collectBlockShapes(colorMode, useColor)
        facesRenderState.buildMesh(
            pipeline = ClientRenderPipelines.relativeQuads(useColor),
            origin = player.blockPosition(),
        ) { pose, origin ->
            mergedShapes.forEach { mergedShape ->
                pose.withPush {
                    translate(mergedShape.blockPos, origin)
                    addShapeFaces(last().pose(), mergedShape.shape, mergedShape.key.color)
                }
            }
        }
        if (outline) buildOutlineMesh(mergedShapes)
    }

    private fun buildOutlineMesh(mergedShapes: List<net.ccbluex.liquidbounce.utils.math.PositionedVoxelShape<BlockMergeKey>>) {
        outlinesRenderState.buildMesh(
            pipeline = ClientRenderPipelines.relativeLines(useColor),
            origin = player.blockPosition(),
        ) { pose, origin ->
            mergedShapes.forEach { mergedShape ->
                pose.withPush {
                    translate(mergedShape.blockPos, origin)
                    addShapeOutlines(last().pose(), mergedShape.shape, mergedShape.key.color)
                }
            }
        }
    }
}
