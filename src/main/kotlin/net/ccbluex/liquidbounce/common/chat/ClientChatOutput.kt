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

package net.ccbluex.liquidbounce.common.chat

import net.minecraft.network.chat.Component

fun interface ClientChatSink {
    fun publish(text: Component)
}

object ClientChatOutput {

    private val DISABLED = ClientChatSink { }

    @Volatile
    private var sink: ClientChatSink = DISABLED

    @Synchronized
    fun install(sink: ClientChatSink) {
        check(this.sink === DISABLED) { "Client chat sink is already installed" }
        this.sink = sink
    }

    fun publish(text: Component): Boolean {
        val current = sink
        if (current === DISABLED) {
            return false
        }

        current.publish(text)
        return true
    }

    @Synchronized
    internal fun <T> withSinkForTest(candidate: ClientChatSink?, block: () -> T): T {
        val previous = sink
        sink = candidate ?: DISABLED
        return try {
            block()
        } finally {
            sink = previous
        }
    }
}
