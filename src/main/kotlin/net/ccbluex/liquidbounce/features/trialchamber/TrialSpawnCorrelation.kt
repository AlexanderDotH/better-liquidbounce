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

package net.ccbluex.liquidbounce.features.trialchamber

import java.util.UUID
import kotlin.math.abs

/** Packed block position of the Trial Spawner which emitted the origin pulse. */
@JvmInline
internal value class TrialSpawnerOrigin(val packedBlockPosition: Long)

/** Packed block position used to join a spawn-at level event to an added entity. */
@JvmInline
internal value class TrialSpawnCell(val packedBlockPosition: Long)

/** Stable registry key for an entity type, for example `minecraft:breeze`. */
@JvmInline
internal value class TrialEntityTypeKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Trial entity type key must not be blank" }
    }
}

/** The Trial Spawner level event emitted at the source spawner. */
internal data class TrialSpawnerPulse(
    val originSpawner: TrialSpawnerOrigin,
    val eventData: Int,
    val tick: Long,
) {
    init {
        require(tick >= 0) { "Trial evidence tick must not be negative" }
    }
}

/** The Trial Spawner level event emitted at the spawned mob's block position. */
internal data class TrialSpawnerMobSpawnEvent(
    val spawnCell: TrialSpawnCell,
    val eventData: Int,
    val tick: Long,
) {
    init {
        require(tick >= 0) { "Trial evidence tick must not be negative" }
    }
}

/** Packet-independent view of one ClientboundAddEntity observation. */
internal data class TrialAddedEntity(
    val entityUuid: UUID,
    val entityType: TrialEntityTypeKey,
    val spawnCell: TrialSpawnCell,
    val tick: Long,
) {
    init {
        require(tick >= 0) { "Trial evidence tick must not be negative" }
    }
}

/** Confirmed association between a client entity and its origin Trial Spawner. */
internal data class TrialMobAssociation(
    val entityUuid: UUID,
    val entityType: TrialEntityTypeKey,
    val originSpawner: TrialSpawnerOrigin,
    val spawnCell: TrialSpawnCell,
    val correlatedAtTick: Long,
)

/**
 * Correlates the two Trial Spawner level events with an added entity.
 *
 * Evidence may arrive in any order. A match consumes one item of each evidence kind, requires
 * matching event data and spawn cells, and spans at most [windowTicks] inclusively. Consequently,
 * a standalone spawn-at event can never classify a mob.
 */
internal class TrialSpawnCorrelator(
    private val windowTicks: Long = TRIAL_SPAWN_CORRELATION_WINDOW_TICKS,
) {

    private val pending = PendingTrialSpawnEvidence()

    init {
        require(windowTicks >= 0) { "Trial spawn correlation window must not be negative" }
    }

    val pendingEvidenceCount: Int
        get() = pending.size

    fun observe(pulse: TrialSpawnerPulse): List<TrialMobAssociation> = accept(pulse.tick) {
        pending.pulses += pulse
    }

    fun observe(event: TrialSpawnerMobSpawnEvent): List<TrialMobAssociation> = accept(event.tick) {
        pending.spawnEvents += event
    }

    fun observe(entity: TrialAddedEntity): List<TrialMobAssociation> = accept(entity.tick) {
        pending.addedEntities += entity
    }

    /** Removes evidence only after the inclusive correlation window has elapsed. */
    fun expire(currentTick: Long): Int {
        require(currentTick >= 0) { "Current tick must not be negative" }
        pending.currentTick = maxOf(pending.currentTick, currentTick)
        return pending.expire(windowTicks)
    }

    /** Clears packet evidence on a world or session change. */
    fun clear() {
        pending.clear()
    }

    private inline fun accept(tick: Long, addEvidence: () -> Unit): List<TrialMobAssociation> {
        addEvidence()
        expire(tick)
        return correlateCompleteEvidence()
    }

    private fun correlateCompleteEvidence(): List<TrialMobAssociation> = buildList {
        while (true) {
            val match = findBestMatch() ?: break
            pending.consume(match)
            add(match.toAssociation())
        }
    }

    private fun findBestMatch(): CompleteTrialSpawnEvidence? = pending.addedEntities.asSequence()
        .flatMap { entity ->
            pending.spawnEvents.asSequence()
                .filter { it.spawnCell == entity.spawnCell }
                .flatMap { spawnEvent ->
                    pending.pulses.asSequence()
                        .filter { it.eventData == spawnEvent.eventData }
                        .map { pulse -> CompleteTrialSpawnEvidence(pulse, spawnEvent, entity) }
                }
        }
        .filter { it.tickSpan <= windowTicks }
        .minWithOrNull(COMPLETE_EVIDENCE_ORDER)
}

private class PendingTrialSpawnEvidence {
    val pulses = mutableListOf<TrialSpawnerPulse>()
    val spawnEvents = mutableListOf<TrialSpawnerMobSpawnEvent>()
    val addedEntities = mutableListOf<TrialAddedEntity>()
    var currentTick: Long = 0

    val size: Int
        get() = pulses.size + spawnEvents.size + addedEntities.size

    fun expire(windowTicks: Long): Int {
        val sizeBefore = size
        pulses.removeAll { hasElapsed(it.tick, windowTicks) }
        spawnEvents.removeAll { hasElapsed(it.tick, windowTicks) }
        addedEntities.removeAll { hasElapsed(it.tick, windowTicks) }
        return sizeBefore - size
    }

    fun consume(match: CompleteTrialSpawnEvidence) {
        pulses.remove(match.pulse)
        spawnEvents.remove(match.spawnEvent)
        addedEntities.remove(match.entity)
    }

    fun clear() {
        pulses.clear()
        spawnEvents.clear()
        addedEntities.clear()
        currentTick = 0
    }

    private fun hasElapsed(evidenceTick: Long, windowTicks: Long): Boolean =
        currentTick > evidenceTick && currentTick - evidenceTick > windowTicks
}

private data class CompleteTrialSpawnEvidence(
    val pulse: TrialSpawnerPulse,
    val spawnEvent: TrialSpawnerMobSpawnEvent,
    val entity: TrialAddedEntity,
) {
    private val earliestTick = minOf(pulse.tick, spawnEvent.tick, entity.tick)
    private val latestTick = maxOf(pulse.tick, spawnEvent.tick, entity.tick)

    val tickSpan: Long
        get() = latestTick - earliestTick

    fun toAssociation() = TrialMobAssociation(
        entityUuid = entity.entityUuid,
        entityType = entity.entityType,
        originSpawner = pulse.originSpawner,
        spawnCell = entity.spawnCell,
        correlatedAtTick = latestTick,
    )

    companion object {
        val ORDER: Comparator<CompleteTrialSpawnEvidence> = compareBy<CompleteTrialSpawnEvidence>(
            { it.tickSpan },
            { abs(it.spawnEvent.tick - it.entity.tick) },
            { abs(it.pulse.tick - it.spawnEvent.tick) },
            { it.latestTick },
            { it.entity.entityUuid.toString() },
            { it.pulse.originSpawner.packedBlockPosition },
        )
    }
}

private val COMPLETE_EVIDENCE_ORDER = CompleteTrialSpawnEvidence.ORDER

/** Current-session membership queried by Combat and Visual targeting. */
internal class TrialMobMembership(
    private val maximumDistanceBlocks: Double,
) {

    private val associationsByUuid = linkedMapOf<UUID, TrialMobAssociation>()

    init {
        require(maximumDistanceBlocks.isFinite() && maximumDistanceBlocks >= 0.0) {
            "Maximum Trial mob distance must be finite and non-negative"
        }
    }

    fun add(association: TrialMobAssociation) {
        associationsByUuid[association.entityUuid] = association
    }

    fun isCurrentTrialMob(entityUuid: UUID): Boolean = entityUuid in associationsByUuid

    fun snapshot(): List<TrialMobAssociation> = associationsByUuid.values.toList()

    fun onEntityRemoved(entityUuid: UUID): Boolean = associationsByUuid.remove(entityUuid) != null

    /** Returns true when the observation crossed the allowed chamber distance and removed the mob. */
    fun onDistanceObserved(entityUuid: UUID, distanceBlocks: Double): Boolean {
        require(distanceBlocks.isFinite() && distanceBlocks >= 0.0) {
            "Trial mob distance must be finite and non-negative"
        }
        if (distanceBlocks <= maximumDistanceBlocks) {
            return false
        }

        return onEntityRemoved(entityUuid)
    }

    /** Removes members whose origin anchor is no longer part of the selected chamber cluster. */
    fun retainCurrentOrigins(currentOrigins: Set<TrialSpawnerOrigin>): Int {
        val sizeBefore = associationsByUuid.size
        associationsByUuid.entries.removeIf { it.value.originSpawner !in currentOrigins }
        return sizeBefore - associationsByUuid.size
    }

    fun onSessionChanged() {
        associationsByUuid.clear()
    }
}

/** Minimal fallback evidence for reconstructing an already-running Trial Spawner wave. */
internal data class TrialFallbackCandidate(
    val activeSpawner: Boolean,
    val displayMobType: TrialEntityTypeKey?,
    val candidateMobType: TrialEntityTypeKey,
    val distanceFromSpawnerBlocks: Double,
) {
    init {
        require(distanceFromSpawnerBlocks.isFinite() && distanceFromSpawnerBlocks >= 0.0) {
            "Trial fallback distance must be finite and non-negative"
        }
    }
}

internal fun isConservativeTrialMobFallback(candidate: TrialFallbackCandidate): Boolean =
    candidate.activeSpawner &&
        candidate.displayMobType == candidate.candidateMobType &&
        candidate.distanceFromSpawnerBlocks <= VANILLA_TRIAL_MOB_TRACKING_DISTANCE_BLOCKS

internal const val TRIAL_SPAWN_CORRELATION_WINDOW_TICKS = 4L
internal const val VANILLA_TRIAL_MOB_TRACKING_DISTANCE_BLOCKS = 47.0
