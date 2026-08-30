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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientChatOutputTest {

    @Test
    fun `publish forwards the original component to the installed sink`() {
        val expected = Component.literal("message")
        var actual: Component? = null

        ClientChatOutput.withSinkForTest(ClientChatSink { actual = it }) {
            assertTrue(ClientChatOutput.publish(expected))
        }

        assertSame(expected, actual)
    }

    @Test
    fun `publish reports when no sink is installed`() {
        ClientChatOutput.withSinkForTest(null) {
            assertFalse(ClientChatOutput.publish(Component.empty()))
        }
    }
}
