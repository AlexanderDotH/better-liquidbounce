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
package net.ccbluex.liquidbounce.features.chat

import net.ccbluex.liquidbounce.features.chat.packet.AxoUser
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidChatUserRegistryTest {

    @Test
    fun `received LiquidChat user is remembered by profile UUID`() {
        val registry = LiquidChatUserRegistry()
        val user = AxoUser("Alex", UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b"))

        registry.remember(user)

        assertTrue(registry.contains(user.uuid))
        assertEquals(user, registry.user(user.uuid))
    }

    @Test
    fun `new message updates the remembered name without duplicating the user`() {
        val registry = LiquidChatUserRegistry()
        val uuid = UUID.fromString("922ad8ba-4b1e-4c6c-b217-61dba0d21731")

        registry.remember(AxoUser("OldName", uuid))
        registry.remember(AxoUser("NewName", uuid))

        assertEquals(listOf(AxoUser("NewName", uuid)), registry.users().toList())
    }

    @Test
    fun `clearing the LiquidChat session removes remembered users`() {
        val registry = LiquidChatUserRegistry()
        val user = AxoUser("Alex", UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b"))
        registry.remember(user)

        registry.clear()

        assertFalse(registry.contains(user.uuid))
        assertTrue(registry.users().isEmpty())
    }
}
