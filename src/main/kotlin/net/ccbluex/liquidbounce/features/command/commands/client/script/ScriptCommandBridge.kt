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
package net.ccbluex.liquidbounce.features.command.commands.client.script

import net.ccbluex.liquidbounce.common.Tagged
import java.io.File

data class ScriptCommandEntry(
    val name: String,
    val language: String,
    val file: File,
)

data class ScriptCommandDebugOptions(
    val protocol: ScriptCommandDebugProtocol,
    val suspendOnStart: Boolean,
    val inspectInternals: Boolean,
    val port: Int,
)

enum class ScriptCommandDebugProtocol(override val tag: String) : Tagged {
    DAP("DAP"),
    INSPECT("INSPECT"),
}

interface ScriptCommandProvider {
    fun root(): File
    fun scripts(): List<ScriptCommandEntry>
    fun load(file: File, debugOptions: ScriptCommandDebugOptions? = null): Result<Unit>
    fun unload(file: File): Result<Unit>
    fun reload(): Result<Unit>
}

object ScriptCommandBridge {
    @Volatile
    private var provider: ScriptCommandProvider? = null

    @Synchronized
    fun install(provider: ScriptCommandProvider) {
        check(this.provider == null) { "Script command provider is already installed" }
        this.provider = provider
    }

    fun root(): File = requireProvider().root()
    fun scripts(): List<ScriptCommandEntry> = requireProvider().scripts()
    fun load(file: File, options: ScriptCommandDebugOptions? = null): Result<Unit> =
        requireProvider().load(file, options)

    fun unload(file: File): Result<Unit> = requireProvider().unload(file)
    fun reload(): Result<Unit> = requireProvider().reload()

    private fun requireProvider(): ScriptCommandProvider =
        checkNotNull(provider) { "Script command provider is not installed" }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: ScriptCommandProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
