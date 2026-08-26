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
package net.ccbluex.liquidbounce.integration.theme.component.components.trialchamber

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialChamberHudModelTest {

    @Test
    fun `HUD model exists only while tracker runs inside a chamber with an active spawner`() {
        val inactiveChamber = TrialChamberHudInput()
        val activeChamber = TrialChamberHudInput(spawnerPhases = listOf(TrialSpawnerHudPhase.ACTIVE))

        assertNull(buildTrialChamberHudModel(
            trackerRunning = false,
            playerInsideChamber = true,
            currentChamber = activeChamber,
        ))
        assertNull(buildTrialChamberHudModel(
            trackerRunning = true,
            playerInsideChamber = false,
            currentChamber = activeChamber,
        ))
        assertNull(buildTrialChamberHudModel(
            trackerRunning = true,
            playerInsideChamber = true,
            currentChamber = null,
        ))
        assertNotNull(buildTrialChamberHudModel(
            trackerRunning = true,
            playerInsideChamber = true,
            currentChamber = activeChamber,
        ))
        assertNull(buildTrialChamberHudModel(true, true, inactiveChamber))
    }

    @Test
    fun `spawner line counts every vanilla phase in stable phase order`() {
        val phases = TrialSpawnerHudPhase.entries
        val chamber = TrialChamberHudInput(spawnerPhases = phases + phases.reversed())

        val line = requireNotNull(buildTrialChamberHudModel(true, true, chamber))
            .line(TrialChamberHudSection.SPAWNERS)

        assertEquals(
            listOf(
                TrialChamberHudMetric.SPAWNER_INACTIVE,
                TrialChamberHudMetric.SPAWNER_WAITING_FOR_PLAYERS,
                TrialChamberHudMetric.SPAWNER_ACTIVE,
                TrialChamberHudMetric.SPAWNER_WAITING_FOR_REWARD_EJECTION,
                TrialChamberHudMetric.SPAWNER_EJECTING_REWARD,
                TrialChamberHudMetric.SPAWNER_COOLDOWN,
            ),
            requireNotNull(line).entries.map(TrialChamberHudEntry::metric),
        )
        assertTrue(line.entries.all { it.count == 2 })
    }

    @Test
    fun `trial mob line includes only living recognized mobs from the current chamber`() {
        val chamber = TrialChamberHudInput(
            spawnerPhases = listOf(TrialSpawnerHudPhase.ACTIVE),
            trialMobs = listOf(
                TrialChamberHudMob(isCurrentTrialMob = true, isAlive = true),
                TrialChamberHudMob(isCurrentTrialMob = true, isAlive = false),
                TrialChamberHudMob(isCurrentTrialMob = false, isAlive = true),
                TrialChamberHudMob(isCurrentTrialMob = false, isAlive = false),
            ),
        )

        val model = requireNotNull(buildTrialChamberHudModel(true, true, chamber))

        assertEquals(1, model.count(TrialChamberHudMetric.LIVING_TRIAL_MOBS))
    }

    @Test
    fun `vault line keeps available claimed and unknown independent`() {
        val statuses = TrialVaultHudStatus.entries
        val chamber = TrialChamberHudInput(
            spawnerPhases = listOf(TrialSpawnerHudPhase.ACTIVE),
            vaultStatuses = statuses + statuses,
        )

        val line = requireNotNull(buildTrialChamberHudModel(true, true, chamber))
            .line(TrialChamberHudSection.VAULTS)

        assertEquals(
            listOf(
                TrialChamberHudMetric.VAULT_AVAILABLE,
                TrialChamberHudMetric.VAULT_CLAIMED,
                TrialChamberHudMetric.VAULT_UNKNOWN,
            ),
            requireNotNull(line).entries.map(TrialChamberHudEntry::metric),
        )
        assertTrue(line.entries.all { it.count == 2 })
    }

    @Test
    fun `loot line counts unvisited resources by type and ignores visited resources`() {
        val chamber = TrialChamberHudInput(
            spawnerPhases = listOf(TrialSpawnerHudPhase.ACTIVE),
            loot = TrialLootHudType.entries.flatMap { type ->
                listOf(
                    TrialChamberHudLoot(type, isVisited = false),
                    TrialChamberHudLoot(type, isVisited = false),
                    TrialChamberHudLoot(type, isVisited = true),
                )
            },
        )

        val line = requireNotNull(buildTrialChamberHudModel(true, true, chamber))
            .line(TrialChamberHudSection.LOOT)

        assertEquals(
            listOf(
                TrialChamberHudMetric.LOOT_CHEST,
                TrialChamberHudMetric.LOOT_BARREL,
                TrialChamberHudMetric.LOOT_POT,
                TrialChamberHudMetric.LOOT_DISPENSER,
            ),
            requireNotNull(line).entries.map(TrialChamberHudEntry::metric),
        )
        assertTrue(line.entries.all { it.count == 2 })
    }

    @Test
    fun `active chamber omits empty secondary resource groups but reports zero living trial mobs`() {
        val model = requireNotNull(buildTrialChamberHudModel(
            true,
            true,
            TrialChamberHudInput(spawnerPhases = listOf(TrialSpawnerHudPhase.ACTIVE)),
        ))

        assertEquals(
            listOf(TrialChamberHudSection.SPAWNERS, TrialChamberHudSection.TRIAL_MOBS),
            model.lines.map(TrialChamberHudLine::section),
        )
        assertEquals(0, model.count(TrialChamberHudMetric.LIVING_TRIAL_MOBS))
        assertNull(model.line(TrialChamberHudSection.VAULTS))
        assertNull(model.line(TrialChamberHudSection.LOOT))
    }

    @Test
    fun `presentation order is deterministic regardless of snapshot collection order`() {
        val ascending = completeChamber()
        val descending = TrialChamberHudInput(
            spawnerPhases = ascending.spawnerPhases.reversed(),
            trialMobs = ascending.trialMobs.reversed(),
            vaultStatuses = ascending.vaultStatuses.reversed(),
            loot = ascending.loot.reversed(),
        )

        val first = requireNotNull(buildTrialChamberHudModel(true, true, ascending))
        val second = requireNotNull(buildTrialChamberHudModel(true, true, descending))

        assertEquals(first, second)
        assertEquals(
            listOf(
                TrialChamberHudSection.SPAWNERS,
                TrialChamberHudSection.TRIAL_MOBS,
                TrialChamberHudSection.VAULTS,
                TrialChamberHudSection.LOOT,
            ),
            first.lines.map(TrialChamberHudLine::section),
        )
    }

    @Test
    fun `HUD output exposes observed counts without remaining estimates or ETA`() {
        val exposedNames = buildList {
            addAll(TrialChamberHudModel::class.java.declaredFields.map { it.name })
            addAll(TrialChamberHudModel::class.java.declaredMethods.map { it.name })
            addAll(TrialChamberHudLine::class.java.declaredFields.map { it.name })
            addAll(TrialChamberHudEntry::class.java.declaredFields.map { it.name })
        }.map(String::lowercase)

        assertFalse(exposedNames.any { "estimate" in it })
        assertFalse(exposedNames.any { "remaining" in it })
        assertFalse(exposedNames.any { it == "eta" || it == "geteta" })
    }

    private fun completeChamber() = TrialChamberHudInput(
        spawnerPhases = TrialSpawnerHudPhase.entries,
        trialMobs = listOf(TrialChamberHudMob(isCurrentTrialMob = true, isAlive = true)),
        vaultStatuses = TrialVaultHudStatus.entries,
        loot = TrialLootHudType.entries.map { TrialChamberHudLoot(it, isVisited = false) },
    )
}
