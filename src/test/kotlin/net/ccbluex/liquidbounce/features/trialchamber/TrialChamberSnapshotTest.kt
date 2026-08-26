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
package net.ccbluex.liquidbounce.features.trialchamber

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrialChamberSnapshotTest {

    @Test
    fun `snapshot defensively copies collections and indexes current trial mobs`() {
        val mobId = UUID.randomUUID()
        val origin = TrialChamberPosition(1, 2, 3)
        val mutableMobs = mutableListOf(
            TrialMobSnapshot(mobId, "minecraft:breeze", origin, origin, alive = true)
        )
        val snapshot = TrialChamberSnapshot.create(
            worldEpoch = 4,
            revision = 8,
            playerInsideChamber = true,
            anchors = listOf(TrialChamberAnchorSnapshot(origin, TrialChamberAnchorType.TRIAL_SPAWNER)),
            spawners = emptyList(),
            mobs = mutableMobs,
            vaults = emptyList(),
            loot = emptyList(),
        )

        mutableMobs.clear()

        assertEquals(1, snapshot.mobs.size)
        assertTrue(snapshot.playerInsideChamber)
        assertTrue(snapshot.isCurrentTrialMob(mobId))
        assertFalse(snapshot.isCurrentTrialMob(UUID.randomUUID()))
    }
}
