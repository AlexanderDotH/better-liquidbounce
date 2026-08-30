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
package net.ccbluex.liquidbounce.features.input

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputTrackerTest {

    @Test
    fun `keyboard press timestamps participate in fresh-use detection`() {
        val keyCode = Int.MAX_VALUE

        InputTracker.recordKeyboardPress(keyCode)

        assertTrue(InputTracker.wasKeyPressedRecently(keyCode, withinMs = 1_000))
    }

    @Test
    fun `scan-code press timestamps participate in fresh-use detection`() {
        val scanCode = Int.MAX_VALUE

        InputTracker.recordScanCodePress(scanCode)

        assertTrue(InputTracker.wasScanCodePressedRecently(scanCode, withinMs = 1_000))
    }
}
