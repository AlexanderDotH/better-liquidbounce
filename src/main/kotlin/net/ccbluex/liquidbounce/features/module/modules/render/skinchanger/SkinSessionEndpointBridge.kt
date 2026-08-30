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
package net.ccbluex.liquidbounce.features.module.modules.render.skinchanger

fun interface SkinSessionEndpointHook {
    fun baseUrl(sessionService: Any): String?
}

object SkinSessionEndpointBridge {

    @Volatile
    private var provider: SkinSessionEndpointHook? = null

    @JvmStatic
    @Synchronized
    fun install(provider: SkinSessionEndpointHook) {
        check(this.provider == null) { "Skin session endpoint provider is already installed" }
        this.provider = provider
    }

    fun baseUrl(sessionService: Any): String? = provider?.baseUrl(sessionService)

    @Synchronized
    internal fun <T> withProviderForTest(candidate: SkinSessionEndpointHook?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
