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

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrialSpawnCorrelationTest {

    @Test
    fun `two spawner events followed by add entity correlate one Trial mob`() {
        val correlator = TrialSpawnCorrelator()

        assertTrue(correlator.observe(pulse(tick = 100)).isEmpty())
        assertTrue(correlator.observe(spawnEvent(tick = 101)).isEmpty())
        val association = correlator.observe(addedEntity(tick = 102)).single()

        assertEquals(ENTITY_UUID, association.entityUuid)
        assertEquals(BREEZE, association.entityType)
        assertEquals(SPAWNER, association.originSpawner)
        assertEquals(SPAWN_CELL, association.spawnCell)
        assertEquals(102L, association.correlatedAtTick)
        assertEquals(0, correlator.pendingEvidenceCount)
    }

    @Test
    fun `add entity before both spawner events correlates bidirectionally`() {
        val correlator = TrialSpawnCorrelator()

        assertTrue(correlator.observe(addedEntity(tick = 200)).isEmpty())
        assertTrue(correlator.observe(spawnEvent(tick = 202)).isEmpty())
        val association = correlator.observe(pulse(tick = 204)).single()

        assertEquals(ENTITY_UUID, association.entityUuid)
        assertEquals(SPAWNER, association.originSpawner)
        assertEquals(204L, association.correlatedAtTick)
    }

    @Test
    fun `standalone spawn mob event is ignored after its evidence window`() {
        val correlator = TrialSpawnCorrelator()

        assertTrue(correlator.observe(spawnEvent(tick = 0)).isEmpty())
        assertEquals(1, correlator.pendingEvidenceCount)
        assertEquals(1, correlator.expire(currentTick = 5))
        assertEquals(0, correlator.pendingEvidenceCount)

        assertTrue(correlator.observe(pulse(tick = 5)).isEmpty())
        assertTrue(correlator.observe(addedEntity(tick = 5)).isEmpty())
    }

    @Test
    fun `origin plus add entity cannot bypass the required spawn mob event`() {
        val correlator = TrialSpawnCorrelator()

        assertTrue(correlator.observe(pulse(tick = 10)).isEmpty())
        assertTrue(correlator.observe(addedEntity(tick = 12)).isEmpty())

        assertEquals(2, correlator.pendingEvidenceCount)
    }

    @Test
    fun `four tick correlation boundary is inclusive and five ticks is expired`() {
        val inclusive = TrialSpawnCorrelator()
        inclusive.observe(pulse(tick = 10))
        inclusive.observe(spawnEvent(tick = 10))

        assertEquals(ENTITY_UUID, inclusive.observe(addedEntity(tick = 14)).single().entityUuid)

        val expired = TrialSpawnCorrelator()
        expired.observe(pulse(tick = 20))
        expired.observe(spawnEvent(tick = 20))

        assertTrue(expired.observe(addedEntity(tick = 25)).isEmpty())
    }

    @Test
    fun `event data and spawn cell prevent evidence from unrelated spawns`() {
        val correlator = TrialSpawnCorrelator()
        correlator.observe(pulse(tick = 30, eventData = 1))
        correlator.observe(spawnEvent(tick = 30, eventData = 2))

        assertTrue(correlator.observe(addedEntity(tick = 30)).isEmpty())

        val matchingDataWrongCell = TrialSpawnCell(99L)
        correlator.observe(spawnEvent(tick = 31, eventData = 1, spawnCell = matchingDataWrongCell))
        assertEquals(4, correlator.pendingEvidenceCount)
    }

    @Test
    fun `entity removal deletes a current Trial mob membership`() {
        val association = association()
        val membership = TrialMobMembership(maximumDistanceBlocks = 192.0)
        membership.add(association)

        assertTrue(membership.isCurrentTrialMob(ENTITY_UUID))
        assertTrue(membership.onEntityRemoved(ENTITY_UUID))
        assertFalse(membership.isCurrentTrialMob(ENTITY_UUID))
    }

    @Test
    fun `session change clears every Trial mob membership`() {
        val membership = TrialMobMembership(maximumDistanceBlocks = 192.0)
        membership.add(association())

        membership.onSessionChanged()

        assertTrue(membership.snapshot().isEmpty())
        assertFalse(membership.isCurrentTrialMob(ENTITY_UUID))
    }

    @Test
    fun `distance boundary remains tracked and overflow removes membership`() {
        val membership = TrialMobMembership(maximumDistanceBlocks = 192.0)
        membership.add(association())

        assertFalse(membership.onDistanceObserved(ENTITY_UUID, distanceBlocks = 192.0))
        assertTrue(membership.isCurrentTrialMob(ENTITY_UUID))
        assertTrue(membership.onDistanceObserved(ENTITY_UUID, distanceBlocks = 192.01))
        assertFalse(membership.isCurrentTrialMob(ENTITY_UUID))
    }

    @Test
    fun `mob is removed when its origin spawner cluster is no longer current`() {
        val membership = TrialMobMembership(maximumDistanceBlocks = 192.0)
        membership.add(association())

        assertEquals(1, membership.retainCurrentOrigins(setOf(TrialSpawnerOrigin(8L))))
        assertFalse(membership.isCurrentTrialMob(ENTITY_UUID))
    }

    @Test
    fun `active spawner with exact display type matches at vanilla tracking boundary`() {
        val candidate = TrialFallbackCandidate(
            activeSpawner = true,
            displayMobType = BREEZE,
            candidateMobType = BREEZE,
            distanceFromSpawnerBlocks = 47.0,
        )

        assertTrue(isConservativeTrialMobFallback(candidate))
    }

    @Test
    fun `fallback rejects inactive spawner wrong type and distance beyond vanilla tracking`() {
        val matching = TrialFallbackCandidate(
            activeSpawner = true,
            displayMobType = BREEZE,
            candidateMobType = BREEZE,
            distanceFromSpawnerBlocks = 47.0,
        )

        assertFalse(isConservativeTrialMobFallback(matching.copy(activeSpawner = false)))
        assertFalse(isConservativeTrialMobFallback(matching.copy(candidateMobType = ZOMBIE)))
        assertFalse(isConservativeTrialMobFallback(matching.copy(displayMobType = null)))
        assertFalse(isConservativeTrialMobFallback(matching.copy(distanceFromSpawnerBlocks = 47.01)))
    }

    private fun association() = TrialMobAssociation(
        entityUuid = ENTITY_UUID,
        entityType = BREEZE,
        originSpawner = SPAWNER,
        spawnCell = SPAWN_CELL,
        correlatedAtTick = 100,
    )

    private fun pulse(
        tick: Long,
        eventData: Int = EVENT_DATA,
    ) = TrialSpawnerPulse(
        originSpawner = SPAWNER,
        eventData = eventData,
        tick = tick,
    )

    private fun spawnEvent(
        tick: Long,
        eventData: Int = EVENT_DATA,
        spawnCell: TrialSpawnCell = SPAWN_CELL,
    ) = TrialSpawnerMobSpawnEvent(
        spawnCell = spawnCell,
        eventData = eventData,
        tick = tick,
    )

    private fun addedEntity(tick: Long) = TrialAddedEntity(
        entityUuid = ENTITY_UUID,
        entityType = BREEZE,
        spawnCell = SPAWN_CELL,
        tick = tick,
    )

    private companion object {
        const val EVENT_DATA = 1
        val ENTITY_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000123")
        val SPAWNER = TrialSpawnerOrigin(42L)
        val SPAWN_CELL = TrialSpawnCell(43L)
        val BREEZE = TrialEntityTypeKey("minecraft:breeze")
        val ZOMBIE = TrialEntityTypeKey("minecraft:zombie")
    }
}
