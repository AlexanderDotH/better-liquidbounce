/*
 * This file is part of the LiquidBounce project - https://liquidbounce.net
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.trialchamber

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrialResourceStateTest {

    private val chestLeft = TrialResourcePosition(10, 20, 30)
    private val chestRight = TrialResourcePosition(11, 20, 30)
    private val barrel = TrialResourcePosition(12, 20, 30)
    private val dispenser = TrialResourcePosition(13, 20, 30)
    private val pot = TrialResourcePosition(14, 20, 30)
    private val vault = TrialResourcePosition(15, 20, 30)
    private val ominousVault = TrialResourcePosition(16, 20, 30)

    @Test
    fun `interaction attempt alone does not visit menu resource`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.BARREL, barrel)

        val attempt = state.beginMenuVisit(barrel, tick = 10)

        assertNotNull(attempt)
        assertFalse(state.snapshot().resourceAt(barrel)!!.visited)
    }

    @Test
    fun `successful confirmation visits chest barrel and dispenser`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.CHEST, chestLeft)
        state.observeResource(TrialResourceKind.BARREL, barrel)
        state.observeResource(TrialResourceKind.DISPENSER, dispenser)

        listOf(chestLeft, barrel, dispenser).forEachIndexed { index, position ->
            val attempt = state.beginMenuVisit(position, tick = index.toLong())

            assertTrue(state.confirmMenuVisit(requireNotNull(attempt), tick = index.toLong() + 1))
        }

        val snapshot = state.snapshot()
        assertTrue(snapshot.resourceAt(chestLeft)!!.visited)
        assertTrue(snapshot.resourceAt(barrel)!!.visited)
        assertTrue(snapshot.resourceAt(dispenser)!!.visited)
    }

    @Test
    fun `confirmation must match the latest interaction attempt`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.BARREL, barrel)
        state.observeResource(TrialResourceKind.DISPENSER, dispenser)
        val staleAttempt = requireNotNull(state.beginMenuVisit(barrel, tick = 10))
        val currentAttempt = requireNotNull(state.beginMenuVisit(dispenser, tick = 11))

        assertFalse(state.confirmMenuVisit(staleAttempt, tick = 12))
        assertFalse(state.snapshot().resourceAt(barrel)!!.visited)
        assertTrue(state.confirmMenuVisit(currentAttempt, tick = 12))
        assertTrue(state.snapshot().resourceAt(dispenser)!!.visited)
    }

    @Test
    fun `late menu confirmation does not visit resource`() {
        val state = TrialResourceState(menuOpenConfirmationWindowTicks = 4)
        state.observeResource(TrialResourceKind.BARREL, barrel)
        val attempt = requireNotNull(state.beginMenuVisit(barrel, tick = 10))

        assertFalse(state.confirmMenuVisit(attempt, tick = 15))
        assertFalse(state.snapshot().resourceAt(barrel)!!.visited)
    }

    @Test
    fun `double chest halves share one visit state`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.CHEST, chestRight)
        state.observeResource(TrialResourceKind.CHEST, chestLeft, connectedChestHalf = chestRight)

        val attempt = requireNotNull(state.beginMenuVisit(chestRight, tick = 10))
        state.confirmMenuVisit(attempt, tick = 11)

        val snapshot = state.snapshot()
        assertEquals(1, snapshot.resources.count { it.kind == TrialResourceKind.CHEST })
        assertEquals(setOf(chestLeft, chestRight), snapshot.resourceAt(chestLeft)!!.positions.toSet())
        assertTrue(snapshot.resourceAt(chestLeft)!!.visited)
        assertTrue(snapshot.resourceAt(chestRight)!!.visited)
    }

    @Test
    fun `decorated pot is not menu visitable and disappears on observed removal`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.DECORATED_POT, pot)

        assertNull(state.beginMenuVisit(pot, tick = 10))
        assertNotNull(state.snapshot().resourceAt(pot))
        assertTrue(state.observeBlockRemoved(pot))
        assertNull(state.snapshot().resourceAt(pot))
    }

    @Test
    fun `removing a menu container is not treated as a pot break`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.BARREL, barrel)

        assertFalse(state.observeBlockRemoved(barrel))
        assertNotNull(state.snapshot().resourceAt(barrel))
    }

    @Test
    fun `normal and ominous vaults are represented independently`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.VAULT, vault)
        state.observeResource(TrialResourceKind.OMINOUS_VAULT, ominousVault)

        val snapshot = state.snapshot()
        assertEquals(TrialVaultDisplayState.UNKNOWN, snapshot.resourceAt(vault)!!.vaultState)
        assertEquals(TrialVaultDisplayState.UNKNOWN, snapshot.resourceAt(ominousVault)!!.vaultState)
        assertEquals(1, snapshot.count(TrialResourceKind.VAULT))
        assertEquals(1, snapshot.count(TrialResourceKind.OMINOUS_VAULT))
    }

    @Test
    fun `vault availability follows synchronized local connected player evidence`() {
        val state = TrialResourceState()
        val localPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val otherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002")
        state.observeResource(TrialResourceKind.VAULT, vault)

        assertEquals(TrialVaultDisplayState.UNKNOWN, state.snapshot().resourceAt(vault)!!.vaultState)
        assertTrue(state.updateVaultConnectedPlayers(vault, localPlayer, setOf(otherPlayer, localPlayer)))
        assertEquals(TrialVaultDisplayState.AVAILABLE, state.snapshot().resourceAt(vault)!!.vaultState)
        assertTrue(state.updateVaultConnectedPlayers(vault, localPlayer, setOf(otherPlayer)))
        assertEquals(TrialVaultDisplayState.UNKNOWN, state.snapshot().resourceAt(vault)!!.vaultState)
    }

    @Test
    fun `stable loaded vault evidence reconstructs an already claimed vault`() {
        val state = TrialResourceState(vaultClaimConfirmationWindowTicks = 40)
        state.observeResource(TrialResourceKind.VAULT, vault)
        val observation = TrialVaultBlockObservation(
            phase = TrialVaultBlockPhase.INACTIVE,
            localPlayerConnected = false,
            localPlayerWithinRange = true,
        )

        assertTrue(state.reconcileVaultBlockObservation(vault, observation, tick = 10))
        assertEquals(TrialVaultDisplayState.UNKNOWN, state.snapshot().resourceAt(vault)!!.vaultState)
        state.reconcileVaultBlockObservation(vault, observation, tick = 49)
        assertEquals(TrialVaultDisplayState.UNKNOWN, state.snapshot().resourceAt(vault)!!.vaultState)
        state.reconcileVaultBlockObservation(vault, observation, tick = 50)

        assertEquals(TrialVaultDisplayState.CLAIMED, state.snapshot().resourceAt(vault)!!.vaultState)
    }

    @Test
    fun `connected vault evidence cancels a pending claimed inference`() {
        val state = TrialResourceState(vaultClaimConfirmationWindowTicks = 40)
        state.observeResource(TrialResourceKind.VAULT, vault)
        val absent = TrialVaultBlockObservation(
            phase = TrialVaultBlockPhase.ACTIVE,
            localPlayerConnected = false,
            localPlayerWithinRange = true,
        )
        val connected = absent.copy(localPlayerConnected = true)

        state.reconcileVaultBlockObservation(vault, absent, tick = 10)
        state.reconcileVaultBlockObservation(vault, connected, tick = 30)
        state.reconcileVaultBlockObservation(vault, absent, tick = 31)
        state.reconcileVaultBlockObservation(vault, absent, tick = 70)

        assertEquals(TrialVaultDisplayState.AVAILABLE, state.snapshot().resourceAt(vault)!!.vaultState)
        state.reconcileVaultBlockObservation(vault, absent, tick = 71)
        assertEquals(TrialVaultDisplayState.CLAIMED, state.snapshot().resourceAt(vault)!!.vaultState)
    }

    @Test
    fun `distance and another players unlock cannot claim the local vault`() {
        val state = TrialResourceState(vaultClaimConfirmationWindowTicks = 4)
        state.observeResource(TrialResourceKind.VAULT, vault)

        state.reconcileVaultBlockObservation(
            vault,
            TrialVaultBlockObservation(TrialVaultBlockPhase.INACTIVE, false, false),
            tick = 10,
        )
        state.reconcileVaultBlockObservation(
            vault,
            TrialVaultBlockObservation(TrialVaultBlockPhase.UNLOCKING, false, true),
            tick = 20,
        )
        state.reconcileVaultBlockObservation(
            vault,
            TrialVaultBlockObservation(TrialVaultBlockPhase.EJECTING, false, true),
            tick = 30,
        )

        assertEquals(TrialVaultDisplayState.UNKNOWN, state.snapshot().resourceAt(vault)!!.vaultState)
    }

    @Test
    fun `known vault availability survives pruning and reobservation`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.VAULT, vault)
        state.reconcileVaultBlockObservation(
            vault,
            TrialVaultBlockObservation(TrialVaultBlockPhase.ACTIVE, true, true),
            tick = 10,
        )

        state.retainObservedPositions(emptySet())
        state.observeResource(TrialResourceKind.VAULT, vault)

        assertEquals(TrialVaultDisplayState.AVAILABLE, state.snapshot().resourceAt(vault)!!.vaultState)
    }

    @Test
    fun `suspending observations retains knowledge and restarts claimed inference`() {
        val state = TrialResourceState(vaultClaimConfirmationWindowTicks = 40)
        state.observeResource(TrialResourceKind.VAULT, vault)
        val connected = TrialVaultBlockObservation(TrialVaultBlockPhase.ACTIVE, true, true)
        val absent = connected.copy(localPlayerConnected = false)
        state.reconcileVaultBlockObservation(vault, connected, tick = 1)
        state.reconcileVaultBlockObservation(vault, absent, tick = 10)

        state.suspendObservations()
        state.reconcileVaultBlockObservation(vault, absent, tick = 100)
        state.reconcileVaultBlockObservation(vault, absent, tick = 139)

        assertEquals(TrialVaultDisplayState.AVAILABLE, state.snapshot().resourceAt(vault)!!.vaultState)
        state.reconcileVaultBlockObservation(vault, absent, tick = 140)
        assertEquals(TrialVaultDisplayState.CLAIMED, state.snapshot().resourceAt(vault)!!.vaultState)
    }

    @Test
    fun `vault is claimed only after matching observed local unlock sequence`() {
        val state = TrialResourceState()
        val localPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001")
        state.observeResource(TrialResourceKind.VAULT, vault)
        state.observeResource(TrialResourceKind.OMINOUS_VAULT, ominousVault)
        state.updateVaultConnectedPlayers(vault, localPlayer, setOf(localPlayer))

        assertFalse(state.completeLocalVaultUnlock(vault))
        assertEquals(TrialVaultDisplayState.AVAILABLE, state.snapshot().resourceAt(vault)!!.vaultState)
        assertTrue(state.beginLocalVaultUnlock(vault))
        assertFalse(state.completeLocalVaultUnlock(ominousVault))
        assertTrue(state.completeLocalVaultUnlock(vault))
        state.updateVaultConnectedPlayers(vault, localPlayer, emptySet())

        assertEquals(TrialVaultDisplayState.CLAIMED, state.snapshot().resourceAt(vault)!!.vaultState)
        assertEquals(TrialVaultDisplayState.UNKNOWN, state.snapshot().resourceAt(ominousVault)!!.vaultState)
    }

    @Test
    fun `snapshot aggregates unvisited loot by type`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.TRIAL_SPAWNER, TrialResourcePosition(9, 20, 30))
        state.observeResource(TrialResourceKind.CHEST, chestLeft, connectedChestHalf = chestRight)
        state.observeResource(TrialResourceKind.BARREL, barrel)
        state.observeResource(TrialResourceKind.DECORATED_POT, pot)
        state.observeResource(TrialResourceKind.DISPENSER, dispenser)
        val visitedBarrel = requireNotNull(state.beginMenuVisit(barrel, tick = 10))
        state.confirmMenuVisit(visitedBarrel, tick = 11)

        val snapshot = state.snapshot()

        assertEquals(1, snapshot.unvisitedLootCount(TrialResourceKind.CHEST))
        assertEquals(0, snapshot.unvisitedLootCount(TrialResourceKind.BARREL))
        assertEquals(1, snapshot.unvisitedLootCount(TrialResourceKind.DECORATED_POT))
        assertEquals(1, snapshot.unvisitedLootCount(TrialResourceKind.DISPENSER))
        assertEquals(1, snapshot.count(TrialResourceKind.TRIAL_SPAWNER))
    }

    @Test
    fun `loaded resource pruning preserves confirmed session knowledge for reobservation`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.BARREL, barrel)
        state.observeResource(TrialResourceKind.DISPENSER, dispenser)
        state.observeResource(TrialResourceKind.VAULT, vault)
        val barrelVisit = requireNotNull(state.beginMenuVisit(barrel, tick = 10))
        state.confirmMenuVisit(barrelVisit, tick = 11)
        state.beginLocalVaultUnlock(vault)
        state.completeLocalVaultUnlock(vault)

        assertEquals(1, state.retainObservedPositions(setOf(barrel, vault)))
        assertTrue(state.snapshot().resourceAt(barrel)!!.visited)
        assertEquals(TrialVaultDisplayState.CLAIMED, state.snapshot().resourceAt(vault)!!.vaultState)
        assertNull(state.snapshot().resourceAt(dispenser))
        assertEquals(2, state.retainObservedPositions(emptySet()))

        state.observeResource(TrialResourceKind.BARREL, barrel)
        state.observeResource(TrialResourceKind.VAULT, vault)

        assertTrue(state.snapshot().resourceAt(barrel)!!.visited)
        assertEquals(TrialVaultDisplayState.CLAIMED, state.snapshot().resourceAt(vault)!!.vaultState)
    }

    @Test
    fun `pruning clears removed pending sequences but retains a double chest attempt with one loaded half`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.CHEST, chestLeft, connectedChestHalf = chestRight)
        val chestAttempt = requireNotNull(state.beginMenuVisit(chestRight, tick = 10))

        assertEquals(0, state.retainObservedPositions(setOf(chestLeft)))
        assertTrue(state.confirmMenuVisit(chestAttempt, tick = 11))

        state.observeResource(TrialResourceKind.BARREL, barrel)
        val barrelAttempt = requireNotNull(state.beginMenuVisit(barrel, tick = 12))
        state.observeResource(TrialResourceKind.VAULT, vault)
        state.beginLocalVaultUnlock(vault)

        assertEquals(2, state.retainObservedPositions(setOf(chestRight)))
        assertFalse(state.confirmMenuVisit(barrelAttempt, tick = 13))
        state.observeResource(TrialResourceKind.VAULT, vault)
        assertFalse(state.completeLocalVaultUnlock(vault))
    }

    @Test
    fun `session reset clears resources observations and pending confirmations`() {
        val state = TrialResourceState()
        state.observeResource(TrialResourceKind.BARREL, barrel)
        state.observeResource(TrialResourceKind.VAULT, vault)
        val attempt = requireNotNull(state.beginMenuVisit(barrel, tick = 10))
        state.beginLocalVaultUnlock(vault)

        state.resetSession()

        assertTrue(state.snapshot().resources.isEmpty())
        assertFalse(state.confirmMenuVisit(attempt, tick = 11))
        assertFalse(state.completeLocalVaultUnlock(vault))
    }
}
