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
package net.ccbluex.liquidbounce.features.marketplace

import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.api.models.marketplace.MarketplaceItemType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MarketplaceContentReloadBridgeTest {
    @Test
    fun `reload forwards the exact marketplace item type`() {
        val reloaded = mutableListOf<MarketplaceItemType>()
        val provider = MarketplaceContentReloadProvider { type ->
            reloaded += type
            Unit
        }

        MarketplaceContentReloadBridge.withProviderForTest(provider) {
            runBlocking {
                MarketplaceContentReloadBridge.reload(MarketplaceItemType.THEME)
                MarketplaceContentReloadBridge.reload(MarketplaceItemType.SCRIPT)
            }
        }

        assertEquals(listOf(MarketplaceItemType.THEME, MarketplaceItemType.SCRIPT), reloaded)
    }

    @Test
    fun `missing provider fails fast`() {
        MarketplaceContentReloadBridge.withProviderForTest(null) {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { MarketplaceContentReloadBridge.reload(MarketplaceItemType.THEME) }
            }
        }
    }
}
