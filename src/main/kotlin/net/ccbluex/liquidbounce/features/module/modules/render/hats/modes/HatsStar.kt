/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
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

package net.ccbluex.liquidbounce.features.module.modules.render.hats.modes

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.hats.config.HatsColorSettings
import net.ccbluex.liquidbounce.features.module.modules.render.hats.runtime.HatsMode
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.addTorusQuad
import net.ccbluex.liquidbounce.render.drawCustomMesh
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.segmentAngle
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.pow

/**
 * @author minecrrrr
 */
internal class HatsStar(parent: ModeValueGroup<*>) : HatsMode("Star", parent) {

    private val colors = HatsColorSettings()

    private object HatStarSettings : ValueGroup("HatSettings") {
        val outerRadius by float("Radius", 0.3f, 0.1f..2f)
        val innerRadius by float("Thickness", 0.05f, 0.01f..1f)
        val sharpness by float("Sharpness", 0.6f, 0.1f..0.7f)
        val pointsCount by int("PointsCount", 5, 5..15)
        val spinSpeed by float("SpinSpeed", 1f, -10f..10f)
    }

    init {
        tree(HatStarSettings)
        tree(colors)
    }

    override fun WorldRenderEnvironment.drawHat(isHurt: Boolean) {
        val rotAngle = getRotationAngle(HatStarSettings.spinSpeed)
        withHatRotation(rotAngle) {
            drawCustomMesh(ClientRenderPipelines.quads(noDepthTest = true)) { matrix ->
                drawStar(matrix, isHurt)
            }
        }
    }

    private fun VertexConsumer.drawStar(matrix: PoseStack.Pose, isHurt: Boolean) {
        val points = HatStarSettings.pointsCount
        val outerSegments = points * 32
        val innerSegments = 12
        for (outerIndex in 0 until outerSegments) {
            val currentAngle = segmentAngle(outerIndex, outerSegments)
            val nextAngle = segmentAngle(outerIndex + 1, outerSegments)
            val currentRadius = getStarRadius(
                currentAngle, HatStarSettings.outerRadius, points, HatStarSettings.sharpness, 1.75F,
            )
            val nextRadius = getStarRadius(
                nextAngle, HatStarSettings.outerRadius, points, HatStarSettings.sharpness, 1.75F,
            )
            val color = if (isHurt) {
                Color4b(255, 0, 0, colors.firstColor.a)
            } else {
                colors.getCurrentStepColor(currentAngle)
            }
            for (innerIndex in 0 until innerSegments) {
                addTorusQuad(
                    matrix,
                    innerSegments,
                    currentAngle,
                    nextAngle,
                    currentRadius,
                    nextRadius,
                    HatStarSettings.innerRadius,
                    innerIndex,
                    color,
                )
            }
        }
    }

    private fun getStarRadius(angle: Float, baseRadius: Float, points: Int, sharpness: Float, exponent: Float): Float {
        val section = Mth.TWO_PI / points
        val m = (angle % section) / section
        val dist = abs(m * 2.0F - 1.0F)
        val linearProgress = 1.0F - dist

        return (baseRadius * (1.0F - sharpness + sharpness * linearProgress.pow(exponent)))
    }

}
