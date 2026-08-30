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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpearShieldRestorationTest {
    @Test
    fun `changed inventory aborts without attempting a guessed restore`() {
        val blocking = blockingWithSwap(tick = 10)

        val aborted = SpearShieldController.update(
            blocking,
            observation(
                tick = 11,
                shieldUseActive = true,
                inventoryLayout = SpearShieldInventoryLayout.CHANGED,
            ),
        )

        val state = assertIs<SpearShieldState.Aborted<String>>(aborted.state)
        assertEquals(SpearShieldAbortReason.INVENTORY_CHANGED, state.reason)
        assertEquals(
            listOf(SpearShieldCommand.StopShieldUse, SpearShieldCommand.ReleaseOffhandReservation),
            aborted.commands,
        )
    }

    @Test
    fun `broken swapped shield restores displaced offhand but broken hand shield aborts`() {
        val swapped = SpearShieldController.update(
            blockingWithSwap(tick = 10),
            observation(
                tick = 11,
                shieldUseActive = true,
                inventoryLayout = SpearShieldInventoryLayout.SHIELD_BROKEN,
            ),
        )
        val alreadyEquipped = SpearShieldController.acquire<String>(
            SpearShieldState.Idle,
            acquisition(route = SpearShieldRoute.AlreadyEquipped(SpearShieldHand.OFF_HAND)),
        )
        val brokenInHand = SpearShieldController.update(
            alreadyEquipped.state,
            observation(
                tick = 11,
                shieldUseActive = true,
                inventoryLayout = SpearShieldInventoryLayout.SHIELD_BROKEN,
            ),
        )

        assertIs<SpearShieldState.Restoring<String>>(swapped.state)
        assertEquals(
            listOf(SpearShieldCommand.StopShieldUse, SpearShieldCommand.RestoreOffhand(snapshot)),
            swapped.commands,
        )
        val brokenRestoreCompleted = SpearShieldController.update(
            swapped.state,
            observation(
                tick = 12,
                inventoryLayout = SpearShieldInventoryLayout.RESTORED_AFTER_BREAK,
            ),
        )
        assertEquals(SpearShieldState.Idle, brokenRestoreCompleted.state)
        assertEquals(
            listOf(SpearShieldCommand.ReleaseOffhandReservation),
            brokenRestoreCompleted.commands,
        )
        assertEquals(
            SpearShieldAbortReason.SHIELD_BROKEN,
            assertIs<SpearShieldState.Aborted<String>>(brokenInHand.state).reason,
        )
        assertEquals(listOf(SpearShieldCommand.StopShieldUse), brokenInHand.commands)
    }

    @Test
    fun `disable stops module use and immediately begins exact restoration`() {
        val disabled = SpearShieldController.disable(
            blockingWithSwap(tick = 10),
            observation(tick = 11, shieldUseActive = true),
        )

        assertIs<SpearShieldState.Restoring<String>>(disabled.state)
        assertEquals(
            listOf(SpearShieldCommand.StopShieldUse, SpearShieldCommand.RestoreOffhand(snapshot)),
            disabled.commands,
        )
    }

    @Test
    fun `disable leaves manually owned shield active until user releases it`() {
        val acquired = SpearShieldController.acquire<String>(
            SpearShieldState.Idle,
            acquisition(
                route = SpearShieldRoute.AlreadyEquipped(SpearShieldHand.OFF_HAND),
                useKeyDown = false,
            ),
        )
        val transferred = SpearShieldController.update(
            acquired.state,
            observation(tick = 11, useKeyDown = true, shieldUseActive = true),
        )

        val disabled = SpearShieldController.disable(
            transferred.state,
            observation(tick = 12, useKeyDown = true, shieldUseActive = true),
        )

        val state = assertIs<SpearShieldState.LoweredAwaitingRestore<String>>(disabled.state)
        assertEquals(SpearShieldUseOwnership.MANUAL, state.session.useOwnership)
        assertNull(state.restoreAtTick)
        assertEquals(emptyList(), disabled.commands)
    }

    @Test
    fun `world reset discards state without sending stale actions`() {
        val transition = SpearShieldController.worldReset<String>()

        assertEquals(SpearShieldState.Idle, transition.state)
        assertEquals(emptyList(), transition.commands)
    }

    @Test
    fun `snapshot classifies only exact original equipped and broken layouts`() {
        fun classify(
            containerId: Int,
            source: String,
            offhand: String,
            expectBrokenShieldRestored: Boolean = false,
        ) = snapshot.classify(
            containerId = containerId,
            sourceStack = source,
            offhandStack = offhand,
            stacksMatch = String::equals,
            isEmpty = String::isEmpty,
            expectBrokenShieldRestored = expectBrokenShieldRestored,
        )

        assertEquals(SpearShieldInventoryLayout.ORIGINAL, classify(9, "shield", "totem"))
        assertEquals(SpearShieldInventoryLayout.EQUIPPED, classify(9, "totem", "shield"))
        assertEquals(SpearShieldInventoryLayout.SHIELD_BROKEN, classify(9, "totem", ""))
        assertEquals(
            SpearShieldInventoryLayout.RESTORED_AFTER_BREAK,
            classify(9, "", "totem", expectBrokenShieldRestored = true),
        )
        assertEquals(SpearShieldInventoryLayout.CHANGED, classify(9, "apple", "shield"))
        assertEquals(SpearShieldInventoryLayout.CHANGED, classify(10, "shield", "totem"))
    }

    private fun blockingWithSwap(tick: Long): SpearShieldState.Blocking<String> {
        val acquired = SpearShieldController.acquire(
            SpearShieldState.Idle,
            acquisition(tick = tick, route = swapRoute),
        )
        val blocking = SpearShieldController.update(
            acquired.state,
            observation(tick = tick, inventoryLayout = SpearShieldInventoryLayout.EQUIPPED),
        )

        return assertIs(blocking.state)
    }

    private fun acquisition(
        tick: Long = 10,
        route: SpearShieldRoute<String>?,
        aligned: Boolean = true,
        usingItem: Boolean = false,
        usingShield: Boolean = false,
        useKeyDown: Boolean = false,
    ) = SpearShieldAcquisition(
        tick = tick,
        aligned = aligned,
        route = route,
        usingItem = usingItem,
        usingShield = usingShield,
        useKeyDown = useKeyDown,
        policy = policy,
    )

    private fun observation(
        tick: Long,
        threatPresent: Boolean = true,
        aligned: Boolean = true,
        usingItem: Boolean = false,
        shieldUseActive: Boolean = false,
        useKeyDown: Boolean = false,
        inventoryLayout: SpearShieldInventoryLayout = SpearShieldInventoryLayout.EQUIPPED,
    ) = SpearShieldObservation(
        tick = tick,
        threatPresent = threatPresent,
        aligned = aligned,
        usingItem = usingItem,
        shieldUseActive = shieldUseActive,
        useKeyDown = useKeyDown,
        inventoryLayout = inventoryLayout,
    )

    companion object {
        private val policy = SpearShieldPolicy(
            horizontalBlockingAngleDegrees = 60F,
            blockDelayTicks = 5,
            releaseDelayTicks = 3,
        )
        private val snapshot = SpearShieldInventorySnapshot(
            containerId = 9,
            sourceSlot = 12,
            shieldStack = "shield",
            displacedOffhandStack = "totem",
        )
        private val swapRoute = SpearShieldRoute.SwapToOffhand(snapshot)
    }
}
