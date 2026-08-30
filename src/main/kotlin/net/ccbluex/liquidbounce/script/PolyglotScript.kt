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
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.RefreshArrayListEvent
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.script.bindings.features.ScriptCommandBuilder
import net.ccbluex.liquidbounce.script.bindings.features.ScriptMode
import net.ccbluex.liquidbounce.script.bindings.features.ScriptModule
import net.ccbluex.liquidbounce.utils.client.logger
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.io.File
import java.util.function.Consumer
import kotlin.time.measureTime

class PolyglotScript(
    val language: String, val file: File,
    val debugOptions: ScriptDebugOptions = ScriptDebugOptions()
) : AutoCloseable {

    private val context: Context = PolyglotContextFactory.create(
        language = language,
        file = file,
        debugOptions = debugOptions,
        scriptMetadataRegistrar = metadataRegistrar(),
    )

    // Script information
    lateinit var scriptName: String
    lateinit var scriptVersion: String
    lateinit var scriptAuthors: Array<String>

    /**
     * Whether the script is enabled
     */
    private var scriptEnabled = false

    private val globalEvents = hashMapOf<String, Runnable>()

    /**
     * Tracks client modifications made by the script
     */
    private val registeredModules = mutableListOf<ClientModule>()
    private val registeredCommands = mutableListOf<Command>()
    private val registeredModes = mutableListOf<Mode>()

    /**
     * Initialization of scripts
     */
    fun initScript() {
        try {
            // Evaluate script
            val duration = measureTime {
                context.eval(Source.newBuilder(language, file).build())

                // Call load event
                callGlobalEvent("load")

                if (!::scriptName.isInitialized || !::scriptVersion.isInitialized || !::scriptAuthors.isInitialized) {
                    logger.error("[ScriptAPI] Script '${file.name}' is missing required information!")
                    error("Script '${file.name}' is missing required information!")
                }
            }
            logger.info("[ScriptAPI] Successfully loaded script '${file.name}' in ${duration.inWholeMilliseconds}ms.")
        } catch (e: Exception) {
            logger.error("[ScriptAPI] Failed to load script '${file.name}'.", e)
            context.close()
            throw e
        }
    }

    /**
     * Registers a new script module
     *
     * @param moduleObject JavaScript object containing information about the module.
     * @param callback JavaScript function to which the corresponding instance of [ScriptModule] is passed.
     * @see ScriptModule
     */
    @Suppress("unused")
    fun registerModule(moduleObject: Map<String, Any>, callback: Consumer<ClientModule>) {
        val module = ScriptModule(this, moduleObject)
        registeredModules += module
        callback.accept(module)
    }

    /**
     * Registers a new script command
     *
     * @param commandObject From the command builder.
     */
    @Suppress("unused")
    fun registerCommand(commandObject: Value) {
        val commandBuilder = ScriptCommandBuilder(commandObject)
        registeredCommands += commandBuilder.build()
    }

    /**
     * Registers a new script mode to an existing mode value group which can be obtained
     * from existing modules.
     *
     * @param modeValueGroup The choice configurable to add the choice to.
     * @param modeObject JavaScript object containing information about the choice.
     * @param callback JavaScript function to which the corresponding instance of [ScriptMode] is passed.
     *
     * @see ScriptMode
     * @see ModeValueGroup
     */
    @Suppress("unused")
    fun registerMode(
        modeValueGroup: ModeValueGroup<Mode>,
        modeObject: Map<String, Any>,
        callback: Consumer<Mode>
    ) {
        ScriptMode(modeObject, modeValueGroup).apply {
            callback.accept(this)
            registeredModes += this
        }
    }

    /**
     * Registers a new script choice to an existing choice configurable which can be obtained
     * from existing modules.
     *
     * @param modeValueGroup The choice configurable to add the choice to.
     * @param modeObject JavaScript object containing information about the choice.
     * @param callback JavaScript function to which the corresponding instance of [ScriptMode] is passed.
     *
     * @see ScriptMode
     * @see ModeValueGroup
     */
    @Suppress("unused")
    @Deprecated(
        "Use registerMode instead",
        ReplaceWith("registerMode(modeValueGroup, modeObject, callback)")
    )
    fun registerChoice(
        modeValueGroup: ModeValueGroup<Mode>,
        modeObject: Map<String, Any>,
        callback: Consumer<Mode>
    ) = registerMode(modeValueGroup, modeObject, callback)

    /**
     * Called from inside the script to register a new event handler.
     * @param eventName Name of the event.
     * @param handler JavaScript function used to handle the event.
     */
    fun on(eventName: String, handler: Runnable) {
        globalEvents[eventName] = handler
    }

    /**
     * Called when the client enables the script.
     */
    fun enable() {
        if (scriptEnabled) {
            return
        }

        callGlobalEvent("enable")

        registeredModules.forEach(ModuleManager::addModule)
        registeredCommands.forEach(CommandManager::addCommand)

        registeredModes.forEach { choice ->
            @Suppress("UNCHECKED_CAST")
            (choice.parent.modes as MutableList<Any>).add(choice)
        }
        scriptEnabled = true
    }

    /**
     * Called when the client disables the script. Handles unregistering all modules and commands
     * created with this script.
     */
    fun disable() {
        if (!scriptEnabled) {
            return
        }

        callGlobalEvent("disable")

        registeredModules.forEach(ModuleManager::removeModule)
        registeredCommands.forEach(CommandManager::removeCommand)

        registeredModes.forEach { it.parent.modes.remove(it) }

        EventManager.callEvent(RefreshArrayListEvent)

        scriptEnabled = false
    }

    /**
     * Called when the client unloads the script.
     */
    override fun close() {
        context.close(true)
    }

    /**
     * Calls the handler of a registered event.
     * @param eventName Name of the event to be called.
     */
    private fun callGlobalEvent(eventName: String) {
        try {
            globalEvents[eventName]?.run()
        } catch (throwable: Throwable) {
            logger.error(
                "${file.name}::$scriptName -> Event Function $eventName threw an error",
                throwable
            )
        }
    }
}
