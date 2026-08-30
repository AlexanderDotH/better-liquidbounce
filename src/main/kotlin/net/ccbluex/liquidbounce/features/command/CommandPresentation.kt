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

import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.onClick
import net.ccbluex.liquidbounce.utils.text.onHover
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.ccbluex.liquidbounce.utils.text.joinToText
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent

internal fun printCommandStyledText(
    command: Command,
    key: String,
    data: String?,
    formatting: (MutableComponent) -> MutableComponent,
    hover: HoverEvent?,
    click: ClickEvent?,
) {
    val content = data?.let(::variable) ?: markAsError("N/A")
    val result = formatting(command.result(key, content))
    chat(result.onHover(hover).onClick(click))
}

internal fun printCommandStyledComponent(
    command: Command,
    key: String,
    textComponent: Component?,
    copyContent: String?,
    formatting: (MutableComponent) -> MutableComponent,
    hover: HoverEvent?,
) {
    val display = textComponent ?: markAsError("N/A")
    val content = copyContent ?: display.string
    chat(formatting(command.result(key, display)).copyable(copyContent = content, hover = hover))
}

internal fun commandResultWithTree(command: Command, key: String, vararg args: Any): MutableComponent {
    var root = command.parentCommand
    while (root?.parentCommand != null) {
        root = root.parentCommand
    }
    return root?.result(key, args = args) ?: command.result(key, args = args)
}

internal fun commandUsage(command: Command): List<Component> = buildList {
    if (command.executable) {
        add(command.ownUsage())
    }
    command.subcommands.forEach { subcommand -> addAll(commandUsage(subcommand)) }
}

private fun Command.ownUsage(): Component {
    val parts = generateSequence(this) { command -> command.parentCommand }
        .map(Command::nameAsText)
        .toMutableList()
    parts.reverse()
    parameters.mapTo(parts, Parameter<*>::nameAsText)
    return parts.joinToText(PlainText.SPACE)
}
