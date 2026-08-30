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

/**
 * @author minecrrrr
 */
internal class HatsFlower(parent: ModeValueGroup<*>) : HatsMode("Flower", parent) {

    private val colors = HatsColorSettings()

    private object HatFlowerSettings : ValueGroup("HatSettings") {
        val outerRadius by float("Radius", 0.3f, 0.1f..2f)
        val innerRadius by float("Thickness", 0.05f, 0.01f..1f)
        val sharpness by float("Sharpness", 0.6f, 0.1f..0.9f)
        val petalCount by int("PetalCount", 5, 5..15)
        val spinSpeed by float("SpinSpeed", 1f, -10f..10f)
    }

    init {
        tree(HatFlowerSettings)
        tree(colors)
    }

    override fun WorldRenderEnvironment.drawHat(isHurt: Boolean) {
        val rotAngle = getRotationAngle(HatFlowerSettings.spinSpeed)
        withHatRotation(rotAngle) {
            drawCustomMesh(ClientRenderPipelines.quads(noDepthTest = true)) { matrix ->
                drawFlower(matrix, isHurt)
            }
        }
    }

    private fun VertexConsumer.drawFlower(matrix: PoseStack.Pose, isHurt: Boolean) {
        val petals = HatFlowerSettings.petalCount
        val outerSegments = petals * 32
        val innerSegments = 12
        for (outerIndex in 0 until outerSegments) {
            val currentAngle = segmentAngle(outerIndex, outerSegments)
            val nextAngle = segmentAngle(outerIndex + 1, outerSegments)
            val currentRadius = getFlowerRadius(
                currentAngle, HatFlowerSettings.outerRadius, petals, HatFlowerSettings.sharpness,
            )
            val nextRadius = getFlowerRadius(
                nextAngle, HatFlowerSettings.outerRadius, petals, HatFlowerSettings.sharpness,
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
                    HatFlowerSettings.innerRadius,
                    innerIndex,
                    color,
                )
            }
        }
    }

    private fun getFlowerRadius(angle: Float, baseRadius: Float, points: Int, sharpness: Float): Float {
        val innerRadius = baseRadius * sharpness
        val f = Mth.PI / points
        val r = abs(angle % (f * 2) - f) / f

        return innerRadius + (baseRadius - innerRadius) * (1f - r)
    }

}
