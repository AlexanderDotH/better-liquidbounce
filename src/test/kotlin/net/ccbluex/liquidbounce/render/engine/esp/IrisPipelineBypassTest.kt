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

package net.ccbluex.liquidbounce.render.engine.esp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IrisPipelineBypassTest {

    @AfterEach
    fun resetFlag() {
        TestFlag.bypass = false
    }

    @Test
    fun `scopes and restores the bypass flag`() {
        val bypass = StaticBooleanBypass(TestFlag::class.java.getField("bypass"))

        bypass.run {
            assertTrue(TestFlag.bypass)
        }

        assertFalse(TestFlag.bypass)
    }

    @Test
    fun `preserves an existing bypass and supports nesting`() {
        val bypass = StaticBooleanBypass(TestFlag::class.java.getField("bypass"))
        TestFlag.bypass = true

        bypass.run {
            bypass.run {
                assertTrue(TestFlag.bypass)
            }
            assertTrue(TestFlag.bypass)
        }

        assertTrue(TestFlag.bypass)
    }

    @Test
    fun `restores the bypass after a failure`() {
        val bypass = StaticBooleanBypass(TestFlag::class.java.getField("bypass"))

        assertThrows(IllegalStateException::class.java) {
            bypass.run {
                assertTrue(TestFlag.bypass)
                error("boom")
            }
        }

        assertFalse(TestFlag.bypass)
    }

    object TestFlag {
        @JvmField
        var bypass = false
    }
}
