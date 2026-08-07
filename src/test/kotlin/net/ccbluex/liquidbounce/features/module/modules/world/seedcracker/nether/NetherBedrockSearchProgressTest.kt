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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NetherBedrockSearchProgressTest {

    @Test
    fun `half of the search domain estimates one elapsed interval remaining`() {
        val progress = NetherBedrockSearchProgress(
            checkedPrefixes = NetherBedrockPrefixRange.TOTAL_PREFIXES / 2L,
            elapsedMillis = 12_000L,
        )

        assertEquals(50.0, progress.percent)
        assertEquals(12_000L, progress.estimatedRemainingMillis)
    }

    @Test
    fun `an untouched search has no misleading eta`() {
        val progress = NetherBedrockSearchProgress(checkedPrefixes = 0L, elapsedMillis = 0L)

        assertEquals(0.0, progress.percent)
        assertNull(progress.estimatedRemainingMillis)
    }
}
