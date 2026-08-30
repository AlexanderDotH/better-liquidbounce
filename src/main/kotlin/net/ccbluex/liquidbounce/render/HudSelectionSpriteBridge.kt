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
package net.ccbluex.liquidbounce.render

import net.minecraft.resources.Identifier

fun interface HudSelectionSpriteProvider {
    fun texture(): Identifier
}

object HudSelectionSpriteBridge {

    @Volatile
    private var provider: HudSelectionSpriteProvider? = null

    @JvmStatic
    @Synchronized
    fun install(provider: HudSelectionSpriteProvider) {
        check(this.provider == null) { "HUD selection sprite provider is already installed" }
        this.provider = provider
    }

    fun texture(): Identifier =
        provider?.texture() ?: error("HUD selection sprite injection adapter is not installed")

    @Synchronized
    internal fun <T> withProviderForTest(candidate: HudSelectionSpriteProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
