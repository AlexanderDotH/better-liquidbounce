/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoDodgePacketWorldSafetyTest {

    @Test
    fun `route accepts only loaded bordered collision-free origin destination and sweep`() {
        assertTrue(isAutoDodgePacketRouteSafe(SAFE_BOX, SAFE_BOX, listOf(SAFE_BOX, SAFE_BOX)))

        listOf(
            AutoDodgePacketBoxSafety(loaded = false, withinWorldBorder = true, collisionFree = true),
            AutoDodgePacketBoxSafety(loaded = true, withinWorldBorder = false, collisionFree = true),
            AutoDodgePacketBoxSafety(loaded = true, withinWorldBorder = true, collisionFree = false),
        ).forEach { unsafe ->
            assertFalse(isAutoDodgePacketRouteSafe(unsafe, SAFE_BOX, listOf(SAFE_BOX)))
            assertFalse(isAutoDodgePacketRouteSafe(SAFE_BOX, unsafe, listOf(SAFE_BOX)))
            assertFalse(isAutoDodgePacketRouteSafe(SAFE_BOX, SAFE_BOX, listOf(SAFE_BOX, unsafe)))
        }
    }

    @Test
    fun `grounded route requires support at both endpoints and rejects void at either endpoint`() {
        assertTrue(groundSafe())
        assertFalse(groundSafe(originSupported = false))
        assertFalse(groundSafe(destinationSupported = false))
        assertFalse(groundSafe(originOverVoid = true))
        assertFalse(groundSafe(destinationOverVoid = true))
    }

    @Test
    fun `airborne route does not require ground support or non-void terrain`() {
        assertTrue(
            isAutoDodgePacketGroundSafe(
                requiresSupport = false,
                originSupported = false,
                destinationSupported = false,
                originOverVoid = true,
                destinationOverVoid = true,
            )
        )
    }

    private fun groundSafe(
        originSupported: Boolean = true,
        destinationSupported: Boolean = true,
        originOverVoid: Boolean = false,
        destinationOverVoid: Boolean = false,
    ) = isAutoDodgePacketGroundSafe(
        requiresSupport = true,
        originSupported = originSupported,
        destinationSupported = destinationSupported,
        originOverVoid = originOverVoid,
        destinationOverVoid = destinationOverVoid,
    )

    private companion object {
        val SAFE_BOX = AutoDodgePacketBoxSafety(
            loaded = true,
            withinWorldBorder = true,
            collisionFree = true,
        )
    }
}
