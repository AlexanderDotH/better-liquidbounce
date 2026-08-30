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
package net.ccbluex.liquidbounce.common.runtime

fun interface BlinkDummyStateProvider {
    fun isDummyPlayer(entityId: Int): Boolean
}

object BlinkDummyState {

    private val NO_DUMMY = BlinkDummyStateProvider { false }

    @Volatile
    private var provider = NO_DUMMY

    @Synchronized
    fun install(provider: BlinkDummyStateProvider) {
        check(this.provider === NO_DUMMY) { "Blink dummy state provider is already installed" }
        this.provider = provider
    }

    fun isDummyPlayer(entityId: Int): Boolean = provider.isDummyPlayer(entityId)

    @Synchronized
    internal fun <T> withProviderForTest(provider: BlinkDummyStateProvider?, block: () -> T): T {
        val previous = this.provider
        this.provider = provider ?: NO_DUMMY
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
