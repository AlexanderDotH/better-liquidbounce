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
package net.ccbluex.liquidbounce.common

import net.minecraft.world.level.block.Block

object GhostHandHook {
    private data class Providers(
        val running: () -> Boolean,
        val targeted: (Block) -> Boolean,
    )

    private val DISABLED = Providers({ false }, { false })

    @Volatile
    private var providers = DISABLED

    @JvmStatic
    @Synchronized
    fun install(running: () -> Boolean, isTargeted: (Block) -> Boolean) {
        check(providers === DISABLED) { "Ghost hand hook is already installed" }
        providers = Providers(running, isTargeted)
    }

    @JvmStatic
    fun isRunning(): Boolean = providers.running()

    @JvmStatic
    fun isTargeted(block: Block): Boolean = providers.targeted(block)

    @Synchronized
    internal fun <T> withProvidersForTest(
        running: (() -> Boolean)?,
        targeted: ((Block) -> Boolean)?,
        block: () -> T,
    ): T {
        val previous = providers
        providers = if (running == null || targeted == null) DISABLED else Providers(running, targeted)
        return try {
            block()
        } finally {
            providers = previous
        }
    }
}
