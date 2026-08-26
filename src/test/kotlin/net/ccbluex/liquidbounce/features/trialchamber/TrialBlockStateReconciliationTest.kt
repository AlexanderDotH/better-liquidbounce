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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrialBlockStateReconciliationTest {

    @Test
    fun `live spawner block phase wins over a stale block entity phase`() {
        val observation = resolveTrialSpawnerBlockObservation(
            liveBlockPhase = TrialSpawnerPhase.COOLDOWN,
            blockEntityPhase = TrialSpawnerPhase.ACTIVE,
        )

        assertEquals(TrialSpawnerPhase.COOLDOWN, observation.phase)
        assertTrue(observation.completed)
    }

    @Test
    fun `spawner block entity phase is a fallback when live state is unavailable`() {
        val observation = resolveTrialSpawnerBlockObservation(
            liveBlockPhase = null,
            blockEntityPhase = TrialSpawnerPhase.EJECTING_REWARD,
        )

        assertEquals(TrialSpawnerPhase.EJECTING_REWARD, observation.phase)
        assertFalse(observation.completed)
    }

    @Test
    fun `missing spawner observations safely resolve to inactive`() {
        val observation = resolveTrialSpawnerBlockObservation(null, null)

        assertEquals(TrialSpawnerPhase.INACTIVE, observation.phase)
        assertFalse(observation.completed)
    }

    @Test
    fun `only stable vault block phases permit used-state inference`() {
        assertTrue(TrialVaultBlockPhase.INACTIVE.permitsClaimInference)
        assertTrue(TrialVaultBlockPhase.ACTIVE.permitsClaimInference)
        assertFalse(TrialVaultBlockPhase.UNLOCKING.permitsClaimInference)
        assertFalse(TrialVaultBlockPhase.EJECTING.permitsClaimInference)
    }
}
