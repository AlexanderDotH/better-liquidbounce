/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.render.text

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.asText
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

fun gradientText(text: String, startColor: Color4b, endColor: Color4b): MutableComponent =
    text.foldIndexed("".asText()) { index, result, character ->
        val factor = if (text.length > 1) index / (text.length - 1.0) else 0.0
        result.append(character.toString().asPlainText(Style.EMPTY + startColor.interpolateTo(endColor, factor)))
    }
