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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.render

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeRenderState
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeRuntime
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawCircleOutline
import net.ccbluex.liquidbounce.render.drawGradientCircle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.world.entity.LivingEntity

internal object TargetStrafeVisuals : ToggleableValueGroup(name = "Visuals", enabled = true) {
    private val width by float("Width", 0.12f, 0.01f..1.0f)
    private val heightOffset by float("HeightOffset", 0.05f, -1.0f..1.0f)
    private val outerColor by color("OuterColor", Color4b.LIQUID_BOUNCE.alpha(100))
    private val innerColor by color("InnerColor", Color4b.LIQUID_BOUNCE.alpha(20))
    private val outlineColor by color("OutlineColor", Color4b.LIQUID_BOUNCE.alpha(180))
    private val showNextPoint by boolean("ShowNextPoint", true)
    private val pointRadius by float("PointRadius", 0.18f, 0.05f..1.0f)
    private val pointColor by color("PointColor", Color4b.LIQUID_BOUNCE.alpha(90))
    private val pointOutlineColor by color("PointOutlineColor", Color4b.LIQUID_BOUNCE.alpha(180))
    private val invalidPointColor by color("InvalidPointColor", Color4b(255, 90, 90, 90))
    private val invalidPointOutlineColor by color("InvalidPointOutlineColor", Color4b(255, 90, 90, 180))

    init {
        doNotIncludeAlways()
    }

    override fun onDisabled() = TargetStrafeRuntime.renderState.reset()

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event -> render(event) }

    private fun render(event: WorldRenderEvent) {
        val state = TargetStrafeRuntime.renderState
        val target = state.target ?: return
        if (target.isRemoved) {
            state.reset()
            return
        }
        event.renderEnvironment {
            drawOrbit(event, target, state)
            if (showNextPoint) drawPoint(state)
        }
    }

    private fun WorldRenderEnvironment.drawOrbit(
        event: WorldRenderEvent,
        target: LivingEntity,
        state: TargetStrafeRenderState,
    ) {
        val outerRadius = state.orbitRadius + width / 2f
        val innerRadius = (state.orbitRadius - width / 2f).coerceAtLeast(0f)
        val position = target.interpolateCurrentPosition(event.partialTicks).add(0.0, heightOffset.toDouble(), 0.0)
        withPositionRelativeToCamera(position) {
            drawGradientCircle(outerRadius, innerRadius, outerColor, innerColor)
            drawCircleOutline(outerRadius, outlineColor)
        }
    }

    private fun WorldRenderEnvironment.drawPoint(state: TargetStrafeRenderState) {
        val color = if (state.nextPointValid) pointColor else invalidPointColor
        val outline = if (state.nextPointValid) pointOutlineColor else invalidPointOutlineColor
        withPositionRelativeToCamera(state.nextPoint.add(0.0, heightOffset.toDouble(), 0.0)) {
            drawGradientCircle(pointRadius, 0f, color, Color4b.TRANSPARENT)
            drawCircleOutline(pointRadius, outline)
        }
    }
}
