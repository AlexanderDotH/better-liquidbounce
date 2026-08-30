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

import net.ccbluex.liquidbounce.api.models.marketplace.MarketplaceItemType
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceContentReloadBridge
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceContentReloadProvider
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.script.ScriptManager

internal object MarketplaceContentReloadAdapter : MarketplaceContentReloadProvider {
    override suspend fun reload(type: MarketplaceItemType) {
        when (type) {
            MarketplaceItemType.THEME -> ThemeManager.load()
            MarketplaceItemType.SCRIPT -> ScriptManager.reload()
            else -> Unit
        }
    }

    fun install() = MarketplaceContentReloadBridge.install(this)
}
