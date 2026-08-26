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

data class TrialResourcePosition(
    val x: Int,
    val y: Int,
    val z: Int,
) : Comparable<TrialResourcePosition> {

    override fun compareTo(other: TrialResourcePosition): Int =
        compareValuesBy(this, other, TrialResourcePosition::x, TrialResourcePosition::y, TrialResourcePosition::z)
}

enum class TrialResourceKind(
    val isLoot: Boolean = false,
    val requiresMenuConfirmation: Boolean = false,
    val isVault: Boolean = false,
) {
    TRIAL_SPAWNER,
    VAULT(isVault = true),
    OMINOUS_VAULT(isVault = true),
    CHEST(isLoot = true, requiresMenuConfirmation = true),
    BARREL(isLoot = true, requiresMenuConfirmation = true),
    DECORATED_POT(isLoot = true),
    DISPENSER(isLoot = true, requiresMenuConfirmation = true),
}

enum class TrialVaultDisplayState {
    AVAILABLE,
    CLAIMED,
    UNKNOWN,
}

data class TrialResourceId internal constructor(
    val kind: TrialResourceKind,
    val positions: List<TrialResourcePosition>,
) {

    val canonicalPosition: TrialResourcePosition
        get() = positions.first()

    init {
        require(positions.isNotEmpty()) { "A trial resource must have at least one position" }
        require(positions == positions.distinct().sorted()) { "Trial resource positions must be unique and sorted" }
        require(positions.size == 1 || kind == TrialResourceKind.CHEST) {
            "Only a chest can span multiple block positions"
        }
        require(positions.size <= MAX_RESOURCE_POSITIONS) { "A trial resource can span at most two positions" }
    }

    companion object {
        private const val MAX_RESOURCE_POSITIONS = 2

        internal fun single(kind: TrialResourceKind, position: TrialResourcePosition) =
            TrialResourceId(kind, listOf(position))

        internal fun chest(first: TrialResourcePosition, second: TrialResourcePosition?) =
            TrialResourceId(TrialResourceKind.CHEST, listOfNotNull(first, second).distinct().sorted())
    }
}

data class TrialMenuVisitAttempt internal constructor(
    val resourceId: TrialResourceId,
    val startedAtTick: Long,
    internal val sequence: Long,
    internal val sessionGeneration: Long,
)

data class TrialResourceSnapshot internal constructor(
    val id: TrialResourceId,
    val kind: TrialResourceKind,
    val positions: List<TrialResourcePosition>,
    val visited: Boolean,
    val vaultState: TrialVaultDisplayState?,
)

data class TrialResourceSessionSnapshot internal constructor(
    val resources: List<TrialResourceSnapshot>,
    val unvisitedLootCounts: Map<TrialResourceKind, Int>,
    val vaultStateCounts: Map<TrialVaultDisplayState, Int>,
) {

    fun resourceAt(position: TrialResourcePosition): TrialResourceSnapshot? =
        resources.firstOrNull { position in it.positions }

    fun count(kind: TrialResourceKind): Int = resources.count { it.kind == kind }

    fun unvisitedLootCount(kind: TrialResourceKind): Int = unvisitedLootCounts[kind] ?: 0

    fun vaultCount(state: TrialVaultDisplayState): Int = vaultStateCounts[state] ?: 0
}

/**
 * Session-scoped reducer for resources observed in the selected trial chamber.
 *
 * Discovery and interaction packets may arrive from different adapters. This reducer deliberately records an
 * interaction as visited only when the adapter confirms the matching menu-open attempt. All exposed snapshots own
 * immutable copies of their collections so render and HUD consumers never observe a partially updated reducer.
 */
// The event-shaped API keeps every session invariant behind one synchronization boundary.
@Suppress("TooManyFunctions")
class TrialResourceState(
    private val menuOpenConfirmationWindowTicks: Long = DEFAULT_MENU_CONFIRMATION_WINDOW_TICKS,
    private val vaultClaimConfirmationWindowTicks: Long = DEFAULT_VAULT_CLAIM_CONFIRMATION_WINDOW_TICKS,
) {

    private val resources = linkedMapOf<TrialResourceId, MutableTrialResource>()
    private val resourceAtPosition = hashMapOf<TrialResourcePosition, TrialResourceId>()
    private val visitedResourcePositions = hashSetOf<ResourceMemoryKey>()
    private val availableVaultPositions = hashSetOf<ResourceMemoryKey>()
    private val claimedVaultPositions = hashSetOf<ResourceMemoryKey>()

    private var pendingMenuVisit: TrialMenuVisitAttempt? = null
    private var pendingLocalVaultUnlock: TrialResourceId? = null
    private var nextMenuVisitSequence = 0L
    private var sessionGeneration = 0L

    init {
        require(menuOpenConfirmationWindowTicks >= 0) { "Menu confirmation window must not be negative" }
        require(vaultClaimConfirmationWindowTicks >= 0) {
            "Vault claim confirmation window must not be negative"
        }
    }

    @Synchronized
    fun observeResource(
        kind: TrialResourceKind,
        position: TrialResourcePosition,
        connectedChestHalf: TrialResourcePosition? = null,
    ): TrialResourceId {
        require(connectedChestHalf == null || kind == TrialResourceKind.CHEST) {
            "Only chest observations may include a connected half"
        }

        val observedId = if (kind == TrialResourceKind.CHEST) {
            TrialResourceId.chest(position, connectedChestHalf)
        } else {
            TrialResourceId.single(kind, position)
        }
        val existingId = resourceAtPosition[position]
        if (existingId != null && existingId.kind == kind && observedId.positions.all(existingId.positions::contains)) {
            return existingId
        }

        return mergeObservation(observedId)
    }

    @Synchronized
    fun beginMenuVisit(position: TrialResourcePosition, tick: Long): TrialMenuVisitAttempt? {
        val resourceId = resourceAtPosition[position] ?: return null
        if (!resourceId.kind.requiresMenuConfirmation) return null

        return TrialMenuVisitAttempt(
            resourceId = resourceId,
            startedAtTick = tick,
            sequence = nextMenuVisitSequence++,
            sessionGeneration = sessionGeneration,
        ).also { pendingMenuVisit = it }
    }

    @Synchronized
    fun confirmMenuVisit(attempt: TrialMenuVisitAttempt, tick: Long): Boolean {
        val pending = pendingMenuVisit ?: return false
        if (!attempt.matches(pending)) return false
        if (!tick.isWithinWindowAfter(pending.startedAtTick, menuOpenConfirmationWindowTicks)) {
            pendingMenuVisit = null
            return false
        }

        val resource = resources[pending.resourceId] ?: return false.also { pendingMenuVisit = null }
        resource.visited = true
        remember(resource.id, visitedResourcePositions)
        pendingMenuVisit = null
        return true
    }

    /** Removes resources absent from the latest complete loaded-position scan, retaining session knowledge. */
    @Synchronized
    fun retainObservedPositions(observedPositions: Set<TrialResourcePosition>): Int {
        val removedIds = resources.keys.filter { resourceId ->
            resourceId.positions.none(observedPositions::contains)
        }
        removedIds.forEach(::removePrunedResource)
        return removedIds.size
    }

    @Synchronized
    fun observeBlockRemoved(position: TrialResourcePosition): Boolean {
        val resourceId = resourceAtPosition[position] ?: return false
        if (resourceId.kind != TrialResourceKind.DECORATED_POT) return false

        removeResource(resourceId)
        return true
    }

    @Synchronized
    fun updateVaultConnectedPlayers(
        position: TrialResourcePosition,
        localPlayer: UUID?,
        connectedPlayers: Set<UUID>,
    ): Boolean {
        val resource = vaultAt(position) ?: return false
        val connected = localPlayer != null && localPlayer in connectedPlayers
        resource.available = connected
        resource.claimCandidateStartedAtTick = null
        if (connected) remember(resource.id, availableVaultPositions)
        return true
    }

    /**
     * Reconciles synchronized Vault eligibility with its live block phase.
     *
     * A missing UUID only proves a prior claim while the player is close enough to have been detected. The stable
     * block phases are sampled for a full server update interval window so packet ordering and another player's
     * unlock animation cannot create a false local claim.
    */
    @Synchronized
    internal fun reconcileVaultBlockObservation(
        position: TrialResourcePosition,
        observation: TrialVaultBlockObservation,
        tick: Long,
    ): Boolean {
        val resource = vaultAt(position) ?: return false
        if (resource.claimed) return true

        if (observation.localPlayerConnected) {
            resource.available = true
            resource.claimCandidateStartedAtTick = null
            remember(resource.id, availableVaultPositions)
            return true
        }

        if (!observation.localPlayerWithinRange || !observation.phase.permitsClaimInference) {
            resource.claimCandidateStartedAtTick = null
            return true
        }

        val startedAt = resource.claimCandidateStartedAtTick
        if (startedAt == null || tick < startedAt) {
            resource.claimCandidateStartedAtTick = tick
            return true
        }
        if (tick - startedAt < vaultClaimConfirmationWindowTicks) return true

        resource.claimed = true
        resource.available = false
        resource.claimCandidateStartedAtTick = null
        remember(resource.id, claimedVaultPositions)
        return true
    }

    @Synchronized
    fun beginLocalVaultUnlock(position: TrialResourcePosition): Boolean {
        val resource = vaultAt(position) ?: return false
        if (resource.claimed) return false

        pendingLocalVaultUnlock = resource.id
        return true
    }

    @Synchronized
    fun completeLocalVaultUnlock(position: TrialResourcePosition): Boolean {
        val resource = vaultAt(position) ?: return false
        if (pendingLocalVaultUnlock != resource.id) return false

        resource.claimed = true
        resource.available = false
        resource.claimCandidateStartedAtTick = null
        remember(resource.id, claimedVaultPositions)
        pendingLocalVaultUnlock = null
        return true
    }

    @Synchronized
    fun snapshot(): TrialResourceSessionSnapshot {
        val snapshots = resources.values
            .sortedWith(compareBy({ it.id.canonicalPosition }, { it.id.kind.ordinal }))
            .map(MutableTrialResource::snapshot)
        val unvisitedLoot = TrialResourceKind.entries
            .filter(TrialResourceKind::isLoot)
            .associateWith { kind -> snapshots.count { it.kind == kind && !it.visited } }
        val vaultStates = TrialVaultDisplayState.entries
            .associateWith { state -> snapshots.count { it.vaultState == state } }

        return TrialResourceSessionSnapshot(
            resources = snapshots.toList(),
            unvisitedLootCounts = unvisitedLoot.toMap(),
            vaultStateCounts = vaultStates.toMap(),
        )
    }

    @Synchronized
    fun resetSession() {
        resources.clear()
        resourceAtPosition.clear()
        visitedResourcePositions.clear()
        availableVaultPositions.clear()
        claimedVaultPositions.clear()
        pendingMenuVisit = null
        pendingLocalVaultUnlock = null
        sessionGeneration++
    }

    /** Drops packet-order-sensitive attempts without erasing confirmed session knowledge. */
    @Synchronized
    fun suspendObservations() {
        pendingMenuVisit = null
        pendingLocalVaultUnlock = null
        resources.values.forEach { it.claimCandidateStartedAtTick = null }
    }

    private fun mergeObservation(observedId: TrialResourceId): TrialResourceId {
        val overlappingIds = observedId.positions.mapNotNull(resourceAtPosition::get).distinct()
        val sameKindResources = overlappingIds
            .filter { it.kind == observedId.kind }
            .mapNotNull(resources::get)
        val mergedId = TrialResourceId(observedId.kind, observedId.positions)
        val mergedResource = MutableTrialResource(
            id = mergedId,
            visited = sameKindResources.any(MutableTrialResource::visited) ||
                isRemembered(mergedId, visitedResourcePositions),
            available = sameKindResources.any(MutableTrialResource::available),
            claimed = sameKindResources.any(MutableTrialResource::claimed) ||
                isRemembered(mergedId, claimedVaultPositions),
            claimCandidateStartedAtTick = sameKindResources.mapNotNull {
                it.claimCandidateStartedAtTick
            }.minOrNull(),
        )
        if (isRemembered(mergedId, availableVaultPositions)) {
            mergedResource.available = true
        }

        overlappingIds.forEach(::removeResource)
        resources[mergedId] = mergedResource
        mergedId.positions.forEach { resourceAtPosition[it] = mergedId }
        remapPendingObservations(overlappingIds, mergedId)
        return mergedId
    }

    private fun remapPendingObservations(oldIds: List<TrialResourceId>, mergedId: TrialResourceId) {
        val pendingMenu = pendingMenuVisit
        if (pendingMenu != null && pendingMenu.resourceId in oldIds) {
            pendingMenuVisit = if (pendingMenu.resourceId.kind == mergedId.kind) {
                pendingMenu.copy(resourceId = mergedId)
            } else {
                null
            }
        }

        val pendingVault = pendingLocalVaultUnlock ?: return
        if (pendingVault !in oldIds) return
        pendingLocalVaultUnlock = mergedId.takeIf { it.kind == pendingVault.kind }
    }

    private fun removeResource(resourceId: TrialResourceId) {
        resources.remove(resourceId)
        resourceId.positions.forEach { position -> resourceAtPosition.remove(position, resourceId) }
    }

    private fun removePrunedResource(resourceId: TrialResourceId) {
        removeResource(resourceId)
        if (pendingMenuVisit?.resourceId == resourceId) pendingMenuVisit = null
        if (pendingLocalVaultUnlock == resourceId) pendingLocalVaultUnlock = null
    }

    private fun remember(resourceId: TrialResourceId, memories: MutableSet<ResourceMemoryKey>) {
        resourceId.positions.mapTo(memories) { position -> ResourceMemoryKey(resourceId.kind, position) }
    }

    private fun isRemembered(resourceId: TrialResourceId, memories: Set<ResourceMemoryKey>): Boolean =
        resourceId.positions.any { position -> ResourceMemoryKey(resourceId.kind, position) in memories }

    private fun vaultAt(position: TrialResourcePosition): MutableTrialResource? {
        val resourceId = resourceAtPosition[position] ?: return null
        if (!resourceId.kind.isVault) return null
        return resources[resourceId]
    }

    private fun TrialMenuVisitAttempt.matches(other: TrialMenuVisitAttempt): Boolean =
        sequence == other.sequence && sessionGeneration == other.sessionGeneration

    private fun Long.isWithinWindowAfter(start: Long, window: Long): Boolean =
        this >= start && this - start <= window

    private data class MutableTrialResource(
        val id: TrialResourceId,
        var visited: Boolean = false,
        var available: Boolean = false,
        var claimed: Boolean = false,
        var claimCandidateStartedAtTick: Long? = null,
    ) {

        fun snapshot() = TrialResourceSnapshot(
            id = id.copy(positions = id.positions.toList()),
            kind = id.kind,
            positions = id.positions.toList(),
            visited = visited,
            vaultState = vaultState(),
        )

        private fun vaultState(): TrialVaultDisplayState? {
            if (!id.kind.isVault) return null
            if (claimed) return TrialVaultDisplayState.CLAIMED
            if (available) return TrialVaultDisplayState.AVAILABLE
            return TrialVaultDisplayState.UNKNOWN
        }
    }

    private data class ResourceMemoryKey(
        val kind: TrialResourceKind,
        val position: TrialResourcePosition,
    )

    companion object {
        const val DEFAULT_MENU_CONFIRMATION_WINDOW_TICKS = 40L
        const val DEFAULT_VAULT_CLAIM_CONFIRMATION_WINDOW_TICKS = 40L
    }
}
