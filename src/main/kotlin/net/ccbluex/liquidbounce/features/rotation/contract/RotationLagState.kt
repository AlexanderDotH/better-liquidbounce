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
package net.ccbluex.liquidbounce.features.rotation.contract

internal object RotationLagState {

    @Volatile
    private var blinkLagProvider: () -> Boolean = { false }

    @Volatile
    private var backtrackLagProvider: () -> Boolean = { false }

    fun bindBlinkLag(provider: () -> Boolean) {
        blinkLagProvider = provider
    }

    fun bindBacktrackLag(provider: () -> Boolean) {
        backtrackLagProvider = provider
    }

    fun isFakeLagging(): Boolean = blinkLagProvider() || backtrackLagProvider()

    @Synchronized
    internal fun <T> withProvidersForTest(
        blinkLag: () -> Boolean = { false },
        backtrackLag: () -> Boolean = { false },
        block: () -> T,
    ): T {
        val previousBlinkLag = blinkLagProvider
        val previousBacktrackLag = backtrackLagProvider
        blinkLagProvider = blinkLag
        backtrackLagProvider = backtrackLag
        return try {
            block()
        } finally {
            blinkLagProvider = previousBlinkLag
            backtrackLagProvider = previousBacktrackLag
        }
    }
}
