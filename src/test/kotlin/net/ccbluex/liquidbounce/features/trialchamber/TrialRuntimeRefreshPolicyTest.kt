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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrialRuntimeRefreshPolicyTest {

    @Test
    fun `snapshot refresh runs immediately and then at most five times per second`() {
        val policy = TrialRuntimeRefreshPolicy(snapshotIntervalTicks = 4)

        assertTrue(policy.shouldRefreshSnapshot(100))
        assertFalse(policy.shouldRefreshSnapshot(101))
        assertFalse(policy.shouldRefreshSnapshot(103))
        assertTrue(policy.shouldRefreshSnapshot(104))
    }

    @Test
    fun `loot scan and fallback reconstruction run at most once per second`() {
        val policy = TrialRuntimeRefreshPolicy(
            lootIntervalTicks = 20,
            fallbackIntervalTicks = 20,
        )

        assertTrue(policy.shouldRefreshLoot(200))
        assertFalse(policy.shouldRefreshLoot(219))
        assertTrue(policy.shouldRefreshLoot(220))
        assertTrue(policy.shouldReconstructWave(200))
        assertFalse(policy.shouldReconstructWave(219))
        assertTrue(policy.shouldReconstructWave(220))
    }

    @Test
    fun `enabling resource tracking forces prompt snapshot and loot refresh`() {
        val policy = TrialRuntimeRefreshPolicy()
        policy.shouldRefreshSnapshot(100)
        policy.shouldRefreshLoot(100)
        assertFalse(policy.shouldRefreshSnapshot(101))
        assertFalse(policy.shouldRefreshLoot(101))

        policy.forceSnapshotAndLootRefresh()

        assertTrue(policy.shouldRefreshSnapshot(101))
        assertTrue(policy.shouldRefreshLoot(101))
    }

    @Test
    fun `world reset makes every refresh stream immediately eligible again`() {
        val policy = TrialRuntimeRefreshPolicy()
        policy.shouldRefreshSnapshot(100)
        policy.shouldRefreshLoot(100)
        policy.shouldReconstructWave(100)

        policy.reset()

        assertTrue(policy.shouldRefreshSnapshot(1))
        assertTrue(policy.shouldRefreshLoot(1))
        assertTrue(policy.shouldReconstructWave(1))
    }
}
