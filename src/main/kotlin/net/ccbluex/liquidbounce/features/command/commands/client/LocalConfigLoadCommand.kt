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

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.autoconfig.LocalConfigCodec
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.ccbluex.liquidbounce.utils.text.warning

internal fun localConfigLoadSubcommand() = CommandBuilder
    .begin("load")
    .parameter(
        ParameterBuilder.begin<String>("name")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .autocompletedFrom {
                ConfigSystem.userConfigsFolder.listFiles()?.map { it.nameWithoutExtension }
            }
            .required()
            .build()
    )
    .parameter(
        ParameterBuilder.begin<CommandLocalConfig.LoadSelectionToken>("selection")
            .verifiedBy { sourceText -> CommandLocalConfig.parseLoadSelectionToken(sourceText) }
            .autocompletedWith { begin, _ -> CommandLocalConfig.autocompleteLoadSelection(begin) }
            .vararg()
            .optional()
            .build()
    )
    .handler {
        val name = args[0] as String
        val tokens = (args.getOrNull(1) as? Array<*>)
            ?.filterIsInstance<CommandLocalConfig.LoadSelectionToken>()
            .orEmpty()
        val selection = try {
            CommandLocalConfig.combineLoadSelection(tokens)
        } catch (error: CommandLocalConfig.RenderOptInRequiredException) {
            val modules = error.modules.joinToString(", ") { module -> module.name }
            throw CommandException(command.result("renderRequiresOptIn", variable(modules)))
        }
        ConfigSystem.userConfigsFolder.resolve("$name.json").runCatching {
            if (!exists()) {
                chat(regular(command.result("notFound", variable(name))))
                return@handler
            }
            bufferedReader().use { reader -> LocalConfigCodec.load(reader, selection) }
        }.onFailure { error ->
            logger.error("Failed to load config $name", error)
            chat(markAsError(command.result("failedToLoad", variable(name))))
        }.onSuccess { result ->
            val resultKey = if (selection.includeRender) "loadedWithRender" else "loaded"
            chat(regular(command.result(resultKey, variable(name))))
            if (selection.includeRender && !result.hasRenderSnapshot) {
                chat(warning(command.result("renderMissing", variable(name))))
            }
        }
    }
    .build()
