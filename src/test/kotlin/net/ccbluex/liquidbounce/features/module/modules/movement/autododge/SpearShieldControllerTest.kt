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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpearShieldControllerTest {

    @Test
    fun `another item is interrupted only after an aligned viable shield route is verified`() {
        val withoutRoute = SpearShieldController.acquire<String>(
            SpearShieldState.Idle,
            acquisition(route = null, usingItem = true),
        )
        val withoutAlignment = SpearShieldController.acquire(
            SpearShieldState.Idle,
            acquisition(route = swapRoute, aligned = false, usingItem = true),
        )
        val viable = SpearShieldController.acquire(
            SpearShieldState.Idle,
            acquisition(route = swapRoute, usingItem = true),
        )

        assertEquals(SpearShieldState.Idle, withoutRoute.state)
        assertEquals(emptyList(), withoutRoute.commands)
        assertEquals(SpearShieldState.Idle, withoutAlignment.state)
        assertEquals(emptyList(), withoutAlignment.commands)
        assertIs<SpearShieldState.Interrupting<String>>(viable.state)
        assertEquals(
            listOf(SpearShieldCommand.ReserveOffhand, SpearShieldCommand.ReleaseItemUse),
            viable.commands,
        )
    }

    @Test
    fun `shield already used before acquisition remains outside module ownership`() {
        val transition = SpearShieldController.acquire<String>(
            SpearShieldState.Idle,
            acquisition(
                route = SpearShieldRoute.AlreadyEquipped(SpearShieldHand.OFF_HAND),
                usingItem = true,
                usingShield = true,
                useKeyDown = true,
            ),
        )

        assertEquals(SpearShieldState.Idle, transition.state)
        assertEquals(emptyList(), transition.commands)
    }

    @Test
    fun `interrupt equip and block are acknowledged as separate edge triggered commands`() {
        val interrupting = SpearShieldController.acquire(
            SpearShieldState.Idle,
            acquisition(route = swapRoute, usingItem = true),
        )

        val equipping = SpearShieldController.update(
            interrupting.state,
            observation(tick = 11, usingItem = false, inventoryLayout = SpearShieldInventoryLayout.ORIGINAL),
        )
        val waitingForEquip = SpearShieldController.update(
            equipping.state,
            observation(tick = 12, inventoryLayout = SpearShieldInventoryLayout.ORIGINAL),
        )
        val blocking = SpearShieldController.update(
            waitingForEquip.state,
            observation(tick = 13, inventoryLayout = SpearShieldInventoryLayout.EQUIPPED),
        )

        assertIs<SpearShieldState.Equipping<String>>(equipping.state)
        assertEquals(listOf(SpearShieldCommand.SwapIntoOffhand(snapshot)), equipping.commands)
        assertIs<SpearShieldState.Equipping<String>>(waitingForEquip.state)
        assertEquals(emptyList(), waitingForEquip.commands)
        assertIs<SpearShieldState.Blocking<String>>(blocking.state)
        assertEquals(listOf(SpearShieldCommand.StartShieldUse(SpearShieldHand.OFF_HAND)), blocking.commands)
        assertEquals(18L, blocking.state.blockReadyAtTick)
    }

    @Test
    fun `losing alignment lowers immediately and restores only after configured delay`() {
        val blocking = blockingWithSwap(tick = 10)

        val lowered = SpearShieldController.update(
            blocking,
            observation(tick = 11, aligned = false, shieldUseActive = true),
        )
        val waiting = SpearShieldController.update(
            lowered.state,
            observation(tick = 13, aligned = false),
        )
        val restoring = SpearShieldController.update(
            waiting.state,
            observation(tick = 14, aligned = false),
        )
        val restored = SpearShieldController.update(
            restoring.state,
            observation(tick = 15, aligned = false, inventoryLayout = SpearShieldInventoryLayout.ORIGINAL),
        )

        val loweredState = assertIs<SpearShieldState.LoweredAwaitingRestore<String>>(lowered.state)
        assertEquals(14L, loweredState.restoreAtTick)
        assertEquals(listOf(SpearShieldCommand.StopShieldUse), lowered.commands)
        assertEquals(emptyList(), waiting.commands)
        assertIs<SpearShieldState.Restoring<String>>(restoring.state)
        assertEquals(listOf(SpearShieldCommand.RestoreOffhand(snapshot)), restoring.commands)
        assertEquals(SpearShieldState.Idle, restored.state)
        assertEquals(listOf(SpearShieldCommand.ReleaseOffhandReservation), restored.commands)
    }

    @Test
    fun `fresh manual use press transfers ownership and defers lowering until manual use ends`() {
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
        val misaligned = SpearShieldController.update(
            transferred.state,
            observation(tick = 12, aligned = false, useKeyDown = true, shieldUseActive = true),
        )
        val keyReleased = SpearShieldController.update(
            misaligned.state,
            observation(tick = 13, aligned = false, useKeyDown = false, shieldUseActive = true),
        )
        val useEnded = SpearShieldController.update(
            keyReleased.state,
            observation(tick = 14, aligned = false, useKeyDown = false, shieldUseActive = false),
        )

        val transferredState = assertIs<SpearShieldState.Blocking<String>>(transferred.state)
        assertEquals(SpearShieldUseOwnership.MANUAL, transferredState.session.useOwnership)
        val misalignedState = assertIs<SpearShieldState.LoweredAwaitingRestore<String>>(misaligned.state)
        assertNull(misalignedState.restoreAtTick)
        assertEquals(emptyList(), misaligned.commands)
        assertNull(assertIs<SpearShieldState.LoweredAwaitingRestore<String>>(keyReleased.state).restoreAtTick)
        assertEquals(17L, assertIs<SpearShieldState.LoweredAwaitingRestore<String>>(useEnded.state).restoreAtTick)
    }

    @Test
    fun `use key held before acquisition remains module owned`() {
        val acquired = SpearShieldController.acquire<String>(
            SpearShieldState.Idle,
            acquisition(
                route = SpearShieldRoute.AlreadyEquipped(SpearShieldHand.OFF_HAND),
                useKeyDown = true,
            ),
        )
        val stillHeld = SpearShieldController.update(
            acquired.state,
            observation(tick = 11, useKeyDown = true, shieldUseActive = true),
        )
        val misaligned = SpearShieldController.update(
            stillHeld.state,
            observation(tick = 12, aligned = false, useKeyDown = true, shieldUseActive = true),
        )

        assertEquals(
            SpearShieldUseOwnership.MODULE,
            assertIs<SpearShieldState.Blocking<String>>(stillHeld.state).session.useOwnership,
        )
        assertEquals(listOf(SpearShieldCommand.StopShieldUse), misaligned.commands)
    }

    @Test
    fun `vanilla use release is suppressed only while AutoDodge owns an active shield use`() {
        val moduleOwned = blockingWithSwap(tick = 10)
        val manuallyOwned = moduleOwned.copy(
            session = moduleOwned.session.copy(useOwnership = SpearShieldUseOwnership.MANUAL),
        )
        val lowered = SpearShieldState.LoweredAwaitingRestore(
            session = moduleOwned.session,
            restoreAtTick = 14,
        )

        assertTrue(shouldPreserveAutoDodgeShieldUse(moduleOwned))
        assertFalse(shouldPreserveAutoDodgeShieldUse(manuallyOwned))
        assertFalse(shouldPreserveAutoDodgeShieldUse(lowered))
        assertFalse(shouldPreserveAutoDodgeShieldUse(SpearShieldState.Idle))
    }

    @Test
    fun `vanilla item use stays suppressed throughout a module owned shield session`() {
        val blocking = blockingWithSwap(tick = 10)
        val session = blocking.session
        val manuallyOwned = blocking.copy(
            session = session.copy(useOwnership = SpearShieldUseOwnership.MANUAL),
        )

        assertTrue(shouldSuppressAutoDodgeVanillaUse(SpearShieldState.Interrupting(session)))
        assertTrue(shouldSuppressAutoDodgeVanillaUse(SpearShieldState.Equipping(session)))
        assertTrue(shouldSuppressAutoDodgeVanillaUse(blocking))
        assertTrue(shouldSuppressAutoDodgeVanillaUse(SpearShieldState.LoweredAwaitingRestore(session, 14)))
        assertTrue(
            shouldSuppressAutoDodgeVanillaUse(
                SpearShieldState.Restoring(session, SpearShieldRestoreKind.STANDARD),
            ),
        )
        assertFalse(shouldSuppressAutoDodgeVanillaUse(manuallyOwned))
        assertFalse(shouldSuppressAutoDodgeVanillaUse(SpearShieldState.Idle))
        assertFalse(
            shouldSuppressAutoDodgeVanillaUse(
                SpearShieldState.Aborted(session, SpearShieldAbortReason.INVENTORY_CHANGED),
            ),
        )
    }

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
