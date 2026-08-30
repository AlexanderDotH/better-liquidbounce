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

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.math.fastCos
import net.ccbluex.liquidbounce.utils.math.fastSin
import net.ccbluex.liquidbounce.utils.math.forAllFaces
import net.ccbluex.liquidbounce.utils.math.forAllSideFaces
import net.ccbluex.liquidbounce.utils.math.forAllSideOutlineEdges
import net.ccbluex.liquidbounce.render.buffer.begin
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import org.joml.Matrix4fc

internal fun VertexConsumer.addColoredVertex(
    pose: Matrix4fc,
    x: Double,
    y: Double,
    z: Double,
    color: Color4b?,
) {
    addVertex(pose, x, y, z)
    if (color != null) {
        setColor(color.argb)
    }
}

fun segmentAngle(i: Int, segments: Int) = i * Mth.TWO_PI / segments

fun VertexConsumer.addTorusQuad(
    pose: PoseStack.Pose,
    innerSegments: Int,
    outerCurAngle: Float,
    outerNextAngle: Float,
    outerCurRadius: Float,
    outerNextRadius: Float,
    innerRadius: Float,
    innerI: Int,
    color: Color4b,
) {
    val innerCurAngle = segmentAngle(innerI, innerSegments)
    val innerNextAngle = segmentAngle(innerI + 1, innerSegments)

    val curMainSin = outerCurAngle.fastSin()
    val curMainCos = outerCurAngle.fastCos()
    val nextMainSin = outerNextAngle.fastSin()
    val nextMainCos = outerNextAngle.fastCos()

    val innerCurSin = innerCurAngle.fastSin()
    val innerCurCos = innerCurAngle.fastCos()
    val innerNextSin = innerNextAngle.fastSin()
    val innerNextCos = innerNextAngle.fastCos()

    val curTubeY = innerRadius * innerCurSin
    val nextTubeY = innerRadius * innerNextSin
    val curTubeOffset = innerRadius * innerCurCos
    val nextTubeOffset = innerRadius * innerNextCos

    val p1Radius = outerCurRadius + curTubeOffset
    val p2Radius = outerCurRadius + nextTubeOffset
    val p3Radius = outerNextRadius + curTubeOffset
    val p4Radius = outerNextRadius + nextTubeOffset

    addTorusVertex(pose, nextMainSin, nextMainCos, curTubeY, p3Radius, color)
    addTorusVertex(pose, curMainSin, curMainCos, curTubeY, p1Radius, color)
    addTorusVertex(pose, curMainSin, curMainCos, nextTubeY, p2Radius, color)
    addTorusVertex(pose, nextMainSin, nextMainCos, nextTubeY, p4Radius, color)
}

/**
 * Build new mesh data and upload it.
 *
 * @param origin a preferred origin; the lambda receives the resolved origin that must be used
 * for relative vertex positions.
 */
inline fun CachedMeshStorage.buildMesh(
    pipeline: RenderPipeline,
    origin: BlockPos = BlockPos.ZERO,
    block: VertexConsumer.(pose: PoseStack, origin: BlockPos) -> Unit,
) {
    clearStates()
    val resolvedOrigin = this.resolveBaseBlockPos(origin)

    val bufferBuilder = this.byteBufferBuilder.begin(pipeline)
    usePoseStack {
        bufferBuilder.block(this, resolvedOrigin)
    }

    bufferBuilder.build()?.use { meshData ->
        this.uploadAndSet(meshData, pipeline)
    }

    this.byteBufferBuilder.clear()
}
