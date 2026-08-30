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
package net.ccbluex.liquidbounce.render.playermodel

internal fun interface PlayerModelNametagStateProvider {
    fun isRunning(): Boolean
}

internal object PlayerModelNametagStateBridge {

    private val DISABLED = PlayerModelNametagStateProvider { false }

    @Volatile
    private var provider = DISABLED

    @Synchronized
    fun install(provider: PlayerModelNametagStateProvider) {
        check(this.provider === DISABLED) { "Player model nametag state provider is already installed" }
        this.provider = provider
    }

    fun isRunning(): Boolean = provider.isRunning()

    @Synchronized
    internal fun <T> withProviderForTest(provider: PlayerModelNametagStateProvider?, block: () -> T): T {
        val previous = this.provider
        this.provider = provider ?: DISABLED
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
