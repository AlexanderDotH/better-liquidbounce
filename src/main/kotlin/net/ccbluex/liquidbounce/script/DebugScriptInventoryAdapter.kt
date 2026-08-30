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

import net.ccbluex.liquidbounce.features.command.commands.client.debug.DebugScriptDescriptor
import net.ccbluex.liquidbounce.features.command.commands.client.debug.DebugScriptInventoryBridge
import net.ccbluex.liquidbounce.features.command.commands.client.debug.DebugScriptInventoryProvider

object DebugScriptInventoryAdapter {

    @JvmStatic
    fun install() {
        DebugScriptInventoryBridge.install(
            DebugScriptInventoryProvider {
                ScriptManager.scripts.map(PolyglotScript::toDebugDescriptor)
            }
        )
    }
}

private fun PolyglotScript.toDebugDescriptor() = debugScriptDescriptor(
    name = scriptName,
    version = scriptVersion,
    authors = scriptAuthors,
    path = file.path,
)

internal fun debugScriptDescriptor(
    name: String,
    version: String,
    authors: Array<String>,
    path: String,
) = DebugScriptDescriptor(
    name = name,
    version = version,
    authors = authors.joinToString(", "),
    path = path,
)
