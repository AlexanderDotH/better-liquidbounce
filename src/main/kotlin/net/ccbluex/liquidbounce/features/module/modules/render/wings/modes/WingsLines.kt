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

package net.ccbluex.liquidbounce.features.module.modules.render.wings.modes

import com.mojang.math.Axis
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.wings.config.WingsColorSettings
import net.ccbluex.liquidbounce.features.module.modules.render.wings.runtime.WingsMode
import net.ccbluex.liquidbounce.features.module.modules.render.wings.modes.WingsLines.WingsOptions.angles
import net.ccbluex.liquidbounce.features.module.modules.render.wings.modes.WingsLines.WingsOptions.linesCount
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawCustomMesh
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.setColor
import net.ccbluex.liquidbounce.render.withPush
import org.joml.Matrix4f

internal fun resolveWingsLineStep(angles: IntRange, lineCount: Int): Float =
    if (lineCount > 1) (angles.last - angles.first).toFloat() / (lineCount - 1) else 0f

internal fun resolveWingsShiftOffset(shifting: Float): Float = if (shifting == 0f) 0f else 27.5f

class WingsLines(parent: ModeValueGroup<*>) : WingsMode("Lines", parent) {

    private val colors = WingsColorSettings()

    private object WingsOptions : ValueGroup("WingsOptions") {
        val wingsLength by float("WingsLength", 1f, 0.1f..2f)
        val wingsWidth by float("WingsWidth", 0.05f, 0.01f..1f)
        val fadeStartRatio by float("FadeStartRatio", 0.5f, 0.1f..1f)
        val linesCount by int("LinesCount", 4, 1..12)
        val angles by intRange("Angles", 10..37, 1..90)
    }

    init {
        tree(WingsOptions)
        tree(colors)
    }

    override fun WorldRenderEnvironment.drawWings(isHurt: Boolean, bodyRot: Float, shifting: Float) {
        val currentColor = if (isHurt) Color4b.RED.alpha(colors.color.a) else colors.color
        val step = resolveWingsLineStep(angles, linesCount)
        poseStack.withPush {
            mulPose(Axis.XP.rotationDegrees(90f))
            mulPose(Axis.ZP.rotationDegrees(bodyRot))
            mulPose(Axis.XP.rotationDegrees(resolveWingsShiftOffset(shifting)))

            for (i in (0 until linesCount)) {
                val angle = angles.first.toFloat() + (i * step)
                drawWingLine(i, angle, currentColor)
            }
        }
    }

    private fun WorldRenderEnvironment.drawWingLine(index: Int, angle: Float, color: Color4b) {
        poseStack.withPush {
            translate(0.1 + (0.0375 * index), 0.0, -0.1 - (0.0375 * index))
            mulPose(Axis.YP.rotationDegrees(angle))
            drawWingPair(
                WingsOptions.wingsLength - 0.25f,
                WingsOptions.wingsWidth,
                color,
                secondX = WingsOptions.wingsLength,
            )
        }
        poseStack.withPush {
            translate(-0.1 - (0.0375 * index), 0.0, -0.1 - (0.0375 * index))
            mulPose(Axis.YP.rotationDegrees(-angle))
            drawWingPair(
                WingsOptions.wingsLength,
                WingsOptions.wingsWidth,
                color,
                secondX = WingsOptions.wingsLength - 0.25f,
            )
        }
    }

    private fun WorldRenderEnvironment.drawWingPair(
        firstX: Float,
        height: Float,
        color: Color4b,
        secondX: Float = firstX,
    ) {
        drawCustomMesh(ClientRenderPipelines.triangles(false)) { pose ->
            val matrix = pose.pose()
            val transparent = color.alpha(0)
            val rightSolid = firstX * WingsOptions.fadeStartRatio
            drawQuadSegment(matrix, 0f, rightSolid, height, color, color)
            drawQuadSegment(matrix, rightSolid, firstX, height, color, transparent)
            val leftSolid = -secondX * WingsOptions.fadeStartRatio
            drawQuadSegment(matrix, 0f, leftSolid, height, color, color)
            drawQuadSegment(matrix, leftSolid, -secondX, height, color, transparent)
        }
    }

    private fun WorldRenderEnvironment.drawQuadSegment(
        matrix: Matrix4f,
        firstX: Float,
        secondX: Float,
        height: Float,
        firstColor: Color4b,
        secondColor: Color4b,
    ) {
        drawCustomMesh(ClientRenderPipelines.triangles(false)) {
            val halfHeight = height / 2f
            addVertex(matrix, firstX, 0f, -halfHeight).setColor(firstColor)
            addVertex(matrix, secondX, 0f, -halfHeight).setColor(secondColor)
            addVertex(matrix, secondX, 0f, halfHeight).setColor(secondColor)
            addVertex(matrix, firstX, 0f, -halfHeight).setColor(firstColor)
            addVertex(matrix, secondX, 0f, halfHeight).setColor(secondColor)
            addVertex(matrix, firstX, 0f, halfHeight).setColor(firstColor)
        }
    }
}
