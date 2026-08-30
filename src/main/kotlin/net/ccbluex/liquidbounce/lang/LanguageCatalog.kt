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

package net.ccbluex.liquidbounce.lang

interface LanguageCatalogProvider {
    fun translate(key: String, fallback: String): String
    fun hasFallbackTranslation(key: String): Boolean
}

object LanguageCatalog {
    private val FALLBACK = object : LanguageCatalogProvider {
        override fun translate(key: String, fallback: String) = fallback
        override fun hasFallbackTranslation(key: String) = false
    }

    @Volatile
    private var provider: LanguageCatalogProvider = FALLBACK

    @Synchronized
    fun install(provider: LanguageCatalogProvider) {
        check(this.provider === FALLBACK) { "Language catalog provider is already installed" }
        this.provider = provider
    }

    fun translate(key: String, fallback: String): String = provider.translate(key, fallback)

    fun hasFallbackTranslation(key: String): Boolean = provider.hasFallbackTranslation(key)
}
