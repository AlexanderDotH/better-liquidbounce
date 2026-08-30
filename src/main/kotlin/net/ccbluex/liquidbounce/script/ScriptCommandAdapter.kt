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
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandBridge
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandDebugOptions
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandDebugProtocol
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandEntry
import net.ccbluex.liquidbounce.features.command.commands.client.script.ScriptCommandProvider
import java.io.File

object ScriptCommandAdapter {

    fun install() {
        ScriptCommandBridge.install(
            RuntimeScriptCommandProvider(
                rootFile = ScriptManager.root,
                entrySupplier = {
                    ScriptManager.scripts.map { script ->
                        ScriptCommandEntry(script.scriptName, script.language, script.file)
                    }
                },
                normalLoader = { file -> ScriptManager.loadScript(file).enable() },
                debugLoader = { file, options ->
                    ScriptManager.loadScript(file, debugOptions = options.toScriptDebugOptions()).enable()
                },
                unloader = { file ->
                    val script = ScriptManager.scripts.find { it.file == file }
                        ?: error("No loaded script for file '${file.path}'")
                    ScriptManager.unloadScript(script)
                },
                reloader = ScriptManager::reload,
            )
        )
    }
}

internal class RuntimeScriptCommandProvider(
    private val rootFile: File,
    private val entrySupplier: () -> List<ScriptCommandEntry>,
    private val normalLoader: (File) -> Unit,
    private val debugLoader: (File, ScriptCommandDebugOptions) -> Unit,
    private val unloader: (File) -> Unit,
    private val reloader: () -> Unit,
) : ScriptCommandProvider {

    override fun root(): File = rootFile

    override fun scripts(): List<ScriptCommandEntry> = entrySupplier()

    override fun load(file: File, debugOptions: ScriptCommandDebugOptions?): Result<Unit> = runCatching {
        if (debugOptions == null) normalLoader(file) else debugLoader(file, debugOptions)
    }

    override fun unload(file: File): Result<Unit> = runCatching { unloader(file) }

    override fun reload(): Result<Unit> = runCatching(reloader)
}

internal fun ScriptCommandDebugOptions.toScriptDebugOptions() = ScriptDebugOptions(
    enabled = true,
    protocol = when (protocol) {
        ScriptCommandDebugProtocol.DAP -> DebugProtocol.DAP
        ScriptCommandDebugProtocol.INSPECT -> DebugProtocol.INSPECT
    },
    suspendOnStart = suspendOnStart,
    inspectInternals = inspectInternals,
    port = port,
)
