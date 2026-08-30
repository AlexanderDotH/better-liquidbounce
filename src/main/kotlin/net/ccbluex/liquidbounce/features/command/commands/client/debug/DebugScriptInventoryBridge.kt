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
package net.ccbluex.liquidbounce.features.command.commands.client.debug

data class DebugScriptDescriptor(
    val name: String,
    val version: String,
    val authors: String,
    val path: String,
)

fun interface DebugScriptInventoryProvider {
    fun scripts(): List<DebugScriptDescriptor>
}

object DebugScriptInventoryBridge {
    @Volatile
    private var provider: DebugScriptInventoryProvider? = null

    @Synchronized
    fun install(provider: DebugScriptInventoryProvider) {
        check(this.provider == null) { "Debug script inventory provider is already installed" }
        this.provider = provider
    }

    fun scripts(): List<DebugScriptDescriptor> = provider?.scripts().orEmpty()

    @Synchronized
    internal fun <T> withProviderForTest(provider: DebugScriptInventoryProvider?, block: () -> T): T {
        val previous = this.provider
        this.provider = provider
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
