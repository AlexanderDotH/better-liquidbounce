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
package net.ccbluex.liquidbounce.features.module.modules.misc.easypearl

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.FULL_BOX
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawBoxSide
import net.ccbluex.liquidbounce.render.drawGradientSides
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.font.VerticalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.math.toFixed
import net.ccbluex.liquidbounce.render.WorldToScreen
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

internal fun renderEasyPearlTarget(
    event: WorldRenderEvent,
    blockPosition: BlockPos,
    state: BlockState,
    color: Color4b,
) = event.renderEnvironment {
    val baseColor = color.with(a = 50)
    val outlineColor = color.with(a = 200)

    withPositionRelativeToCamera(blockPosition) {
        if (state.renderShape != RenderShape.MODEL && state.isAir) {
            drawBoxSide(FULL_BOX, Direction.DOWN, baseColor, outlineColor)
            drawGradientSides(0.7, baseColor, baseColor.with(a = 0), FULL_BOX)
        } else {
            drawBox(FULL_BOX, baseColor, outlineColor)
        }
    }
}

internal fun renderEasyPearlDistance(
    event: OverlayRenderEvent,
    target: Vec3,
    playerPosition: Vec3,
    eyeHeight: Float,
    color: Color4b,
) {
    val screenPosition = WorldToScreen.calculateScreenPos(target.add(0.0, eyeHeight.toDouble(), 0.0)) ?: return
    val distanceText = "${playerPosition.distanceTo(target).toFixed(1)}m".asPlainText()
    val fontRenderer = FontManager.FONT_RENDERER

    with(event.context) {
        pose().pushMatrix()
        pose().translate(screenPosition.x, screenPosition.y)
        pose().scale(fontRenderer.scaleToVanillaFont)
        fontRenderer.draw(fontRenderer.process(distanceText, color)) {
            horizontalAnchor = HorizontalAnchor.CENTER
            verticalAnchor = VerticalAnchor.MIDDLE
            shadow = true
        }
        pose().popMatrix()
    }
}
