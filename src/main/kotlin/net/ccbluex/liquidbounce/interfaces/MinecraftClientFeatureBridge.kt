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

package net.ccbluex.liquidbounce.interfaces

interface MinecraftClientFeatureProvider {
    fun isAppearanceHidden(): Boolean
    fun onGameTick()
    fun claimReachUse(): Boolean
    fun hasEnforcedBlockingHand(): Boolean
    fun shouldPauseCombat(): Boolean
    fun resetPlayerModelState()
}

object MinecraftClientFeatureBridge {
    @Volatile
    private var provider: MinecraftClientFeatureProvider? = null

    @JvmStatic
    @Synchronized
    fun install(provider: MinecraftClientFeatureProvider) {
        check(this.provider == null) { "Minecraft client feature provider is already installed" }
        this.provider = provider
    }

    @JvmStatic
    fun isAppearanceHidden(): Boolean = provider?.isAppearanceHidden() ?: false

    @JvmStatic
    fun onGameTick() = provider?.onGameTick() ?: Unit

    @JvmStatic
    fun claimReachUse(): Boolean = provider?.claimReachUse() ?: false

    @JvmStatic
    fun hasEnforcedBlockingHand(): Boolean = provider?.hasEnforcedBlockingHand() ?: false

    @JvmStatic
    fun shouldPauseCombat(): Boolean = provider?.shouldPauseCombat() ?: false

    @JvmStatic
    fun resetPlayerModelState() = provider?.resetPlayerModelState() ?: Unit

    @Synchronized
    internal fun <T> withProviderForTest(candidate: MinecraftClientFeatureProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
