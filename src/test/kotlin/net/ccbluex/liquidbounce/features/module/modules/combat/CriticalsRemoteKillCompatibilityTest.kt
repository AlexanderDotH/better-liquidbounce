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

import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes.shouldRunPacketCriticals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CriticalsRemoteKillCompatibilityTest {

    @Test
    fun `packet criticals yields its movement sequence to a remote kill route`() {
        assertTrue(shouldRunPacketCriticals(remoteKillOwnsMovement = false))
        assertFalse(shouldRunPacketCriticals(remoteKillOwnsMovement = true))
    }
}
