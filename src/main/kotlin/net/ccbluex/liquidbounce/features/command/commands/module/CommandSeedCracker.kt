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
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleSeedCracker
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerPresentation
import net.ccbluex.liquidbounce.utils.client.chat

/** Local-only controls for the SeedCracker; none of these commands are sent to the server. */
object CommandSeedCracker : Command.Factory {

    override fun createCommand(): Command = CommandBuilder
        .begin("seedcracker")
        .hub()
        .subcommand(statusSubcommand())
        .subcommand(evidenceSubcommand(
            "confirm",
            ModuleSeedCracker::confirm,
            ModuleSeedCracker::confirmGuided,
            ModuleSeedCracker::pendingEvidenceIds,
        ))
        .subcommand(evidenceSubcommand(
            "reject",
            ModuleSeedCracker::reject,
            ModuleSeedCracker::rejectGuided,
            ModuleSeedCracker::pendingEvidenceIds,
        ))
        .subcommand(evidenceSubcommand(
            "undo",
            ModuleSeedCracker::undo,
            ModuleSeedCracker::status,
            ModuleSeedCracker::evidenceIds,
        ))
        .subcommand(controlSubcommand("pause", ModuleSeedCracker::pause))
        .subcommand(controlSubcommand("resume", ModuleSeedCracker::resume))
        .subcommand(resetSubcommand())
        .build()

    private fun statusSubcommand() = CommandBuilder
        .begin("status")
        .requiresIngame()
        .handler {
            send(ModuleSeedCracker.status())
        }
        .build()

    private fun evidenceSubcommand(
        name: String,
        action: (String) -> SeedCrackerPresentation,
        actionWithoutId: () -> SeedCrackerPresentation,
        autocomplete: () -> List<String>,
    ) = CommandBuilder
        .begin(name)
        .parameter(evidenceIdParameter(autocomplete))
        .requiresIngame()
        .handler {
            val evidenceId = args.getOrNull(0) as? String
            send(evidenceId?.let(action) ?: actionWithoutId())
        }
        .build()

    private fun controlSubcommand(name: String, action: () -> SeedCrackerPresentation) = CommandBuilder
        .begin(name)
        .requiresIngame()
        .handler {
            send(action())
        }
        .build()

    private fun resetSubcommand() = CommandBuilder
        .begin("reset")
        .hub()
        .subcommand(resetScopeSubcommand("current", ModuleSeedCracker::resetCurrent))
        .subcommand(resetScopeSubcommand("all", ModuleSeedCracker::resetAll))
        .build()

    private fun resetScopeSubcommand(name: String, action: () -> SeedCrackerPresentation) = CommandBuilder
        .begin(name)
        .parameter(confirmParameter())
        .requiresIngame()
        .handler {
            send(action())
        }
        .build()

    private fun evidenceIdParameter(autocomplete: () -> List<String>) = ParameterBuilder
        .begin<String>("id")
        .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
        .autocompletedFrom { autocomplete() }
        .optional()
        .build()

    private fun confirmParameter() = ParameterBuilder
        .begin<String>("confirm")
        .verifiedBy { token ->
            if (token == "--confirm") {
                Result.Ok(token)
            } else {
                Result.Error("Type '--confirm' exactly to reset SeedCracker data")
            }
        }
        .autocompletedFrom { listOf("--confirm") }
        .required()
        .build()

    private fun Command.Handler.Context.send(presentation: SeedCrackerPresentation) {
        chat(presentation.message, command)
    }
}
