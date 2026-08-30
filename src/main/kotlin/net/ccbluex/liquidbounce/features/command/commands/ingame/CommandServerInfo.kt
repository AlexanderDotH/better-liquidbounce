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
package net.ccbluex.liquidbounce.features.command.commands.ingame

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.builder.enumChoices
import net.ccbluex.liquidbounce.features.server.ServerObserver
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.regular
import kotlin.time.Duration.Companion.seconds

/**
 * ServerInfo Command
 *
 * Displays the current server information, including:
 * - Server Address (Typed In)
 * - Resolved Server Address
 * - Server ID
 * - Server Type (Premium or Cracked)
 * - Server Brand (Brand that the server sent us, F3 menu)
 * - Advertised Version (Version that the server sent us)
 * - Detected Version (Gathers actual server version from known packs packet)
 * - TPS (Same as .tps)
 * - Ping (Same as .ping)
 * - Payload Channels
 * - Transactions (5x ping payloads)
 * - Transaction Differences
 * - Guessed Anti Cheat (Same as AntiCheatDetect)
 * - Hosting Information (Shown when command is being executed with hosting parameter)
 * - Plugins (Same as Plugins Module, requires plugins detect parameter)
 *
 * The command supports active detection modes for more thorough analysis.
 */
object CommandServerInfo : Command.Factory, EventListener {

    override fun createCommand(): Command {
        return CommandBuilder
            .begin("serverinfo")
            .requiresIngame()
            .parameter(
                ParameterBuilder.enumChoices<DetectionType>("detect")
                    .optional()
                    .build()
            )
            .suspendHandler {
                val detectionTypes = args.getOrNull(0) as? Set<DetectionType>

                if (!detectionTypes.isNullOrEmpty()) {
                    runActiveDetection(command, detectionTypes)
                } else {
                    printInformation(command)
                }
            }
            .build()
    }

    /**
     * Runs active detection for specified detection types
     *
     * @param command The command instance
     * @param detectionTypes Collection of detection types to run
     */
    private suspend fun runActiveDetection(command: Command, detectionTypes: Collection<DetectionType>) {
        chat(regular(command.result("detecting")))

        // Run plugin detection if requested
        if (DetectionType.PLUGINS in detectionTypes) {
            if (!ServerObserver.captureCommandSuggestions(10.seconds)) {
                chat(markAsError(command.result("pluginsDetectionTimeout")))
            }
        }

        // Request hosting information if requested
        if (DetectionType.HOSTING in detectionTypes) {
            ServerObserver.requestHostingInformation()
        }

        printInformation(command, detectionTypes)
    }

    /**
     * Print all server information to chat
     *
     * @param command The command instance
     * @param detections Optional list of active detections that were run
     */
    private fun printInformation(command: Command, detections: Collection<DetectionType> = emptyList()) =
        printServerInformation(
            command,
            detectionTags = if (detections.isEmpty()) DetectionType.entries.map(DetectionType::tag) else emptyList(),
        )

    /**
     * Detection for further server information
     */
    private enum class DetectionType(override val tag: String) : Tagged {
        PLUGINS("Plugins"),
        HOSTING("Hosting");
    }
}
