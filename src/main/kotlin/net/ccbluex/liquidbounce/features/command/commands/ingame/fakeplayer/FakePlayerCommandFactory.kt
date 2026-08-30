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
package net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder

internal fun FakePlayerSession.createCommand(): Command = CommandBuilder
    .begin("fakeplayer")
    .requiresIngame()
    .hub()
    .subcommand(spawnCommand())
    .subcommand(removeCommand())
    .subcommand(clearCommand())
    .subcommand(startRecordingCommand())
    .subcommand(endRecordingCommand())
    .build()

private fun FakePlayerSession.spawnCommand() = CommandBuilder
    .begin("spawn")
    .parameter(optionalNameParameter())
    .handler {
        checkInGame()
        spawn(args, moving = false)
    }
    .build()

private fun FakePlayerSession.removeCommand() = CommandBuilder
    .begin("remove")
    .parameter(
        ParameterBuilder.begin<String>("name")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .autocompletedFrom { fakePlayers.map { fakePlayer -> fakePlayer.name.string } }
            .optional()
            .build(),
    )
    .handler {
        checkInGame()
        remove(command, args.getOrNull(0)?.toString() ?: "FakePlayer")
    }
    .build()

private fun FakePlayerSession.clearCommand() = CommandBuilder
    .begin("clear")
    .handler {
        checkInGame()
        clear()
    }
    .build()

@Suppress("SpellCheckingInspection")
private fun FakePlayerSession.startRecordingCommand() = CommandBuilder
    .begin("startrecording")
    .handler {
        checkInGame()
        startRecording(command)
    }
    .build()

@Suppress("SpellCheckingInspection")
private fun FakePlayerSession.endRecordingCommand() = CommandBuilder
    .begin("endrecording")
    .parameter(optionalNameParameter())
    .handler {
        checkInGame()
        finishRecording(command, args)
    }
    .build()

private fun optionalNameParameter() = ParameterBuilder.begin<String>("name")
    .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
    .optional()
    .build()
