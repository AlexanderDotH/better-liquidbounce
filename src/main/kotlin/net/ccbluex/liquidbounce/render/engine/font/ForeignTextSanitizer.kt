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
package net.ccbluex.liquidbounce.render.engine.font

import net.minecraft.network.chat.Component

fun interface ForeignTextSanitizerAdapter {
    fun sanitize(component: Component): Component
}

object ForeignTextSanitizer {

    private val IDENTITY = ForeignTextSanitizerAdapter { it }

    @Volatile
    private var adapter: ForeignTextSanitizerAdapter = IDENTITY

    @JvmStatic
    @Synchronized
    fun install(adapter: ForeignTextSanitizerAdapter) {
        check(this.adapter === IDENTITY) { "Foreign text sanitizer is already installed" }
        this.adapter = adapter
    }

    fun sanitize(component: Component): Component = adapter.sanitize(component)

    @Synchronized
    internal fun <T> withSanitizerForTest(candidate: ForeignTextSanitizerAdapter?, block: () -> T): T {
        val previous = adapter
        adapter = candidate ?: IDENTITY
        return try {
            block()
        } finally {
            adapter = previous
        }
    }
}
