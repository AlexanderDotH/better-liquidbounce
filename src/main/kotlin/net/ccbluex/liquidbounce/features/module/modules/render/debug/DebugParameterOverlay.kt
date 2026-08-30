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
package net.ccbluex.liquidbounce.features.module.modules.render.debug

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.features.misc.DebuggedOwner
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.textOf
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

@JvmRecord
internal data class DebugParameterKey(val owner: DebuggedOwner, val name: String)

@JvmRecord
internal data class DebugParameterCapture(val time: Long = System.currentTimeMillis(), val value: Any?)

internal fun buildDebugParameterLines(
    parameters: Map<DebugParameterKey, DebugParameterCapture>,
    currentTime: Long = System.currentTimeMillis(),
): List<Component> = buildList {
    parameters.keys.groupBy(DebugParameterKey::owner).forEach { (owner, keys) ->
        add(owner.debugDisplayName)
        keys.forEach { key ->
            val capture = parameters[key] ?: return@forEach
            val duration = (currentTime - capture.time) / 1000
            add(textOf(
                "${key.name}: ".asPlainText(ChatFormatting.WHITE),
                capture.value.toString().asPlainText(ChatFormatting.GREEN),
                " [${duration}s ago]".asPlainText(ChatFormatting.GRAY),
            ))
        }
    }
}

internal fun renderDebugParameterOverlay(event: OverlayRenderEvent, lines: List<Component>) {
    val fontRenderer = FontManager.FONT_RENDERER
    val vanillaScale = fontRenderer.scaleToVanillaFont
    with(event.context) {
        fontRenderer.draw("Debugging".asPlainText()) {
            x = 120f
            y = 22f
            shadow = true
            scale = vanillaScale * 2
        }
        lines.forEachIndexed { index, text ->
            fontRenderer.draw(text) {
                x = 120f
                y = 40 + ((fontRenderer.height * vanillaScale) * index)
                shadow = true
                scale = vanillaScale
            }
        }
    }
}
