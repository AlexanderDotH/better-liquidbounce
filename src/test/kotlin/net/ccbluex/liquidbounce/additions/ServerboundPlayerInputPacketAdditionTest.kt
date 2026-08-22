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
package net.ccbluex.liquidbounce.additions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerboundPlayerInputPacketAdditionTest {

    @Test
    fun `jump suppression clears a physical jump before serialization`() {
        assertFalse(resolveServerboundPlayerInputJump(rawJump = true, suppressJump = true))
        assertFalse(resolveServerboundPlayerInputJump(rawJump = false, suppressJump = true))
        assertTrue(resolveServerboundPlayerInputJump(rawJump = true, suppressJump = false))
    }
}
