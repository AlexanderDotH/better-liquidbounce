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
package net.ccbluex.liquidbounce.integration

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientCommandRuntimeBridge
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientCommandRuntimeProvider
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientIntegrationLink
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientThemeDescription
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientThemeHandle
import net.ccbluex.liquidbounce.features.command.commands.client.client.runtime.ClientThemeReloadResult
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.screen.impl.InternetExplorerScreen
import net.ccbluex.liquidbounce.integration.theme.Theme
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.integration.theme.ThemeMetadata
import net.ccbluex.liquidbounce.utils.client.mc
import java.io.File

object ClientCommandRuntimeAdapter {

    fun install() {
        ClientCommandRuntimeBridge.install(
            RuntimeClientCommandProvider(
                browserOpener = { name ->
                    mc.schedule { mc.gui.setScreen(InternetExplorerScreen(name)) }
                },
                integrationResetter = ScreenManager::update,
                baseUrlSupplier = { ThemeManager.getScreenLocation().url },
                linkSupplier = {
                    mapAvailableIntegrationLinks(
                        CustomScreenType.entries,
                        CustomScreenType::routeName,
                    ) { type -> ThemeManager.getScreenLocation(type, true).url }
                },
                themesFolderSupplier = { ThemeManager.themesFolder },
                themeSupplier = { ThemeManager.themes.map(::IntegrationThemeHandle) },
                remoteThemeLoader = { url -> IntegrationThemeHandle(Theme.load(url)) },
                themeActivator = { handle -> activateTheme(handle) },
                themeReloader = {
                    reloadThemeCatalog({ ThemeManager.themes.size }, ThemeManager::load)
                },
            )
        )
    }

    private fun activateTheme(handle: ClientThemeHandle) {
        val theme = (handle as? IntegrationThemeHandle)?.theme
            ?: error("Theme handle was not created by the integration adapter")
        ThemeManager.theme = theme
        ConfigSystem.store(ThemeManager)
    }
}

internal class RuntimeClientCommandProvider(
    private val browserOpener: (String) -> Unit,
    private val integrationResetter: () -> Unit,
    private val baseUrlSupplier: () -> String,
    private val linkSupplier: () -> List<ClientIntegrationLink>,
    private val themesFolderSupplier: () -> File,
    private val themeSupplier: () -> List<ClientThemeHandle>,
    private val remoteThemeLoader: suspend (String) -> ClientThemeHandle,
    private val themeActivator: (ClientThemeHandle) -> Unit,
    private val themeReloader: suspend () -> ClientThemeReloadResult,
) : ClientCommandRuntimeProvider {

    override fun openBrowser(name: String) = browserOpener(name)
    override fun resetIntegration() = integrationResetter()
    override fun integrationBaseUrl(): String = baseUrlSupplier()
    override fun integrationLinks(): List<ClientIntegrationLink> = linkSupplier()
    override fun themesFolder(): File = themesFolderSupplier()
    override fun themeIds(): List<String> = themes().map { it.description.id }
    override fun themes(): List<ClientThemeHandle> = themeSupplier()
    override suspend fun loadRemoteTheme(url: String): ClientThemeHandle = remoteThemeLoader(url)
    override fun findTheme(id: String): ClientThemeHandle? = findThemeHandle(themes(), id)
    override fun activateTheme(theme: ClientThemeHandle): Result<Unit> = runCatching { themeActivator(theme) }
    override suspend fun reloadThemes(): ClientThemeReloadResult = themeReloader()
}

internal data class IntegrationThemeHandle(val theme: Theme) : ClientThemeHandle {
    override val description: ClientThemeDescription = describeTheme(theme.metadata, theme.origin.tag)
}

internal fun describeTheme(metadata: ThemeMetadata, origin: String) = ClientThemeDescription(
    id = metadata.id,
    name = metadata.name,
    version = metadata.version,
    authors = metadata.authors,
    origin = origin,
)

internal fun findThemeHandle(themes: List<ClientThemeHandle>, id: String): ClientThemeHandle? =
    themes.find { it.description.id.equals(id, true) }

internal inline fun <T> mapAvailableIntegrationLinks(
    entries: Iterable<T>,
    routeName: (T) -> String,
    url: (T) -> String,
): List<ClientIntegrationLink> = entries.mapNotNull { entry ->
    runCatching { ClientIntegrationLink(routeName(entry), url(entry)) }.getOrNull()
}

internal suspend fun reloadThemeCatalog(
    count: () -> Int,
    reload: suspend () -> Unit,
): ClientThemeReloadResult {
    val previousCount = count()
    reload()
    return ClientThemeReloadResult(previousCount, count())
}
