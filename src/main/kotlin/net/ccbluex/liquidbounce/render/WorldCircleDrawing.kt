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

import com.mojang.blaze3d.buffers.GpuBufferSlice
import net.ccbluex.fastutil.objectObjectMapOf
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.forEachVertex
import net.ccbluex.liquidbounce.render.utils.UnitCircle
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.minecraft.util.Mth
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Function to draw a circle of the size [outerRadius] with a cutout of size [innerRadius]
 *
 * @param outerRadius The radius of the circle
 * @param innerRadius The radius inside the circle (the cutout)
 * @param outerColor The color of the outer edges
 * @param innerColor The color of the inner edges
 */
fun WorldRenderEnvironment.drawGradientCircle(
    outerRadius: Float,
    innerRadius: Float,
    outerColor: Color4b,
    innerColor: Color4b,
    innerOffset: Vector3fc = Vector3f(),
    noDepthTest: Boolean = true,
) {
    if (outerRadius <= 0f || outerColor.isTransparent && innerColor.isTransparent) {
        return
    }

    if (Mth.equal(innerOffset.lengthSquared(), 0f)) {
        val innerRatio = (innerRadius / outerRadius).coerceIn(0f, 1f)

        drawGradientCircleQuad(outerRadius, outerColor, innerColor, innerRatio, noDepthTest)
        return
    }

    drawCustomMesh(ClientRenderPipelines.triangleStrip(noDepthTest)) { matrix ->
        val innerP = Vector3f()
        val outerP = Vector3f()
        UnitCircle.forEach { cosine, sine ->
            outerP.set(cosine * outerRadius, 0f, sine * outerRadius)
            innerP.set(cosine * innerRadius, 0f, sine * innerRadius).add(innerOffset)

            addVertex(matrix, outerP).setColor(outerColor.argb)
            addVertex(matrix, innerP).setColor(innerColor.argb)
        }
    }
}

private fun WorldRenderEnvironment.drawGradientCircleQuad(
    radius: Float,
    outerColor: Color4b,
    innerColor: Color4b,
    innerRatio: Float,
    noDepthTest: Boolean,
) {
    fun packColorRG(color: Color4b): Int =
        ((color.r and 0xFF) shl 8) or (color.g and 0xFF)

    fun packColorBA(color: Color4b): Int =
        ((color.b and 0xFF) shl 8) or (color.a and 0xFF)

    val outerRg = packColorRG(outerColor)
    val outerBa = packColorBA(outerColor)
    val innerRg = packColorRG(innerColor)
    val innerBa = packColorBA(innerColor)

    drawCustomMesh(ClientRenderPipelines.gradientCircle(noDepthTest)) { matrix ->
        addVertex(matrix, -radius, 0f, -radius)
            .setUv(0f, 0f)
            .setUv1(outerRg, outerBa)
            .setUv2(innerRg, innerBa)
            .setLineWidth(innerRatio)
        addVertex(matrix, -radius, 0f, radius)
            .setUv(0f, 1f)
            .setUv1(outerRg, outerBa)
            .setUv2(innerRg, innerBa)
            .setLineWidth(innerRatio)
        addVertex(matrix, radius, 0f, radius)
            .setUv(1f, 1f)
            .setUv1(outerRg, outerBa)
            .setUv2(innerRg, innerBa)
            .setLineWidth(innerRatio)
        addVertex(matrix, radius, 0f, -radius)
            .setUv(1f, 0f)
            .setUv1(outerRg, outerBa)
            .setUv2(innerRg, innerBa)
            .setLineWidth(innerRatio)
    }
}

private fun WorldRenderEnvironment.drawRoundedRectQuad(
    radius: Float,
    argb: Int,
    noDepthTest: Boolean,
    uniform: GpuBufferSlice,
) {
    drawCustomMesh(
        pipeline = ClientRenderPipelines.roundedRect(noDepthTest),
        uniforms = objectObjectMapOf(ClientUniformDefine.ROUNDED_RECT.uboName, uniform),
    ) { pose ->
        addVertex(pose, -radius, 0f, -radius).setUv(0f, 0f).setColor(argb)
        addVertex(pose, -radius, 0f, radius).setUv(0f, 1f).setColor(argb)
        addVertex(pose, radius, 0f, radius).setUv(1f, 1f).setColor(argb)
        addVertex(pose, radius, 0f, -radius).setUv(1f, 0f).setColor(argb)
    }
}

fun WorldRenderEnvironment.drawCircle(
    radius: Float,
    color: Color4b,
) {
    if (radius <= 0f || color.isTransparent) {
        return
    }

    drawGradientCircleQuad(
        radius = radius,
        outerColor = color,
        innerColor = color,
        innerRatio = 0f,
        noDepthTest = true,
    )
}

/**
 * Function to draw the outline of a circle of the size [radius]
 *
 * @param radius The radius
 * @param color The color
 */
@JvmOverloads
fun WorldRenderEnvironment.drawCircleOutline(radius: Float, color: Color4b, noDepthTest: Boolean = true) {
    if (radius <= 0f || color.isTransparent) {
        return
    }

    drawRoundedRectQuad(
        radius = radius,
        argb = color.argb,
        noDepthTest = noDepthTest,
        uniform = ROUNDED_RECT_AS_OUTLINE_CIRCLE_UBO,
    )
}
