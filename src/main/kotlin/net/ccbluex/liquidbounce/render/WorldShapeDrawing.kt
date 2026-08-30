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
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.utils.forEachVertex
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape


@JvmOverloads
fun WorldRenderEnvironment.drawTriangle(p1: Vec3f, p2: Vec3f, p3: Vec3f, argb: Int, noDepthTest: Boolean = true) {
    drawCustomMesh(ClientRenderPipelines.triangles(noDepthTest)) { matrix ->
        addVertex(matrix, p1).setColor(argb)
        addVertex(matrix, p2).setColor(argb)
        addVertex(matrix, p3).setColor(argb)
    }
}

/**
 * Function to draw a colored [box].
 */
fun WorldRenderEnvironment.drawBox(
    box: AABB,
    faceColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
    faceVertices: Int = -1,
    outlineVertices: Int = -1,
    noDepthTest: Boolean = true,
) {
    if (faceColor != null && !faceColor.isTransparent) {
        drawCustomMesh(ClientRenderPipelines.quads(noDepthTest)) { pose ->
            addBoxFaces(pose.pose(), box, color = faceColor, verticesToUse = faceVertices)
        }
    }

    if (outlineColor != null && !outlineColor.isTransparent) {
        drawCustomMesh(ClientRenderPipelines.lines(noDepthTest)) { pose ->
            addBoxOutlines(pose.pose(), box, outlineColor, outlineVertices)
        }
    }
}

fun WorldRenderEnvironment.drawShape(
    shape: VoxelShape,
    faceColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
) {
    if (faceColor != null && !faceColor.isTransparent) {
        drawCustomMesh(ClientRenderPipelines.quads(noDepthTest = true)) { pose ->
            addShapeFaces(pose.pose(), shape, color = faceColor)
        }
    }

    if (outlineColor != null && !outlineColor.isTransparent) {
        drawCustomMesh(ClientRenderPipelines.lines(noDepthTest = true)) { pose ->
            addShapeOutlines(pose.pose(), shape, outlineColor)
        }
    }
}

fun WorldRenderEnvironment.drawShapeSide(
    shape: VoxelShape,
    side: Direction,
    hitPos: Vec3,
    faceColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
) {
    if (faceColor != null && !faceColor.isTransparent) {
        drawCustomMesh(ClientRenderPipelines.quads(noDepthTest = true)) { pose ->
            addShapeSideFaces(pose.pose(), shape, side, hitPos, color = faceColor)
        }
    }

    if (outlineColor != null && !outlineColor.isTransparent) {
        drawCustomMesh(ClientRenderPipelines.lines(noDepthTest = true)) { pose ->
            addShapeSideOutlines(pose.pose(), shape, side, hitPos, outlineColor)
        }
    }
}

/**
 * Function to draw a colored [box] with specified [side].
 */
fun WorldRenderEnvironment.drawBoxSide(
    box: AABB,
    side: Direction,
    faceColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
) = drawBox(
    box,
    faceColor,
    outlineColor,
    faceVertices = BoxVertexIterator.FACE.sideMask(side),
    outlineVertices = BoxVertexIterator.OUTLINE.sideMask(side),
)

/**
 * Function to draw a colored [box] with specified [sides].
 */
fun WorldRenderEnvironment.drawBoxSides(
    box: AABB,
    sides: Iterable<Direction>,
    faceColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
) = drawBox(
    box,
    faceColor,
    outlineColor,
    faceVertices = BoxVertexIterator.FACE.sideMask(sides),
    outlineVertices = BoxVertexIterator.OUTLINE.sideMask(sides),
)

/**
 * Function to draw a flat plane on the XZ axis with an optional outline.
 */
fun WorldRenderEnvironment.drawPlane(
    sizeX: Float,
    sizeZ: Float,
    fillColor: Color4b? = Color4b.TRANSPARENT,
    outlineColor: Color4b? = Color4b.TRANSPARENT,
    noDepthTest: Boolean = true
) {
    if (fillColor != null && !fillColor.isTransparent) {
        val argb = fillColor.argb
        drawCustomMesh(ClientRenderPipelines.quads(noDepthTest = noDepthTest)) { matrix ->
            addVertex(matrix, 0f, 0f, 0f).setColor(argb)
            addVertex(matrix, 0f, 0f, sizeZ).setColor(argb)
            addVertex(matrix, sizeX, 0f, sizeZ).setColor(argb)
            addVertex(matrix, sizeX, 0f, 0f).setColor(argb)
        }
    }

    if (outlineColor != null && !outlineColor.isTransparent) {
        val argb = outlineColor.argb
        drawCustomMesh(ClientRenderPipelines.lines(noDepthTest = noDepthTest)) { matrix ->
            addVertex(matrix, 0f, 0f, 0f).setColor(argb)
            addVertex(matrix, 0f, 0f, sizeZ).setColor(argb)

            addVertex(matrix, 0f, 0f, sizeZ).setColor(argb)
            addVertex(matrix, sizeX, 0f, sizeZ).setColor(argb)

            addVertex(matrix, sizeX, 0f, sizeZ).setColor(argb)
            addVertex(matrix, sizeX, 0f, 0f).setColor(argb)

            addVertex(matrix, sizeX, 0f, 0f).setColor(argb)
            addVertex(matrix, 0f, 0f, 0f).setColor(argb)
        }
    }
}
