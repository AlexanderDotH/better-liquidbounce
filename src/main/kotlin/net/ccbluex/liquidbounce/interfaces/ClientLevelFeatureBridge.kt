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

interface ClientLevelFeatureProvider {
    fun canRenderExplosionParticles(): Boolean
    fun canRenderBlockBreakParticles(): Boolean
    fun canPushEntities(): Boolean
    fun canPushFishingRod(): Boolean
}

object ClientLevelFeatureBridge {
    @Volatile
    private var provider: ClientLevelFeatureProvider? = null

    @JvmStatic
    @Synchronized
    fun install(provider: ClientLevelFeatureProvider) {
        check(this.provider == null) { "Client level feature provider is already installed" }
        this.provider = provider
    }

    @JvmStatic
    fun canRenderExplosionParticles(): Boolean = provider?.canRenderExplosionParticles() ?: true

    @JvmStatic
    fun canRenderBlockBreakParticles(): Boolean = provider?.canRenderBlockBreakParticles() ?: true

    @JvmStatic
    fun canPushEntities(): Boolean = provider?.canPushEntities() ?: true

    @JvmStatic
    fun canPushFishingRod(): Boolean = provider?.canPushFishingRod() ?: true

    @Synchronized
    internal fun <T> withProviderForTest(candidate: ClientLevelFeatureProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
