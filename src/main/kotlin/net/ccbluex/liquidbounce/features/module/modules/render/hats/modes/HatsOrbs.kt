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
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.hats.runtime.HatsMode
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawCustomMesh
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.segmentAngle
import kotlin.math.cos
import kotlin.math.sin

/**
 * @author minecrrrr
 */
internal class HatsOrbs(parent: ModeValueGroup<*>) : HatsMode("Orbs", parent) {

    val color by color("color", Color4b(0, 0, 255, 125))

    private val settings = HatOrbsSettings()
    private val waveSettings = WaveSettings()

    private class HatOrbsSettings : ValueGroup("HatSettings") {
        val radius by float("Radius", 0.5f, 0f..2f)
        val speed by float("Speed", 0.5f, 0.1f..10f)
        val size by float("OrbsSize", 0.1f, 0.01f..0.5f)
        val count by int("OrbsCount", 6, 1..12)
        val spinSpeed by float("SpinSpeed", 2f, -10f..10f)
    }

    private inner class WaveSettings : ToggleableValueGroup(this@HatsOrbs, "Wave", true) {
        val waveHeight by float("WaveHeight", 0.1f, 0.01f..1f)
        val waveSpeed by float("WaveSpeed", 2.0f, 0.1f..10f)
    }

    init {
        tree(settings)
        tree(waveSettings)
    }

    override fun WorldRenderEnvironment.drawHat(isHurt: Boolean) {
        drawCustomMesh(ClientRenderPipelines.triangles(noDepthTest = true)) { matrix ->
            val time = ((System.currentTimeMillis() % 1000000L).toFloat() / 1000f) * settings.speed
            for (i in 0 until settings.count) {
                drawOrb(matrix, i, time, isHurt)
            }
        }
    }

    private fun VertexConsumer.drawOrb(matrix: PoseStack.Pose, index: Int, time: Float, isHurt: Boolean) {
        val angle = segmentAngle(index, settings.count) + time
        val x = getPointX(angle, settings.radius)
        val z = getPointZ(angle, settings.radius)
        val y = if (waveSettings.enabled) {
            sin(time * waveSettings.waveSpeed + index) * waveSettings.waveHeight
        } else {
            0f
        }
        val rotation = getRotationAngle(settings.spinSpeed)
        val orbColor = if (isHurt) Color4b(255, 0, 0, color.a) else color
        drawOrbRhombus(matrix, x, y, z, rotation, settings.size, orbColor)
    }

    private fun getPointX(angle: Float, radius: Float) = sin(angle) * radius
    private fun getPointZ(angle: Float, radius: Float) = cos(angle) * radius

}
