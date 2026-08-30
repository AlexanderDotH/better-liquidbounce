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

package net.ccbluex.liquidbounce.config.autoconfig

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AutoConfigContextTest {
    @AfterEach
    fun restoreDefaults() {
        AutoConfigContext.onLoadingFinished {}
        AutoConfigContext.loadingNow = false
        AutoConfigContext.includeConfiguration = IncludeConfiguration.DEFAULT
    }

    @Test
    fun `completion callback runs exactly when loading becomes false`() {
        var completions = 0
        AutoConfigContext.onLoadingFinished { completions++ }

        AutoConfigContext.loadingNow = true
        assertEquals(0, completions)
        AutoConfigContext.loadingNow = false
        assertEquals(1, completions)
        assertFalse(AutoConfigContext.loadingNow)
    }

    @Test
    fun `inclusion state is independent from integration implementation`() {
        val selected = IncludeConfiguration(includeBinds = false)

        AutoConfigContext.includeConfiguration = selected

        assertEquals(selected, AutoConfigContext.includeConfiguration)
    }
}
