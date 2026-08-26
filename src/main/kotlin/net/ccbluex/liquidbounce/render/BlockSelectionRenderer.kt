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
package net.ccbluex.liquidbounce.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3

fun WorldRenderEnvironment.drawBlockSelection(position: Vec3, color: Color4b) {
    val baseColor = color.with(a = 50)
    val transparentColor = baseColor.with(a = 0)
    val outlineColor = color.with(a = 200)

    withPositionRelativeToCamera(position) {
        drawBoxSide(
            FULL_BOX,
            Direction.DOWN,
            baseColor,
            outlineColor,
        )
        drawGradientSides(0.7, baseColor, transparentColor, FULL_BOX)
    }
}

fun WorldRenderEnvironment.drawBlockSelection(position: Vec3i, color: Color4b) {
    drawBlockSelection(Vec3.atLowerCornerOf(position), color)
}
