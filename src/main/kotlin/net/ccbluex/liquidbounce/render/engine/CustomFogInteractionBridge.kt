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
package net.ccbluex.liquidbounce.render.engine

fun interface CustomFogInteractionProvider {
    fun active(): Boolean
}

object CustomFogInteractionBridge {

    private val DISABLED = CustomFogInteractionProvider { false }

    @Volatile
    private var provider: CustomFogInteractionProvider = DISABLED

    @JvmStatic
    @Synchronized
    fun install(provider: CustomFogInteractionProvider) {
        check(this.provider === DISABLED) { "Custom fog interaction provider is already installed" }
        this.provider = provider
    }

    fun active(): Boolean = provider.active()

    @Synchronized
    internal fun <T> withProviderForTest(candidate: CustomFogInteractionProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate ?: DISABLED
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
