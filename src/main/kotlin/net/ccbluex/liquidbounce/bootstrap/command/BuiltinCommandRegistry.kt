/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.bootstrap.command

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandAutoAccount
import net.ccbluex.liquidbounce.features.command.commands.client.CommandAutoDisable
import net.ccbluex.liquidbounce.features.command.commands.client.CommandBaritone
import net.ccbluex.liquidbounce.features.command.commands.client.CommandBind
import net.ccbluex.liquidbounce.features.command.commands.client.CommandBinds
import net.ccbluex.liquidbounce.features.command.commands.client.CommandClear
import net.ccbluex.liquidbounce.features.command.commands.client.CommandConfig
import net.ccbluex.liquidbounce.features.command.commands.client.CommandDebug
import net.ccbluex.liquidbounce.features.command.commands.client.CommandFriend
import net.ccbluex.liquidbounce.features.command.commands.client.CommandHelp
import net.ccbluex.liquidbounce.features.command.commands.client.CommandHide
import net.ccbluex.liquidbounce.features.command.commands.client.CommandLocalConfig
import net.ccbluex.liquidbounce.features.command.commands.client.CommandPanic
import net.ccbluex.liquidbounce.features.command.commands.client.CommandScript
import net.ccbluex.liquidbounce.features.command.commands.client.CommandTargets
import net.ccbluex.liquidbounce.features.command.commands.client.CommandToggle
import net.ccbluex.liquidbounce.features.command.commands.client.CommandValue
import net.ccbluex.liquidbounce.features.command.commands.client.CommandXRay
import net.ccbluex.liquidbounce.features.command.commands.client.client.CommandClient
import net.ccbluex.liquidbounce.features.command.commands.client.marketplace.CommandMarketplace
import net.ccbluex.liquidbounce.deeplearn.command.CommandModels
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandCenter
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandCoordinates
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandInvsee
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandPing
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandRemoteView
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandResync
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandSay
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandServerInfo
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandTps
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandUsername
import net.ccbluex.liquidbounce.features.command.commands.ingame.creative.CommandItemEnchant
import net.ccbluex.liquidbounce.features.command.commands.ingame.creative.CommandItemGive
import net.ccbluex.liquidbounce.features.command.commands.ingame.creative.CommandItemRename
import net.ccbluex.liquidbounce.features.command.commands.ingame.creative.CommandItemSkull
import net.ccbluex.liquidbounce.features.command.commands.ingame.creative.CommandItemStack
import net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer.CommandFakePlayer
import net.ccbluex.liquidbounce.features.command.commands.translate.CommandAutoTranslate
import net.ccbluex.liquidbounce.features.command.commands.translate.CommandTranslate
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.command.CommandMaceClip
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.command.CommandSpearHighSpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.teleport.command.CommandPlayerTeleport
import net.ccbluex.liquidbounce.features.module.modules.movement.teleport.command.CommandTeleport
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.command.CommandBaseFinder
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.command.CommandSeedCracker

internal val builtinCommands: Array<Command.Factory> = arrayOf(
    CommandClient,
    CommandFriend,
    CommandBaritone,
    CommandToggle,
    CommandBind,
    CommandCenter,
    CommandHelp,
    CommandBinds,
    CommandClear,
    CommandHide,
    CommandInvsee,
    CommandPanic,
    CommandValue,
    CommandPing,
    CommandRemoteView,
    CommandResync,
    CommandMaceClip,
    CommandSpearHighSpeed,
    CommandXRay,
    CommandBaseFinder,
    CommandSeedCracker,
    CommandTargets,
    CommandConfig,
    CommandLocalConfig,
    CommandAutoDisable,
    CommandScript,
    CommandSay,
    CommandFakePlayer,
    CommandAutoAccount,
    CommandDebug,
    CommandItemRename,
    CommandItemGive,
    CommandItemSkull,
    CommandItemStack,
    CommandItemEnchant,
    CommandUsername,
    CommandCoordinates,
    CommandTeleport,
    CommandPlayerTeleport,
    CommandTps,
    CommandServerInfo,
    CommandModels,
    CommandTranslate,
    CommandAutoTranslate,
    CommandMarketplace,
)
