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
package net.ccbluex.liquidbounce.features.command

import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.math.levenshtein
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.joinToText
import net.ccbluex.liquidbounce.utils.text.textOf
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import kotlin.math.min

internal fun unknownCommand(input: String): CommandException = CommandException(
    translation("liquidbounce.commandManager.unknownCommand", input),
    usageInfo = nearestCommandHints(input),
)

private fun nearestCommandHints(input: String): List<Component> {
    if (CommandRegistry.isEmpty() || CommandManager.GlobalSettings.hintCount == 0) {
        return emptyList()
    }
    return CommandRegistry.sortedBy { command -> command.distanceFrom(input) }
        .take(CommandManager.GlobalSettings.hintCount)
        .map(Command::hintText)
}

private fun Command.distanceFrom(input: String): Int {
    val nameDistance = levenshtein(input, name)
    val aliasDistance = aliases.minOfOrNull { alias -> levenshtein(input, alias) }
    return aliasDistance?.let { min(nameDistance, it) } ?: nameDistance
}

private fun Command.hintText(): Component {
    if (aliases.isEmpty()) {
        return nameAsText()
    }
    return textOf(
        nameAsText(),
        " (".asPlainText(ChatFormatting.DARK_GRAY),
        aliases.joinToText(", ".asPlainText(ChatFormatting.DARK_GRAY)),
        ")".asPlainText(ChatFormatting.DARK_GRAY),
    )
}
