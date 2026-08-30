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

import net.ccbluex.liquidbounce.features.autoconfig.AutoConfig
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.regular
import net.minecraft.network.chat.MutableComponent

/**
 * Panic Command
 *
 * Allows you to disable all modules or modules in a specific category.
 */
object CommandPanic : Command.Factory {

    override fun createCommand(): Command {
        return CommandBuilder
            .begin("panic")
            .parameter(
                ParameterBuilder
                    .begin<String>("category")
                    .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                    .optional()
                    .build()
            )
            .handler {
                executePanic(command, args)
            }
            .build()
    }

    private fun executePanic(command: Command, arguments: Array<out Any>) {
        val type = arguments.getOrNull(0) as String? ?: "nonrender"
        val (modules, message) = panicSelection(command, type)
        runCatching {
            AutoConfig.withLoading {
                modules.forEach { module -> module.enabled = false }
            }
        }.onSuccess {
            chat(regular(message), command)
        }.onFailure {
            throw CommandException(command.result("panicFailed"))
        }
    }

    private fun panicSelection(command: Command, type: String): Pair<List<ClientModule>, MutableComponent> {
        val running = ModuleManager.filter { module -> module.running }
        if (type == "all") {
            return running to command.result("disabledAllModules")
        }
        if (type == "nonrender") {
            val modules = running.filter { module -> module.category != ModuleCategories.RENDER }
            return modules to command.result("disabledAllCategoryModules", command.result("nonRender"))
        }
        val category = ModuleCategories.byName(type)
            ?: throw CommandException(command.result("categoryNotFound", type))
        return running.filter { module -> module.category == category } to
            command.result("disabledAllCategoryModules", category.tag)
    }

}
