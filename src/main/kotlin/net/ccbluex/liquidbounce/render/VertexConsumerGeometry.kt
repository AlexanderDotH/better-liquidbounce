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



@file:JvmName("VertexBuilderKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.utils.math.fastCos
import net.ccbluex.liquidbounce.utils.math.fastSin
import net.ccbluex.liquidbounce.utils.math.forAllFaces
import net.ccbluex.liquidbounce.utils.math.forAllSideFaces
import net.ccbluex.liquidbounce.utils.math.forAllSideOutlineEdges
import net.ccbluex.liquidbounce.render.buffer.begin
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Matrix4fc
import org.joml.Vector3fc

inline fun VertexConsumer.addVertex(pose: Matrix4fc, x: Double, y: Double, z: Double): VertexConsumer =
    addVertex(pose, x.toFloat(), y.toFloat(), z.toFloat())

inline fun VertexConsumer.addVertex(pose: PoseStack.Pose, x: Double, y: Double, z: Double): VertexConsumer =
    addVertex(pose.pose(), x, y, z)

inline fun VertexConsumer.addVertex(pose: Matrix4fc, pos: Vec3): VertexConsumer =
    addVertex(pose, pos.x, pos.y, pos.z)

inline fun VertexConsumer.addVertex(pose: PoseStack.Pose, pos: Vec3): VertexConsumer =
    addVertex(pose, pos.x, pos.y, pos.z)

inline fun VertexConsumer.addVertex(pose: Matrix4fc, pos: Vec3f): VertexConsumer =
    addVertex(pose, pos.x, pos.y, pos.z)

inline fun VertexConsumer.addVertex(pose: PoseStack.Pose, pos: Vec3f): VertexConsumer =
    addVertex(pose, pos.x, pos.y, pos.z)

inline fun VertexConsumer.addVertex(pose: Matrix4fc, pos: Vector3fc): VertexConsumer =
    addVertex(pose, pos.x(), pos.y(), pos.z())

inline fun VertexConsumer.addVertex(pose: PoseStack.Pose, pos: Vector3fc): VertexConsumer =
    addVertex(pose, pos.x(), pos.y(), pos.z())

inline fun VertexConsumer.setNormal(pose: PoseStack.Pose, normalVector: Vec3f): VertexConsumer =
    setNormal(pose, normalVector.x, normalVector.y, normalVector.z)

inline fun VertexConsumer.setColor(color: Color4b): VertexConsumer = setColor(color.argb)

fun VertexConsumer.addBoxOutlines(
    pose: Matrix4fc,
    box: AABB,
    color: Color4b? = null,
    verticesToUse: Int = -1,
) {
    val checkNeeded = verticesToUse and 0xFFFFFF != 0xFFFFFF

    box.forEachOutlineVertex { i, x, y, z ->
        if (checkNeeded && (verticesToUse and (1 shl i)) == 0) {
            return@forEachOutlineVertex
        }

        addVertex(pose, x, y, z)
        if (color != null) setColor(color.argb)
    }
}

fun VertexConsumer.addBoxFaces(
    pose: Matrix4fc,
    box: AABB,
    color: Color4b? = null,
    verticesToUse: Int = -1,
) {
    val checkNeeded = verticesToUse and 0xFFFFFF != 0xFFFFFF

    box.forEachFaceVertex { i, x, y, z ->
        if (checkNeeded && (verticesToUse and (1 shl i)) == 0) {
            return@forEachFaceVertex
        }

        addVertex(pose, x, y, z)
        if (color != null) setColor(color.argb)
    }
}

fun VertexConsumer.addShapeFaces(
    pose: Matrix4fc,
    shape: VoxelShape,
    color: Color4b? = null,
) {
    shape.forAllFaces { direction, minX, minY, minZ, maxX, maxY, maxZ ->
        addFaceVertices(pose, direction, minX, minY, minZ, maxX, maxY, maxZ, color)
    }
}

fun VertexConsumer.addShapeOutlines(
    pose: Matrix4fc,
    shape: VoxelShape,
    color: Color4b? = null,
) {
    shape.forAllEdges { startX, startY, startZ, endX, endY, endZ ->
        addVertex(pose, startX, startY, startZ)
        if (color != null) setColor(color.argb)

        addVertex(pose, endX, endY, endZ)
        if (color != null) setColor(color.argb)
    }
}

fun VertexConsumer.addShapeSideFaces(
    pose: Matrix4fc,
    shape: VoxelShape,
    side: Direction,
    hitPos: Vec3,
    color: Color4b? = null,
) {
    shape.forAllSideFaces(side, hitPos) { direction, minX, minY, minZ, maxX, maxY, maxZ ->
        addFaceVertices(pose, direction, minX, minY, minZ, maxX, maxY, maxZ, color)
    }
}

fun VertexConsumer.addShapeSideOutlines(
    pose: Matrix4fc,
    shape: VoxelShape,
    side: Direction,
    hitPos: Vec3,
    color: Color4b? = null,
) {
    shape.forAllSideOutlineEdges(side, hitPos) { startX, startY, startZ, endX, endY, endZ ->
        addVertex(pose, startX, startY, startZ)
        if (color != null) setColor(color.argb)

        addVertex(pose, endX, endY, endZ)
        if (color != null) setColor(color.argb)
    }
}

internal fun VertexConsumer.addFaceVertices(
    pose: Matrix4fc,
    direction: Direction,
    minX: Double,
    minY: Double,
    minZ: Double,
    maxX: Double,
    maxY: Double,
    maxZ: Double,
    color: Color4b?,
) {
    val vertices = faceVertexCoordinates(direction, minX, minY, minZ, maxX, maxY, maxZ)
    for (index in vertices.indices step 3) {
        addColoredVertex(pose, vertices[index], vertices[index + 1], vertices[index + 2], color)
    }
}

@Suppress("LongParameterList")
internal fun faceVertexCoordinates(
    direction: Direction,
    minX: Double,
    minY: Double,
    minZ: Double,
    maxX: Double,
    maxY: Double,
    maxZ: Double,
): DoubleArray = when (direction) {
    Direction.DOWN -> doubleArrayOf(
        minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
    )
    Direction.UP -> doubleArrayOf(
        minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ,
    )
    Direction.NORTH -> doubleArrayOf(
        minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ,
    )
    Direction.EAST -> doubleArrayOf(
        maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ,
    )
    Direction.SOUTH -> doubleArrayOf(
        minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
    )
    Direction.WEST -> doubleArrayOf(
        minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
    )
}
