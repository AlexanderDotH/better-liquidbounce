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
package net.ccbluex.liquidbounce.features.module.modules.misc.betterchat

import net.ccbluex.liquidbounce.interfaces.GuiMessageLineAddition
import net.minecraft.client.multiplayer.chat.GuiMessage

@Suppress("CAST_NEVER_SUCCEEDS")
internal fun resolveChatMessageBounds(visibleMessages: List<GuiMessage.Line>, index: Int): IntRange {
    val id = (visibleMessages[index] as GuiMessageLineAddition).`liquid_bounce$getId`()
    if (id != null) return resolveMessageIdBounds(visibleMessages, index, id)

    var start = index
    while (start > 0 && !visibleMessages[start].endOfEntry()) start--
    var end = index
    while (end < visibleMessages.lastIndex && !visibleMessages[end + 1].endOfEntry()) end++
    return start..end
}

@Suppress("CAST_NEVER_SUCCEEDS")
private fun resolveMessageIdBounds(visibleMessages: List<GuiMessage.Line>, index: Int, id: String): IntRange {
    var start = index
    while (start > 0) {
        val previousId = (visibleMessages[start - 1] as GuiMessageLineAddition).`liquid_bounce$getId`()
        if (id != previousId) break
        start--
    }

    var end = index
    while (end < visibleMessages.lastIndex) {
        val nextId = (visibleMessages[end + 1] as GuiMessageLineAddition).`liquid_bounce$getId`()
        if (id != nextId) break
        end++
    }
    return start..end
}
