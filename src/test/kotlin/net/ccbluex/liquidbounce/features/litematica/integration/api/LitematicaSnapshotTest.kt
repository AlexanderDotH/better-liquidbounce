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
package net.ccbluex.liquidbounce.features.litematica.integration.api

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPoint
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class LitematicaSnapshotTest {

    @Test
    fun `bounded scan rejects an exhausted per-tick cell budget`() {
        assertFailsWith<IllegalArgumentException> {
            LitematicaScanRequest(
                center = LitematicaPoint(0.0, 64.0, 0.0),
                range = 4.5,
                maxCells = 0,
                timeBudgetNanos = 1_000_000L,
            )
        }
    }
}
