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
package net.ccbluex.liquidbounce.integration.theme

import net.ccbluex.liquidbounce.api.models.marketplace.MarketplaceItemType
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceManager
import org.apache.logging.log4j.Logger
import java.io.File

internal class ThemeCatalogLoader(
    private val themesFolder: File,
    private val logger: Logger,
) {

    suspend fun load(includedTheme: Theme?): List<Theme> {
        val themes = mutableListOf<Theme>()
        loadLocalThemes(themes)
        loadMarketplaceThemes(themes)
        includedTheme?.let(themes::add)
        return themes
    }

    private suspend fun loadLocalThemes(themes: MutableList<Theme>) {
        themesFolder.listFiles { it.isDirectory }
            ?.forEach { file ->
                if (file.name.equals("default", true)) return@forEach
                loadTheme(themes, file.name) {
                    Theme.load(Theme.Origin.LOCAL, file.relativeTo(themesFolder))
                }
            }
    }

    private suspend fun loadMarketplaceThemes(themes: MutableList<Theme>) {
        MarketplaceManager.getSubscribedItemsOfType(MarketplaceItemType.THEME).forEach { item ->
            loadTheme(themes, item.name) loader@{
                val installationFolder = item.getInstallationFolder() ?: return@loader null
                val relativeFile = installationFolder.relativeTo(MarketplaceManager.marketplaceRoot)
                Theme.load(Theme.Origin.MARKETPLACE, relativeFile)
            }
        }
    }

    private suspend inline fun loadTheme(
        themes: MutableList<Theme>,
        name: String,
        crossinline loader: suspend () -> Theme?,
    ) {
        runCatching { loader() }
            .onSuccess { theme -> theme?.let { addIfUnloaded(themes, it) } }
            .onFailure { error -> logger.error("Failed to load theme '$name'.", error) }
    }

    private fun addIfUnloaded(themes: MutableList<Theme>, theme: Theme) {
        if (themes.none { it.metadata.id.equals(theme.metadata.id, true) }) {
            themes.add(theme)
        } else {
            logger.warn("Theme with ID '${theme.metadata.id}' is already loaded, skipping duplicate.")
        }
    }
}
