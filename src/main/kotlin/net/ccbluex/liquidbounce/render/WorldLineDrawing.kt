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

package net.ccbluex.liquidbounce.render

import net.ccbluex.fastutil.objectObjectMapOf
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.utils.VertexList
import net.ccbluex.liquidbounce.render.utils.forEachVertex
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * Draws a line with endpoint [p1] and [p2] and color [argb].
 */
fun WorldRenderEnvironment.drawLine(p1: Vec3, p2: Vec3, argb: Int) =
    drawCustomMesh(ClientRenderPipelines.lines(noDepthTest = true)) { pose ->
        addVertex(pose, p1).setColor(argb)
        addVertex(pose, p2).setColor(argb)
    }

/**
 * Draws lines with [width].
 * Modern GL doesn't support `glLineWidth` well, so draw with shader simulation.
 */
fun WorldRenderEnvironment.drawLinesWithWidth(argb: Int, width: Float, vararg positions: Vec3f) {
    if (positions.isEmpty()) return
    require(positions.size and 1 == 0)

    drawCustomMesh(pipeline = ClientRenderPipelines.LinesWithWidth) { pose ->
        for (i in 0 until positions.size step 2) {
            val p1 = positions[i]
            val p2 = positions[i + 1]
            val norm1 = (p1 - p2).normalized()
            addVertex(pose, p1)
                .setColor(argb)
                .setNormal(pose, norm1)
                .setLineWidth(width)
            addVertex(pose, p2)
                .setColor(argb)
                .setNormal(pose, -norm1)
                .setLineWidth(width)
        }
    }
}

fun WorldRenderEnvironment.drawLinesWithWidth(argb: Int, width: Float, positions: VertexList) {
    if (positions.size == 0) return
    require(positions.size and 1 == 0)

    val p1 = Vector3f()
    val p2 = Vector3f()
    val norm1 = Vector3f()
    drawCustomMesh(pipeline = ClientRenderPipelines.LinesWithWidth) { pose ->
        for (i in 0 until positions.size step 2) {
            positions.vec(i, p1)
            positions.vec(i + 1, p2)
            val norm1 = p1.sub(p2, norm1).normalize()

            addVertex(pose, p1)
                .setColor(argb)
                .setNormal(pose, norm1)
                .setLineWidth(width)
            addVertex(pose, p2)
                .setColor(argb)
                .setNormal(pose, norm1.negate())
                .setLineWidth(width)
        }
    }
}

/**
 * Function to draw lines using the specified [positions] vectors.
 *
 * @param positions The vectors representing the lines.
 */
fun WorldRenderEnvironment.drawLines(argb: Int, vararg positions: Vec3f) {
    if (positions.isEmpty()) return
    require(positions.size and 1 == 0)

    drawCustomMesh(pipeline = ClientRenderPipelines.lines(noDepthTest = true)) { pose ->
        for (pos in positions) {
            addVertex(pose, pos).setColor(argb)
        }
    }
}

fun WorldRenderEnvironment.drawLines(argb: Int, positions: VertexList) {
    if (positions.size == 0) return
    require(positions.size and 1 == 0)

    drawCustomMesh(pipeline = ClientRenderPipelines.lines(noDepthTest = true)) { pose ->
        positions.forEachVertex { x, y, z ->
            addVertex(pose, x, y, z).setColor(argb)
        }
    }
}

/**
 * Function to draw a line strip using the specified [positions] vectors.
 *
 * @param positions The vectors representing the line strip.
 */
fun WorldRenderEnvironment.drawLineStrip(argb: Int, vararg positions: Vec3f) {
    if (positions.isEmpty()) return

    drawCustomMesh(pipeline = ClientRenderPipelines.LineStrip) { pose ->
        for (pos in positions) {
            addVertex(pose, pos).setColor(argb)
        }
    }
}

fun WorldRenderEnvironment.drawLineStrip(argb: Int, positions: VertexList) {
    if (positions.size == 0) return

    drawCustomMesh(pipeline = ClientRenderPipelines.LineStrip) { pose ->
        positions.forEachVertex { x, y, z ->
            addVertex(pose, x, y, z).setColor(argb)
        }
    }
}
