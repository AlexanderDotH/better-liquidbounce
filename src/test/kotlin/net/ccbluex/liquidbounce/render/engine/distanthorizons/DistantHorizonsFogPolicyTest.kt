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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DistantHorizonsFogPolicyTest {

    @AfterEach
    fun restoreFailClosedDefault() {
        DistantHorizonsFogPolicy.install { false }
    }

    @Test
    fun `policy fails closed until feature adapter is installed`() {
        DistantHorizonsFogPolicy.install { false }

        assertFalse(DistantHorizonsFogPolicy.shouldSuppressNativeFog())
    }

    @Test
    fun `policy evaluates installed feature adapter for every render event`() {
        var suppress = false
        DistantHorizonsFogPolicy.install { suppress }

        assertFalse(DistantHorizonsFogPolicy.shouldSuppressNativeFog())
        suppress = true
        assertTrue(DistantHorizonsFogPolicy.shouldSuppressNativeFog())
    }
}
