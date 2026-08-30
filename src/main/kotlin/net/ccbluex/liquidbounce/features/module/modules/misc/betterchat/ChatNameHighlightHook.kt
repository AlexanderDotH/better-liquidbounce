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
 */

package net.ccbluex.liquidbounce.features.module.modules.misc.betterchat

import net.minecraft.client.multiplayer.chat.GuiMessage

object ChatNameHighlightHook {

    @JvmStatic
    fun namesEnabled(): Boolean = ModuleBetterChat.running && ModuleBetterChat.NameHighlight.running

    @JvmStatic
    fun copyHighlightEnabled(): Boolean = ModuleBetterChat.running && ModuleBetterChat.Copy.running &&
        ModuleBetterChat.Copy.highlight

    @JvmStatic
    fun resolveMessageBounds(lines: List<GuiMessage.Line>, messageIndex: Int): IntRange =
        ModuleBetterChat.resolveMessageBounds(lines, messageIndex)

    @JvmStatic
    fun colorFor(message: String, playerName: String, visibility: Float): Int? =
        ChatNameHighlightPolicy.colorFor(
            message,
            playerName,
            ModuleBetterChat.NameHighlight.color,
            visibility,
        )
}
