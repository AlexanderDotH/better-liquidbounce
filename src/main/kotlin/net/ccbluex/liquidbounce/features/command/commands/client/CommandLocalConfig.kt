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
package net.ccbluex.liquidbounce.features.command.commands.client

import net.ccbluex.liquidbounce.features.autoconfig.LocalConfigLoadSelection
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager

/** Local configuration load, list, browse, and save command facade. */
object CommandLocalConfig : Command.Factory {
    private const val RENDER_SELECTION = "render"

    internal sealed interface LoadSelectionToken {
        data object Render : LoadSelectionToken
        data class Modules(val modules: Set<ClientModule>) : LoadSelectionToken
    }

    internal class RenderOptInRequiredException(
        val modules: Set<ClientModule>,
    ) : IllegalArgumentException()

    override fun createCommand(): Command = CommandBuilder
        .begin("localconfig")
        .hub()
        .subcommand(localConfigLoadSubcommand())
        .subcommand(localConfigListSubcommand())
        .subcommand(localConfigBrowseSubcommand())
        .subcommand(localConfigSaveSubcommand())
        .build()

    internal fun parseLoadSelectionToken(
        sourceText: String,
        modules: Iterable<ClientModule> = ModuleManager,
    ): Result<out LoadSelectionToken> {
        if (sourceText.equals(RENDER_SELECTION, ignoreCase = true)) {
            return Result.Ok(LoadSelectionToken.Render)
        }
        val resolved = sourceText.split(',').mapNotNullTo(linkedSetOf()) { moduleName ->
            modules.find { module -> module.name.equals(moduleName, ignoreCase = true) }
        }
        return if (resolved.isEmpty()) {
            Result.Error("'$sourceText' contains no valid Module")
        } else {
            Result.Ok(LoadSelectionToken.Modules(resolved))
        }
    }

    internal fun combineLoadSelection(tokens: Iterable<LoadSelectionToken>): LocalConfigLoadSelection {
        val includeRender = tokens.any { token -> token === LoadSelectionToken.Render }
        val selectedModules = tokens.asSequence()
            .filterIsInstance<LoadSelectionToken.Modules>()
            .flatMap { token -> token.modules.asSequence() }
            .toCollection(linkedSetOf())
        val renderModules = selectedModules.filterTo(linkedSetOf()) { module ->
            module.category == ModuleCategories.RENDER
        }
        if (!includeRender && renderModules.isNotEmpty()) {
            throw RenderOptInRequiredException(renderModules)
        }
        selectedModules.removeAll(renderModules)
        return LocalConfigLoadSelection(selectedModules, includeRender)
    }

    internal fun autocompleteLoadSelection(
        begin: String,
        modules: Iterable<ClientModule> = ModuleManager,
    ): List<String> {
        val splitAt = begin.lastIndexOf(',') + 1
        val prefix = begin.substring(0, splitAt)
        val modulePrefix = begin.substring(splitAt)
        val suggestions = modules.asSequence()
            .filter { module -> module.name.startsWith(modulePrefix, ignoreCase = true) }
            .mapTo(mutableListOf()) { module -> prefix + module.name }
        if (splitAt == 0 && RENDER_SELECTION.startsWith(begin, ignoreCase = true)) {
            suggestions.add(0, RENDER_SELECTION)
        }
        return suggestions.distinct()
    }
}
