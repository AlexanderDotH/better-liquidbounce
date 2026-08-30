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
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.module.modules.misc.autoaccount.contract.AutoAccountCommandBridge

/**
 * Allows the AutoAccount register and login actions to be triggered manually.
 */
object CommandAutoAccount : Command.Factory {

    @Suppress("SpellCheckingInspection")
    override fun createCommand(): Command = CommandBuilder
        .begin("autoaccount")
        .requiresIngame()
        .hub()
        .subcommand(
            CommandBuilder
                .begin("register")
                .handler {
                    AutoAccountCommandBridge.register()
                }
                .build()
        )
        .subcommand(
            CommandBuilder
                .begin("login")
                .handler {
                    AutoAccountCommandBridge.login()
                }
                .build()
        )
        .build()
}
