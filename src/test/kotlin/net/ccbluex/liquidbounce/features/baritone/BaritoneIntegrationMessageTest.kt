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
package net.ccbluex.liquidbounce.features.baritone

import net.ccbluex.liquidbounce.features.baritone.adapter.BaritoneAdapterMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaritoneIntegrationMessageTest {

    @Test
    fun `debug logs remain dashboard-only`() {
        assertNull(BaritoneAdapterMessage.Log("path calculated").toLiquidBounceNotification())
    }

    @Test
    fun `upstream errors become bounded LiquidBounce notifications`() {
        val notification = BaritoneAdapterMessage.Notification("No path found", error = true)
            .toLiquidBounceNotification()

        assertEquals("Baritone", notification?.title)
        assertEquals("No path found", notification?.message)
        assertTrue(notification?.error == true)
    }
}
