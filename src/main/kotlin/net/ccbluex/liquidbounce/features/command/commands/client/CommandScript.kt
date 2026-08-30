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

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.builder.enumChoice
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandBridge
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandDebugOptions
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandDebugProtocol
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.clickablePath
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.minecraft.util.Util
import java.io.File

private fun ParameterBuilder<*>.autocompletedFromScriptNames() =
    autocompletedFrom { ScriptCommandBridge.root().listFiles()?.map { it.name } }

object CommandScript : Command.Factory {

    override fun createCommand(): Command {
        return CommandBuilder.begin("script")
            .hub()
            .subcommand(reloadSubcommand())
            .subcommand(loadSubcommand())
            .subcommand(unloadSubcommand())
            .subcommand(debugSubcommand())
            .subcommand(listSubcommand())
            .subcommand(browseSubcommand())
            .subcommand(editSubcommand())
            .build()
    }

    private fun editSubcommand() = CommandBuilder.begin("edit").parameter(
        ParameterBuilder.begin<String>("name")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .required()
            .autocompletedFromScriptNames()
            .build()
    ).handler {
        val name = args[0] as String
        val scriptFile = ScriptCommandBridge.root().resolve(name)

        if (!scriptFile.exists()) {
            chat(regular(command.result("notFound", variable(name))))
            return@handler
        }

        Util.getPlatform().openFile(scriptFile)
        chat(regular(command.result("opened", variable(name))))
    }.build()

    private fun browseSubcommand() = CommandBuilder.begin("browse").handler {
        Util.getPlatform().openFile(ScriptCommandBridge.root())
        chat(regular(command.result("browse", clickablePath(ScriptCommandBridge.root()))))
    }.build()

    private fun listSubcommand() = CommandBuilder.begin("list").handler {
        val scripts = ScriptCommandBridge.scripts()
        val scriptNames = scripts.map { script -> "${script.name} (${script.language})" }

        if (scriptNames.isEmpty()) {
            chat(regular(command.result("noScripts")))
            return@handler
        }

        chat(regular(command.result("scripts", variable(scriptNames.joinToString(", ")))))
    }.build()

    private fun debugSubcommand() = CommandBuilder.begin("debug")
        .parameter(
            ParameterBuilder.begin<String>("name")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .required()
                .autocompletedFromScriptNames()
                .build()
        )
        .parameter(
            ParameterBuilder.enumChoice<ScriptCommandDebugProtocol>("protocol")
                .optional()
                .build()
        )
        .parameter(
            ParameterBuilder.begin<Boolean>("suspendOnStart")
                .verifiedBy(ParameterBuilder.BOOLEAN_VALIDATOR)
                .optional()
                .build()
        )
        .parameter(
            ParameterBuilder.begin<Boolean>("inspectInternals")
                .verifiedBy(ParameterBuilder.BOOLEAN_VALIDATOR)
                .optional()
                .build()
        )
        .parameter(
            ParameterBuilder.begin<Int>("port")
                .verifiedBy(ParameterBuilder.intRange(1, 65535))
                .optional()
                .build()
        )
        .handler {
            val name = args[0] as String
            val scriptFile = ScriptCommandBridge.root().resolve(name)

            if (!scriptFile.exists()) {
                chat(regular(command.result("notFound", variable(name))))
                return@handler
            }

            unloadIfLoaded(scriptFile, command, name)
            loadScriptWithDebug(args, scriptFile, command, name)
        }
        .build()

    private fun loadScriptWithDebug(
        args: Array<out Any>,
        scriptFile: File,
        command: Command,
        name: String
    ) {
        val protocol = args.getOrNull(1) as ScriptCommandDebugProtocol? ?: ScriptCommandDebugProtocol.INSPECT

        ScriptCommandBridge.load(
            scriptFile,
            ScriptCommandDebugOptions(
                protocol = protocol,
                suspendOnStart = args.getOrNull(2) as Boolean? == true,
                inspectInternals = args.getOrNull(3) as Boolean? == true,
                port = args.getOrNull(4) as Int?
                    ?: if (protocol == ScriptCommandDebugProtocol.INSPECT) 4242 else 4711,
            )
        ).onSuccess {
            chat(regular(command.result("loaded", variable(name))))
        }.onFailure {
            chat(regular(command.result("failedToLoad", variable(it.message ?: "unknown"))))
        }
    }

    private fun unloadIfLoaded(
        scriptFile: File,
        command: Command,
        name: String
    ) {
        ScriptCommandBridge.scripts().find { it.file == scriptFile }?.also {
            chat(regular(command.result("alreadyLoaded", variable(name))))

            ScriptCommandBridge.unload(scriptFile).onSuccess {
                chat(regular(command.result("unloaded", variable(name))))
            }.onFailure {
                chat(regular(command.result("failedToUnload", variable(it.message ?: "unknown"))))
            }
        }
    }

    private fun unloadSubcommand() = CommandBuilder.begin("unload").parameter(
        ParameterBuilder.begin<String>("name").verifiedBy(ParameterBuilder.STRING_VALIDATOR).required()
            .autocompletedFrom {
                ScriptCommandBridge.scripts().map { it.name }
            }
            .build()
    ).handler {
        val name = args[0] as String

        val script = ScriptCommandBridge.scripts().find { it.name.equals(name, true) }

        if (script == null) {
            chat(regular(command.result("notFound", variable(name))))
            return@handler
        }

        ScriptCommandBridge.unload(script.file).onSuccess {
            chat(regular(command.result("unloaded", variable(name))))
        }.onFailure {
            chat(regular(command.result("failedToUnload", variable(it.message ?: "unknown"))))
        }
    }.build()

    private fun loadSubcommand() = CommandBuilder.begin("load").parameter(
        ParameterBuilder.begin<String>("name")
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .required()
            .autocompletedFromScriptNames()
            .build()
    ).handler {
        val name = args[0] as String
        val scriptFile = ScriptCommandBridge.root().resolve(name)

        if (!scriptFile.exists()) {
            chat(regular(command.result("notFound", variable(name))))
            return@handler
        }

        // Check if script is already loaded
        if (ScriptCommandBridge.scripts().any { it.file == scriptFile }) {
            chat(regular(command.result("alreadyLoaded", variable(name))))
            return@handler
        }

        ScriptCommandBridge.load(scriptFile).onSuccess {
            chat(regular(command.result("loaded", variable(name))))
        }.onFailure {
            chat(regular(command.result("failedToLoad", variable(it.message ?: "unknown"))))
        }

    }.build()

    private fun reloadSubcommand() = CommandBuilder.begin("reload").handler {
        ScriptCommandBridge.reload().onSuccess {
            chat(regular(command.result("reloaded")))
        }.onFailure {
            chat(regular(command.result("reloadFailed", variable(it.message ?: "unknown"))))
        }
    }.build()

}
