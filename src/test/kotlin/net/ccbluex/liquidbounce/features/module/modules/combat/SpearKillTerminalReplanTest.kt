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
package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillTerminalReplanTest {

    @Test
    fun `transient target prediction miss keeps the terminal route pending`() {
        assertTrue(shouldKeepSpearKillTerminalPending(SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE))
        assertTrue(shouldKeepSpearKillTerminalPending(SpearKillPacketRouteReplanResult.INSTALLED))
    }

    @Test
    fun `confirmed blocked terminal route ends the attack`() {
        assertFalse(shouldKeepSpearKillTerminalPending(SpearKillPacketRouteReplanResult.BLOCKED))
    }
}
