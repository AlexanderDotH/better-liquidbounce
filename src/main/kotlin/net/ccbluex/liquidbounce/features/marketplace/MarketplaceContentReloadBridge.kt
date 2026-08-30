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

import net.ccbluex.liquidbounce.api.models.marketplace.MarketplaceItemType

internal fun interface MarketplaceContentReloadProvider {
    suspend fun reload(type: MarketplaceItemType)
}

internal object MarketplaceContentReloadBridge {
    @Volatile
    private var provider: MarketplaceContentReloadProvider? = null

    @Synchronized
    fun install(provider: MarketplaceContentReloadProvider) {
        check(this.provider == null) { "Marketplace content reload provider is already installed" }
        this.provider = provider
    }

    suspend fun reload(type: MarketplaceItemType) = requireProvider().reload(type)

    private fun requireProvider() = checkNotNull(provider) {
        "Marketplace content reload provider is not installed"
    }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: MarketplaceContentReloadProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
