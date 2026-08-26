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
import org.junit.jupiter.api.Test

class TrialChamberHudPresentationTest {

    @Test
    fun `presentation replaces internal abbreviations with readable grouped labels`() {
        val model = TrialChamberHudModel(listOf(
            line(
                TrialChamberHudSection.SPAWNERS,
                TrialChamberHudMetric.SPAWNER_WAITING_FOR_PLAYERS to 17,
                TrialChamberHudMetric.SPAWNER_ACTIVE to 2,
            ),
            line(
                TrialChamberHudSection.TRIAL_MOBS,
                TrialChamberHudMetric.LIVING_TRIAL_MOBS to 3,
            ),
            line(
                TrialChamberHudSection.VAULTS,
                TrialChamberHudMetric.VAULT_AVAILABLE to 1,
                TrialChamberHudMetric.VAULT_UNKNOWN to 18,
            ),
            line(
                TrialChamberHudSection.LOOT,
                TrialChamberHudMetric.LOOT_CHEST to 3,
                TrialChamberHudMetric.LOOT_BARREL to 28,
                TrialChamberHudMetric.LOOT_POT to 42,
                TrialChamberHudMetric.LOOT_DISPENSER to 41,
            ),
        ))

        val presentation = buildTrialChamberHudPresentation(model)

        assertEquals(
            listOf(
                TrialChamberHudStat("Waiting", 17, TrialChamberHudTone.ACCENT),
                TrialChamberHudStat("Active", 2, TrialChamberHudTone.WARNING),
            ),
            presentation.spawners,
        )
        assertEquals(3, presentation.livingMobs)
        assertEquals(
            listOf(
                TrialChamberHudStat("Ready", 1, TrialChamberHudTone.POSITIVE),
                TrialChamberHudStat("Unknown", 18, TrialChamberHudTone.MUTED),
            ),
            presentation.vaults,
        )
        assertEquals(
            listOf(
                TrialChamberHudStat("Chest", 3),
                TrialChamberHudStat("Barrel", 28),
                TrialChamberHudStat("Pot", 42),
                TrialChamberHudStat("Dispenser", 41),
            ),
            presentation.loot,
        )
        assertFalse(presentation.allLabels.any { it.length == 1 || ':' in it })
    }

    @Test
    fun `presentation has no global phase status duplicating the spawner group`() {
        val model = TrialChamberHudModel(listOf(
            line(
                TrialChamberHudSection.SPAWNERS,
                TrialChamberHudMetric.SPAWNER_WAITING_FOR_PLAYERS to 10,
            ),
        ))
        val presentation = buildTrialChamberHudPresentation(model)

        assertFalse(TrialChamberHudPresentation::class.java.declaredFields.any { it.name == "status" })
        assertEquals(listOf("Waiting"), presentation.allLabels)
    }

    @Test
    fun `empty observations produce calm zero state without invented estimates`() {
        val presentation = buildTrialChamberHudPresentation(TrialChamberHudModel(emptyList()))

        assertEquals(0, presentation.livingMobs)
        assertEquals(emptyList<TrialChamberHudStat>(), presentation.spawners)
        assertEquals(emptyList<TrialChamberHudStat>(), presentation.vaults)
        assertEquals(emptyList<TrialChamberHudStat>(), presentation.loot)
    }

    private fun line(
        section: TrialChamberHudSection,
        vararg entries: Pair<TrialChamberHudMetric, Int>,
    ) = TrialChamberHudLine(
        section,
        entries.map { (metric, count) -> TrialChamberHudEntry(metric, count) },
    )
}
