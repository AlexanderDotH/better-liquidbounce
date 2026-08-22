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

package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaceKillDebugConsoleTest {

    @Test
    fun `availability transition reset permits the same later rejection once`() {
        val messages = mutableListOf<String>()
        val console = MaceKillDebugConsole(enabled = { true }, sink = messages::add)

        repeat(2) {
            console.logChanged("admission", "REJECTED", fingerprint = { "BACKOFF" }) {
                listOf("reason" to "BACKOFF")
            }
        }
        console.clearTransition("admission")
        console.logChanged("admission", "REJECTED", fingerprint = { "BACKOFF" }) {
            listOf("reason" to "BACKOFF")
        }

        assertEquals(2, messages.size)
    }
}
