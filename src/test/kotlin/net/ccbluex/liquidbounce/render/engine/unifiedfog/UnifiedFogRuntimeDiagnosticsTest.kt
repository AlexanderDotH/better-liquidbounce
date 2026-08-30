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
package net.ccbluex.liquidbounce.render.engine.unifiedfog

import net.ccbluex.liquidbounce.render.engine.UnifiedFogDebugState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedFogRuntimeDiagnosticsTest {

    @Test
    fun `runtime diagnostic identifies rendered and skipped state transitions`() {
        val rendered = state(passCount = 5, skipReason = null)
        val skipped = state(passCount = 0, skipReason = "DH depth unavailable")

        assertEquals("Unified|true|OPEN_GL|5|rendered", rendered.diagnosticKey())
        assertEquals("Unified|true|OPEN_GL|0|DH depth unavailable", skipped.diagnosticKey())
        assertTrue(rendered.runtimeSummary().contains("passes=5"))
        assertTrue(skipped.runtimeSummary().contains("DH depth unavailable"))
    }

    private fun state(passCount: Int, skipReason: String?) = UnifiedFogDebugState(
        engine = "Unified",
        vanillaReady = true,
        distantHorizonsReady = true,
        distantHorizonsBackend = "OPEN_GL",
        distantHorizonsApiVersion = "7.1.0",
        frameAge = 1,
        horizonStartBlocks = 2867.2f,
        horizonEndBlocks = 4096f,
        passCount = passCount,
        skipReason = skipReason,
    )
}
