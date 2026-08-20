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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchantRuntimePolicyTest {

    @Test
    fun `acquisition requires a clear gui and the normal inventory menu`() {
        fun canAcquire(
            tick: Int = 20,
            suppressedUntilTick: Int = 20,
            guiOpen: Boolean = false,
            inventoryMenuActive: Boolean = true,
            safeHandAvailable: Boolean = true,
            hasActiveRule: Boolean = true,
        ) = MerchantAcquisitionPolicy.canAcquire(
            tick,
            suppressedUntilTick,
            guiOpen,
            inventoryMenuActive,
            safeHandAvailable,
            hasActiveRule,
        )

        assertTrue(canAcquire())
        assertFalse(canAcquire(tick = 19))
        assertFalse(canAcquire(guiOpen = true))
        assertFalse(canAcquire(inventoryMenuActive = false))
        assertFalse(canAcquire(safeHandAvailable = false))
        assertFalse(canAcquire(hasActiveRule = false))
    }

    @Test
    fun `rotation completion queues interaction only for the locked target`() {
        val rotating = MerchantSessionState.Rotating(targetId = 41, sinceTick = 10)

        assertTrue(MerchantRotationGate.canInteract(rotating, targetId = 41, angleDifference = 1.9f, threshold = 2f))
        assertFalse(MerchantRotationGate.canInteract(rotating, targetId = 42, angleDifference = 1f, threshold = 2f))
        assertFalse(MerchantRotationGate.canInteract(rotating, targetId = 41, angleDifference = 2.1f, threshold = 2f))
        assertFalse(
            MerchantRotationGate.canInteract(
                MerchantSessionState.Opening(targetId = 41, sinceTick = 10),
                targetId = 41,
                angleDifference = 1f,
                threshold = 2f,
            ),
        )
    }

    @Test
    fun `owned screen requires running mode and exact active menu identity`() {
        assertTrue(MerchantScreenClaimPolicy.canClaim(modeRunning = true, activeMenuMatches = true))
        assertFalse(MerchantScreenClaimPolicy.canClaim(modeRunning = false, activeMenuMatches = true))
        assertFalse(MerchantScreenClaimPolicy.canClaim(modeRunning = true, activeMenuMatches = false))
    }

    @Test
    fun `target loss timeout and unexpected gui close owned session and remember retry`() {
        listOf(
            MerchantSessionEndCause.TARGET_LOST,
            MerchantSessionEndCause.TIMEOUT,
            MerchantSessionEndCause.UNEXPECTED_GUI,
            MerchantSessionEndCause.TRADE_BLOCKED,
        ).forEach { cause ->
            val decision = MerchantCleanupPolicy.forCause(cause)
            assertTrue(decision.closeOwnedMenu)
            assertTrue(decision.rememberRetry)
        }
    }

    @Test
    fun `server close never sends a stale client close`() {
        val decision = MerchantCleanupPolicy.forCause(MerchantSessionEndCause.SERVER_CLOSE)

        assertFalse(decision.closeOwnedMenu)
        assertTrue(decision.rememberRetry)
    }

    @Test
    fun `disable mode switch world change and disconnect clear all session history`() {
        listOf(
            MerchantSessionEndCause.DISABLE_OR_MODE_SWITCH,
            MerchantSessionEndCause.WORLD_CHANGE,
            MerchantSessionEndCause.DISCONNECT,
        ).forEach { cause ->
            val decision = MerchantCleanupPolicy.forCause(cause)
            assertFalse(decision.rememberRetry)
        }

        assertTrue(MerchantCleanupPolicy.forCause(MerchantSessionEndCause.DISABLE_OR_MODE_SWITCH).closeOwnedMenu)
        assertTrue(MerchantCleanupPolicy.forCause(MerchantSessionEndCause.WORLD_CHANGE).closeOwnedMenu)
        assertFalse(MerchantCleanupPolicy.forCause(MerchantSessionEndCause.DISCONNECT).closeOwnedMenu)
    }
}
