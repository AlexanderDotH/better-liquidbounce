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
package net.ccbluex.liquidbounce.deeplearn.command

import kotlinx.coroutines.sync.Mutex
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder

object CommandModels : Command.Factory {
    private val mutationMutex = Mutex()

    override fun createCommand(): Command = CommandBuilder
        .begin("models")
        .hub()
        .subcommand(createModelCommand(mutationMutex))
        .subcommand(improveModelCommand(mutationMutex))
        .subcommand(deleteModelCommand(mutationMutex))
        .subcommand(reloadModelCommand(mutationMutex))
        .subcommand(browseModelCommand())
        .build()
}
