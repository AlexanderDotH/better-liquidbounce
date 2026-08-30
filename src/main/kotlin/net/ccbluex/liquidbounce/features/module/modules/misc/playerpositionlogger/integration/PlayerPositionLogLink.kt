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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.integration

import net.ccbluex.liquidbounce.utils.text.onClick
import net.ccbluex.liquidbounce.utils.text.onHover
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.underline
import net.ccbluex.liquidbounce.utils.text.asText
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import java.io.File

internal fun playerPositionLogFileLink(file: File) = file.absolutePath.asText()
    .underline(true)
    .onHover(HoverEvent.ShowText(regular("Browse...")))
    .onClick(ClickEvent.OpenFile(file.absolutePath))
