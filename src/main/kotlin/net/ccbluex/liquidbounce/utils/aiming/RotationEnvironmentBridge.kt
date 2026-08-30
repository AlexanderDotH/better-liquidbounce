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

package net.ccbluex.liquidbounce.utils.aiming

interface RotationEnvironmentProvider {
    fun isFakeLagging(): Boolean
    fun isFreezing(): Boolean
    fun shouldPauseRotation(): Boolean
}

object RotationEnvironmentBridge {
    @Volatile
    private var provider: RotationEnvironmentProvider? = null

    @Synchronized
    fun install(provider: RotationEnvironmentProvider) {
        check(this.provider == null) { "Rotation environment provider is already installed" }
        this.provider = provider
    }

    fun isFakeLagging(): Boolean = provider?.isFakeLagging() ?: false

    fun isFreezing(): Boolean = provider?.isFreezing() ?: false

    fun shouldPauseRotation(): Boolean = provider?.shouldPauseRotation() ?: false

    @Synchronized
    internal fun <T> withProviderForTest(candidate: RotationEnvironmentProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
