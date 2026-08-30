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

import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.builder.block
import net.ccbluex.liquidbounce.features.command.preset.pagedQuery
import net.ccbluex.liquidbounce.features.module.modules.render.xray.contract.XRayCommandBridge
import net.ccbluex.liquidbounce.utils.text.bold
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.ccbluex.liquidbounce.utils.text.withColor
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block

object CommandXRay : Command.Factory {

    override fun createCommand(): Command = CommandBuilder
        .begin("xray")
        .hub()
        .subcommand(addSubcommand())
        .subcommand(removeSubcommand())
        .subcommand(listSubcommand())
        .subcommand(clearSubcommand())
        .subcommand(resetSubcommand())
        .build()

    private fun resetSubcommand() = CommandBuilder
        .begin("reset")
        .handler {
            XRayCommandBridge.reset()
            chat(
                regular(command.result("Reset the blocks to the default values")),
                metadata = MessageMetadata(id = "CXRay#global")
            )
        }
        .build()

    private fun clearSubcommand() = CommandBuilder
        .begin("clear")
        .handler {
            XRayCommandBridge.clear()
            chat(
                regular(command.result("blocksCleared")),
                metadata = MessageMetadata(id = "CXRay#global")
            )
        }
        .build()

    private fun listSubcommand() = CommandBuilder
        .begin("list")
        .pagedQuery(
            pageSize = 8,
            header = { result("list").withColor(ChatFormatting.RED).bold(true) },
            items = { XRayCommandBridge.blocks().sortedBy { it.descriptionId } },
            eachRow = { _, block ->
                regular("\u2B25 ")
                    .append(variable(block.name).copyable())
                    .append(regular(" ("))
                    .append(variable(BuiltInRegistries.BLOCK.getKey(block).toString()).copyable())
                    .append(regular(")"))
            }
        )

    private fun removeSubcommand() = CommandBuilder
        .begin("remove")
        .parameter(
            ParameterBuilder.block()
                .required()
                .build()
        )
        .handler {
            val block = args[0] as Block
            if (!XRayCommandBridge.remove(block)) {
                throw CommandException(command.result("blockNotFound", block.name))
            }

            chat(
                regular(command.result("blockRemoved", block.name)),
                metadata = MessageMetadata(id = "CXRay#info")
            )
        }
        .build()

    private fun addSubcommand() = CommandBuilder
        .begin("add")
        .parameter(
            ParameterBuilder.block()
                .required()
                .build()
        )
        .handler {
            val block = args[0] as Block
            if (!XRayCommandBridge.add(block)) {
                throw CommandException(command.result("blockIsPresent", block.name))
            }

            chat(
                regular(command.result("blockAdded", block.name)),
                metadata = MessageMetadata(id = "CXRay#info")
            )
        }
        .build()
}
