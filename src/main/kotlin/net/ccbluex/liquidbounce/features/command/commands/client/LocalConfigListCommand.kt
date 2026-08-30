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
package net.ccbluex.liquidbounce.features.command.commands.client

import kotlinx.coroutines.async
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.api.models.client.AutoSettings
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.features.autoconfig.AutoConfigMetadata
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.preset.pagedQuery
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.clickablePath
import net.ccbluex.liquidbounce.utils.text.highlight
import net.ccbluex.liquidbounce.utils.text.onClick
import net.ccbluex.liquidbounce.utils.text.onHover
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.ccbluex.liquidbounce.utils.kotlin.unmodifiable
import net.ccbluex.liquidbounce.utils.text.AsyncLoadingText
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus
import net.ccbluex.liquidbounce.utils.text.textOf
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.util.Util
import java.io.File
import java.time.Instant
import java.time.ZoneId

internal fun localConfigBrowseSubcommand() = CommandBuilder.begin("browse").handler {
    Util.getPlatform().openFile(ConfigSystem.userConfigsFolder)
    chat(
        regular(command.result("browse", clickablePath(ConfigSystem.userConfigsFolder)))
    )
}.build()

internal fun localConfigListSubcommand() = CommandBuilder
    .begin("list")
    .pagedQuery(
        pageSize = 8,
        header = { highlight("Local Configs:") },
        items = {
            ConfigSystem.userConfigsFolder.listFiles { _, name ->
                name.endsWith(".json", ignoreCase = true)
            }.unmodifiable()
        },
        eachRow = { _, file -> localConfigRow(file) },
    )

private fun localConfigRow(file: File) = file.name.removeSuffix(".json").let { settingName ->
    val modified = Instant.ofEpochMilli(file.lastModified())
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(AutoSettings.FORMATTER)
    textOf(
        "\u2B25 ".asPlainText(ChatFormatting.BLUE),
        variable(file.name)
            .onClick(
                ClickEvent.SuggestCommand(
                    CommandManager.GlobalSettings.prefix + "localconfig load $settingName"
                )
            )
            .onHover(HoverEvent.ShowText(localConfigHoverText(file, settingName))),
        regular(" ($modified)"),
    )
}

private fun localConfigHoverText(file: File, settingName: String) = textOf(
    "Click to load ".asPlainText(ChatFormatting.GRAY),
    settingName.asPlainText(Style.EMPTY + ChatFormatting.AQUA + ChatFormatting.BOLD),
    PlainText.NEW_LINE,
    AsyncLoadingText(
        ioScope.async {
            file.bufferedReader().use { reader ->
                publicGson.fromJson(reader, AutoConfigMetadata::class.java)
            }.asText()
        }
    ),
)
