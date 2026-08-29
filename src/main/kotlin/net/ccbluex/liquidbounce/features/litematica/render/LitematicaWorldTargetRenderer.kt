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
package net.ccbluex.liquidbounce.features.litematica.render

import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.FULL_BOX
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera

internal object LitematicaWorldTargetRenderer {
    fun render(event: WorldRenderEvent, targets: List<LitematicaRenderTarget>) {
        if (targets.isEmpty()) return

        event.renderEnvironment {
            targets.forEach { target ->
                val colors = LitematicaTargetPalette.colorsFor(target.style)
                withPositionRelativeToCamera(target.position) {
                    drawBox(FULL_BOX, colors.fill, colors.outline)
                }
            }
        }
    }
}
