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

internal interface AiAngleSmoothModel {
    val name: String

    fun predict(input: FloatArray): FloatArray
}

internal interface AiAngleSmoothRuntimeProvider {
    val isInitialized: Boolean
    val models: List<AiAngleSmoothModel>
    val activeModelName: String

    fun onModelsChanged(listener: () -> Unit)
}

internal object AiAngleSmoothRuntimeBridge {

    @Volatile
    private var provider: AiAngleSmoothRuntimeProvider? = null

    val isInitialized: Boolean
        get() = requireProvider().isInitialized

    val models: List<AiAngleSmoothModel>
        get() = requireProvider().models

    val activeModelName: String
        get() = requireProvider().activeModelName

    @Synchronized
    fun install(provider: AiAngleSmoothRuntimeProvider) {
        check(this.provider == null) { "AI angle smooth runtime provider is already installed" }
        this.provider = provider
    }

    fun onModelsChanged(listener: () -> Unit) = requireProvider().onModelsChanged(listener)

    private fun requireProvider(): AiAngleSmoothRuntimeProvider = checkNotNull(provider) {
        "AI angle smooth runtime provider is not installed"
    }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: AiAngleSmoothRuntimeProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
