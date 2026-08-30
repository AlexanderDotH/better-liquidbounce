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
package net.ccbluex.liquidbounce.utils.movement.remote

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteMovementOwnershipTest {

    @Test
    fun `neutral and legacy ownership facades share one exclusive lease`() {
        val lease = RemoteMovementOwnership.tryAcquire("ReachInteractable")

        try {
            requireNotNull(lease)
            assertTrue(RemoteMovementOwnership.active)
            assertTrue(RemoteKillMovementOwnership.active)
            assertEquals("ReachInteractable", RemoteMovementOwnership.currentOwner)
            assertEquals("ReachInteractable", RemoteKillMovementOwnership.currentOwner)
            assertNull(RemoteKillMovementOwnership.tryAcquire("SpearKill"))
        } finally {
            lease?.close()
        }

        assertFalse(RemoteMovementOwnership.active)
        assertFalse(RemoteKillMovementOwnership.active)
    }
}
