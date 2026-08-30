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

import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.OptionalInclusion
import net.ccbluex.liquidbounce.config.autoconfig.IncludeConfiguration
import net.ccbluex.liquidbounce.features.autoconfig.LocalConfigCodec
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.builder.boolean
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.minecraft.SharedConstants

internal fun localConfigSaveSubcommand() = CommandBuilder
    .begin("save")
    .alias("create")
    .parameter(
        ParameterBuilder.begin<String>("name")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .autocompletedFrom {
                ConfigSystem.userConfigsFolder.listFiles()?.map { it.nameWithoutExtension }
            }
            .required()
            .build()
    )
    .parameter(ParameterBuilder.boolean("overwrite").optional().build())
    .parameter(
        ParameterBuilder.begin<String>("include")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .autocompletedFrom { listOf("binds", "hidden", "render", "fun") }
            .vararg()
            .optional()
            .build()
    )
    .handler {
        val name = args[0] as String
        if (name.isBlank() || name.indexOfAny(SharedConstants.ILLEGAL_FILE_CHARACTERS) != -1) {
            throw CommandException(command.result("invalidFileName", variable(name)))
        }
        val overwrite = args.getOrNull(1) as Boolean? ?: false
        val include = args.getOrNull(2) as Array<*>? ?: emptyArray<String>()
        val inclusions = enumSetOf<OptionalInclusion>()
        if (include.contains("render")) inclusions.add(OptionalInclusion.RENDER)
        if (include.contains("fun")) inclusions.add(OptionalInclusion.FUN)
        val configuration = IncludeConfiguration(
            includeBinds = include.contains("binds"),
            includeHidden = include.contains("hidden"),
            optionalInclusions = inclusions,
        )
        val file = ConfigSystem.userConfigsFolder.resolve("$name.json")
        try {
            if (file.exists()) {
                if (overwrite) file.delete() else {
                    chat(markAsError(command.result("alreadyExists", variable(name))))
                    return@handler
                }
            }
            file.createNewFile()
            LocalConfigCodec.serialize(file.bufferedWriter(), configuration)
            chat(regular(command.result("created", variable(name))))
        } catch (error: Exception) {
            chat(regular(command.result("failedToCreate", variable(name))))
            logger.error("Failed to create local config '$name'", error)
        }
    }
    .build()
