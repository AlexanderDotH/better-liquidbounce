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
package net.ccbluex.liquidbounce.features.module.modules.render.potionfx.contract

import net.minecraft.core.particles.ParticleOptions

fun interface ParticleColorHook {
    fun color(particle: ParticleOptions): Int?
}

object ParticleColorBridge {

    @Volatile
    private var provider: ParticleColorHook? = null

    @JvmStatic
    @Synchronized
    fun install(provider: ParticleColorHook) {
        check(this.provider == null) { "Particle color provider is already installed" }
        this.provider = provider
    }

    fun color(particle: ParticleOptions): Int? = provider?.color(particle)

    @Synchronized
    internal fun <T> withProviderForTest(candidate: ParticleColorHook?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
