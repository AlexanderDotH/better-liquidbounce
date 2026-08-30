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
package net.ccbluex.liquidbounce.features.module.modules.render.skinchanger

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkinSessionEndpointBridgeTest {

    @Test
    fun `missing provider rejects upload and installed provider preserves base url`() {
        SkinSessionEndpointBridge.withProviderForTest(null) {
            assertNull(SkinSessionEndpointBridge.baseUrl(Any()))
        }
        SkinSessionEndpointBridge.withProviderForTest(SkinSessionEndpointHook { "https://session.example" }) {
            assertEquals("https://session.example", SkinSessionEndpointBridge.baseUrl(Any()))
        }
    }
}
