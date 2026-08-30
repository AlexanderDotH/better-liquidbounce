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



@file:JvmName("CommandBaseFinderKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.command

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinderExportFormat
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinding
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ModuleBaseFinder
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.clickablePath
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable

/**
 * Manages findings stored by [ModuleBaseFinder] for the active server and dimension.
 */
object CommandBaseFinder : Command.Factory {

    override fun createCommand(): Command = CommandBuilder
        .begin("basefinder")
        .hub()
        .subcommand(listSubcommand())
        .subcommand(reportSubcommand())
        .subcommand(exportSubcommand())
        .subcommand(clearSubcommand())
        .build()

    private fun listSubcommand() = CommandBuilder
        .begin("list")
        .parameter(
            ParameterBuilder.begin<Int>("page")
                .verifiedBy(ParameterBuilder.POSITIVE_INTEGER_VALIDATOR)
                .optional()
                .build()
        )
        .requiresIngame()
        .handler {
            val findings = sortedFindings(ModuleBaseFinder.findingsForCurrentScope())
            val requestedPage = args.getOrNull(0) as Int? ?: 1
            command.sendPage(findings, requestedPage)
        }
        .build()

    private fun reportSubcommand() = CommandBuilder
        .begin("report")
        .parameter(
            ParameterBuilder.begin<String>("id")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedFrom { currentFindingSuggestions() }
                .required()
                .build()
        )
        .requiresIngame()
        .handler {
            val identifier = args.first() as String
            when (val lookup = resolveBaseFinderFinding(ModuleBaseFinder.findingsForCurrentScope(), identifier)) {
                is BaseFinderLookupResult.Found -> command.sendReport(lookup.finding)
                is BaseFinderLookupResult.Ambiguous -> throw CommandException(
                    command.result(
                        "ambiguous",
                        variable(identifier),
                        variable(lookup.matches.joinToString(", ", transform = BaseFinding::id)),
                    )
                )
                BaseFinderLookupResult.NotFound -> throw CommandException(
                    command.result("notFound", variable(identifier))
                )
            }
        }
        .build()

    private fun exportSubcommand() = CommandBuilder
        .begin("export")
        .hub()
        .subcommand(exportFormatSubcommand("json", BaseFinderExportFormat.JSON))
        .subcommand(exportFormatSubcommand("csv", BaseFinderExportFormat.CSV))
        .build()

    private fun exportFormatSubcommand(name: String, format: BaseFinderExportFormat) = CommandBuilder
        .begin(name)
        .requiresIngame()
        .handler {
            val path = try {
                ModuleBaseFinder.exportCurrentFindings(format)
            } catch (exception: Exception) {
                throw CommandException(
                    command.result("failed", variable(exception.localizedMessage ?: exception.javaClass.simpleName)),
                    exception
                )
            }

            chat(
                regular(command.result("success", clickablePath(path.toFile()))),
                metadata = MessageMetadata(id = BASE_FINDER_MESSAGE_ID)
            )
        }
        .build()

    private fun clearSubcommand() = CommandBuilder
        .begin("clear")
        .hub()
        .subcommand(clearCurrentSubcommand())
        .subcommand(
            CommandBuilder.begin("cache")
                .requiresIngame()
                .handler {
                    ModuleBaseFinder.clearSeedComparisonCache()
                    chat(
                        regular(command.result("success")),
                        metadata = MessageMetadata(id = BASE_FINDER_MESSAGE_ID)
                    )
                }
                .build()
        )
        .build()

    private fun clearCurrentSubcommand() = CommandBuilder
        .begin("current")
        .parameter(
            ParameterBuilder.begin<String>("confirm")
                .verifiedBy { token ->
                    if (token == "confirm") {
                        Result.Ok(token)
                    } else {
                        Result.Error("Type 'confirm' exactly to clear findings")
                    }
                }
                .autocompletedFrom { listOf("confirm") }
                .required()
                .build()
        )
        .requiresIngame()
        .handler {
            val removed = ModuleBaseFinder.clearCurrentFindings()
            val message = if (removed == 0) {
                command.result("empty")
            } else {
                command.result("success", variable(removed.toString()))
            }
            chat(
                regular(message),
                metadata = MessageMetadata(id = BASE_FINDER_MESSAGE_ID)
            )
        }
        .build()

}
