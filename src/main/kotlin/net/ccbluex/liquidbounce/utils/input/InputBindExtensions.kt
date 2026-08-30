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
@file:JvmName("InputBindKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.input

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.fastutil.unmodifiable
import net.ccbluex.liquidbounce.common.Tagged.Companion.makeLookupTable
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.bold
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.onHover
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.ccbluex.liquidbounce.utils.text.buildText
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

fun InputBind.renderText(): Component = buildText {
    add(
        inputByName(keyName).let { key ->
            variable(key.displayName.copy()).bold(true)
                .copyable(copyContent = key.name)
        }
    )

    val divider = regular(" + ")
    if (modifiers.isNotEmpty()) {
        modifiers.forEach {
            add(divider)
            add(variable(it.platformRenderName).onHover(HoverEvent.ShowText(it.tag.asPlainText())))
        }
    }
    add(regular(" ("))
    add(variable(action.tag))
    add(regular(")"))
}
