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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientLifecycleStateTest {

    @AfterEach
    fun reset() {
        ClientLifecycleState.isInitialized = false
    }

    @Test
    fun `bootstrap readiness is false until explicitly initialized`() {
        ClientLifecycleState.isInitialized = false
        assertFalse(ClientLifecycleState.isInitialized)

        ClientLifecycleState.isInitialized = true
        assertTrue(ClientLifecycleState.isInitialized)
    }
}
