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

package net.ccbluex.liquidbounce.features.command.commands.module

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinderExportFormat
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinding
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ModuleBaseFinder
import net.ccbluex.liquidbounce.utils.client.MessageMetadata
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.clickablePath
import net.ccbluex.liquidbounce.utils.client.copyable
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.client.variable

private const val PAGE_SIZE = 8
private const val MESSAGE_ID = "CBaseFinder#management"

/**
 * Manages findings stored by [ModuleBaseFinder] for the active server and dimension.
 */
object CommandBaseFinder : Command.Factory {

    override fun createCommand(): Command = CommandBuilder
        .begin("basefinder")
        .hub()
        .subcommand(listSubcommand())
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
                metadata = MessageMetadata(id = MESSAGE_ID)
            )
        }
        .build()

    private fun clearSubcommand() = CommandBuilder
        .begin("clear")
        .hub()
        .subcommand(clearCurrentSubcommand())
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
                metadata = MessageMetadata(id = MESSAGE_ID)
            )
        }
        .build()

    private fun Command.sendPage(findings: List<BaseFinding>, requestedPage: Int) {
        if (findings.isEmpty()) {
            chat(regular(result("empty")), metadata = MessageMetadata(id = MESSAGE_ID))
            return
        }

        val maximumPage = (findings.size + PAGE_SIZE - 1) / PAGE_SIZE
        if (requestedPage > maximumPage) {
            throw CommandException(result("pageOutOfRange", variable(maximumPage.toString())))
        }

        mc.gui.hud.chat.removeMessage(MESSAGE_ID)
        val metadata = MessageMetadata(id = MESSAGE_ID, remove = false)
        chat(
            regular(
                result(
                    "header",
                    variable(requestedPage.toString()),
                    variable(maximumPage.toString()),
                    variable(findings.size.toString())
                )
            ),
            metadata = metadata
        )

        findings
            .subList((requestedPage - 1) * PAGE_SIZE, minOf(requestedPage * PAGE_SIZE, findings.size))
            .forEach { finding -> chat(finding.asRow(this), metadata = metadata) }
    }

    private fun BaseFinding.asRow(command: Command) = regular(
        command.result(
            "row",
            coordinateText().let { variable(it).copyable(copyContent = it) },
            variable("$confidence%"),
            variable(command.result("tier.${tier.name.lowercase()}")),
            topEvidenceText(command)
        )
    )

    private fun BaseFinding.coordinateText() = "${anchor.x} ${anchor.y} ${anchor.z}"

    private fun BaseFinding.topEvidenceText(command: Command) = evidence
        .sortedByDescending { it.score }
        .take(2)
        .map { variable(command.result("family.${it.family.name.lowercase()}")) }
        .reduceOrNull { text, family -> text.append(regular(" + ")).append(family) }
        ?: regular(command.result("family.unknown"))

    private fun sortedFindings(findings: List<BaseFinding>) = findings.sortedWith(
        compareByDescending<BaseFinding> { it.confidence }
            .thenByDescending { it.lastSeenAtMillis }
            .thenBy { it.id }
    )

}
