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
package net.ccbluex.liquidbounce.deeplearn.command

import java.io.File

internal interface ModelCommandIntegrationProvider {
    val combatRecorderFolder: File
    val trainerRecorderFolder: File
    fun syncClickGui()
}

internal object ModelCommandIntegrationBridge {
    @Volatile
    private var provider: ModelCommandIntegrationProvider? = null

    @Synchronized
    fun install(provider: ModelCommandIntegrationProvider) {
        check(this.provider == null) { "Model command integration provider is already installed" }
        this.provider = provider
    }

    fun combatRecorderFolder(): File = requireProvider().combatRecorderFolder
    fun trainerRecorderFolder(): File = requireProvider().trainerRecorderFolder
    fun syncClickGui() = requireProvider().syncClickGui()

    private fun requireProvider() = checkNotNull(provider) {
        "Model command integration provider is not installed"
    }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: ModelCommandIntegrationProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
