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
package net.ccbluex.liquidbounce.features.command.commands.ingame

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.builder.playerName
import net.ccbluex.liquidbounce.features.module.modules.misc.inventorytracker.contract.InventoryTrackerCommandBridge

object CommandInvsee : Command.Factory {

    override fun createCommand(): Command = CommandBuilder
        .begin("invsee")
        .requiresIngame()
        .parameter(
            ParameterBuilder.playerName()
                .required()
                .build()
        )
        .handler {
            val inputName = args[0] as String
            if (!InventoryTrackerCommandBridge.open(inputName)) {
                throw CommandException(command.result("playerNotFound", inputName))
            }
        }
        .build()
}
