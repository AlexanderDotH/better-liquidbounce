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
package net.ccbluex.liquidbounce.render.engine.distanthorizons

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DistantHorizonsOptionalClassLoadingTest {

    @Test
    fun `always loaded provider links when compile only DH API is absent`() {
        val loader = DistantHorizonsOptionalClassLoadingTest::class.java.classLoader

        assertTrue(runCatching { Class.forName(DH_API_CLASS, false, loader) }.isFailure)
        assertNotNull(Class.forName(DEPTH_PROVIDER_CLASS, false, loader))
    }

    private companion object {
        const val DH_API_CLASS = "com.seibel.distanthorizons.api.DhApi"
        const val DEPTH_PROVIDER_CLASS =
            "net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthTextureProvider"
    }
}
