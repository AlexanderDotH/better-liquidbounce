/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.render.target

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.mapReadOnly
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawTriangle
import net.ccbluex.liquidbounce.render.engine.font.HorizontalAnchor
import net.ccbluex.liquidbounce.render.engine.font.VerticalAnchor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.render.WorldToScreen.calculateScreenPos
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.Entity

internal class TextTargetAppearance(
    owner: ToggleableValueGroup,
    override val parent: ModeValueGroup<*>,
) : GuiTargetAppearance("Text2D") {
    private val textScale by float("Scale", 1f, 0.01f..10f)
    private val textShadow by boolean("Shadow", true)
    private val style by color("Color", Color4b.RED).mapReadOnly { Style.EMPTY + it }
    private val texts by textList("Text", mutableListOf("TARGET"))
    private val heightMode = modes(owner, "HeightMode") {
        arrayOf(
            TargetHeightMode.Feet(it),
            TargetHeightMode.Top(it),
            TargetHeightMode.Relative(it),
            TargetHeightMode.Health(it),
            TargetHeightMode.Animated(it),
        )
    }

    override fun GuiGraphicsExtractor.render(entity: Entity, partialTicks: Float) {
        val position = entity.targetPosition(heightMode.activeMode.getHeight(entity, partialTicks), partialTicks)
        val screenPosition = calculateScreenPos(position) ?: return
        texts.forEachIndexed { index, text ->
            FontManager.FONT_RENDERER.draw(text.asPlainText(style)) {
                horizontalAnchor = HorizontalAnchor.CENTER
                verticalAnchor = VerticalAnchor.MIDDLE
                x = screenPosition.x
                y = screenPosition.y + index * FontManager.FONT_RENDERER.height
                shadow = textShadow
                scale = textScale
            }
        }
    }
}

internal class ArrowTargetAppearance(override val parent: ModeValueGroup<*>) : GuiTargetAppearance("Arrow") {
    private val color by color("Color", Color4b.RED)
    private val outlineColor by color("OutlineColor", Color4b.TRANSPARENT)
    private val size by float("Size", 1.5f, 0.5f..20f)

    override fun GuiGraphicsExtractor.render(entity: Entity, partialTicks: Float) {
        val position = entity.interpolateCurrentPosition(partialTicks).add(0.0, entity.bbHeight.toDouble(), 0.0)
        val screenPosition = calculateScreenPos(position) ?: return
        drawTriangle(
            x0 = screenPosition.x - 5 * size,
            y0 = screenPosition.y - 10 * size,
            x1 = screenPosition.x,
            y1 = screenPosition.y,
            x2 = screenPosition.x + 5 * size,
            y2 = screenPosition.y - 10 * size,
            color,
            outlineColor,
        )
    }
}
