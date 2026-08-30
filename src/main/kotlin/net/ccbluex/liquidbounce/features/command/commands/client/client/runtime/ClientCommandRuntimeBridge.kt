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
package net.ccbluex.liquidbounce.features.command.commands.client.client.runtime

import java.io.File

data class ClientIntegrationLink(val routeName: String, val url: String)

data class ClientThemeDescription(
    val id: String,
    val name: String,
    val version: String,
    val authors: List<String>,
    val origin: String,
)

interface ClientThemeHandle {
    val description: ClientThemeDescription
}

data class ClientThemeReloadResult(val previousCount: Int, val currentCount: Int)

interface ClientCommandRuntimeProvider {
    fun openBrowser(name: String)
    fun resetIntegration()
    fun integrationBaseUrl(): String
    fun integrationLinks(): List<ClientIntegrationLink>
    fun themesFolder(): File
    fun themeIds(): List<String>
    fun themes(): List<ClientThemeHandle>
    suspend fun loadRemoteTheme(url: String): ClientThemeHandle
    fun findTheme(id: String): ClientThemeHandle?
    fun activateTheme(theme: ClientThemeHandle): Result<Unit>
    suspend fun reloadThemes(): ClientThemeReloadResult
}

object ClientCommandRuntimeBridge {
    @Volatile
    private var provider: ClientCommandRuntimeProvider? = null

    @Synchronized
    fun install(provider: ClientCommandRuntimeProvider) {
        check(this.provider == null) { "Client command runtime provider is already installed" }
        this.provider = provider
    }

    fun openBrowser(name: String) = requireProvider().openBrowser(name)
    fun resetIntegration() = requireProvider().resetIntegration()
    fun integrationBaseUrl(): String = requireProvider().integrationBaseUrl()
    fun integrationLinks(): List<ClientIntegrationLink> = requireProvider().integrationLinks()
    fun themesFolder(): File = requireProvider().themesFolder()
    fun themeIds(): List<String> = requireProvider().themeIds()
    fun themes(): List<ClientThemeHandle> = requireProvider().themes()
    suspend fun loadRemoteTheme(url: String): ClientThemeHandle = requireProvider().loadRemoteTheme(url)
    fun findTheme(id: String): ClientThemeHandle? = requireProvider().findTheme(id)
    fun activateTheme(theme: ClientThemeHandle): Result<Unit> = requireProvider().activateTheme(theme)
    suspend fun reloadThemes(): ClientThemeReloadResult = requireProvider().reloadThemes()

    private fun requireProvider(): ClientCommandRuntimeProvider =
        checkNotNull(provider) { "Client command runtime provider is not installed" }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: ClientCommandRuntimeProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
