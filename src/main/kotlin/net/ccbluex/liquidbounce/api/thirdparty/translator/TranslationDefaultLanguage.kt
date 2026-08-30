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
package net.ccbluex.liquidbounce.api.thirdparty.translator

internal object TranslationDefaultLanguage {
    @Volatile
    private var provider: (() -> String)? = null

    @Synchronized
    fun install(provider: () -> String) {
        check(this.provider == null) { "Translation default language provider is already installed" }
        this.provider = provider
    }

    fun code(): String = provider?.invoke() ?: "en"

    @Synchronized
    internal fun <T> withProviderForTest(candidate: (() -> String)?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
