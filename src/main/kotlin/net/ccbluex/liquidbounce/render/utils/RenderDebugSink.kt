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
package net.ccbluex.liquidbounce.render.utils

fun interface RenderDebugAdapter {
    fun publishRenderPassCount(count: Int)
}

object RenderDebugSink {

    private val DISABLED = RenderDebugAdapter { }

    @Volatile
    private var adapter: RenderDebugAdapter = DISABLED

    @JvmStatic
    @Synchronized
    fun install(adapter: RenderDebugAdapter) {
        check(this.adapter === DISABLED) { "Render debug adapter is already installed" }
        this.adapter = adapter
    }

    fun publishRenderPassCount(count: Int) = adapter.publishRenderPassCount(count)

    @Synchronized
    internal fun <T> withSinkForTest(candidate: RenderDebugAdapter?, block: () -> T): T {
        val previous = adapter
        adapter = candidate ?: DISABLED
        return try {
            block()
        } finally {
            adapter = previous
        }
    }
}
