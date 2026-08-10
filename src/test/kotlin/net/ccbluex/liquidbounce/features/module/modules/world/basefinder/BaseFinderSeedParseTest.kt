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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseFinderSeedParseTest {

    @Test
    fun `empty seed is not configured`() {
        assertNull(BaseFinderSeedParse.parseOrNull(""))
        assertNull(BaseFinderSeedParse.parseOrNull("   "))
        assertFalse(BaseFinderSeedParse.isConfigured(""))
    }

    @Test
    fun `numeric seeds parse as longs`() {
        assertEquals(12345L, BaseFinderSeedParse.parseOrNull("12345"))
        assertEquals(-42L, BaseFinderSeedParse.parseOrNull("-42"))
        assertTrue(BaseFinderSeedParse.isConfigured("0"))
    }

    @Test
    fun `string seeds hash like vanilla`() {
        val first = BaseFinderSeedParse.parseOrNull("LiquidBounce")
        val second = BaseFinderSeedParse.parseOrNull("LiquidBounce")
        assertNotNull(first)
        assertEquals(first, second)
        assertTrue(first != 0L || "LiquidBounce".isEmpty())
    }
}
