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

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrialChamberRuntimeIntegrationContractTest {

    @Test
    fun `always-on runtime is initialized independently from the disabled tracker module`() {
        val bootstrap = source("src/main/kotlin/net/ccbluex/liquidbounce/LiquidBounce.kt")
        val runtime = source(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/TrialChamberRuntime.kt",
        )

        assertTrue(bootstrap.contains("TrialChamberRuntime.initialize()"))
        assertTrue(runtime.contains("ChunkScanner.subscribe(TrialChamberAnchorScanner)"))
        assertTrue(runtime.contains("if (enabled)"))
        assertTrue(runtime.contains("ChunkScanner.subscribe(TrialChamberLootScanner)"))
        assertTrue(runtime.contains("ChunkScanner.unsubscribe(TrialChamberLootScanner)"))
    }

    @Test
    fun `vault connected players accessor is registered without removing existing mixins`() {
        val mixins = JsonParser.parseString(
            source("src/main/resources/liquidbounce.mixins.json"),
        ).asJsonObject.getAsJsonArray("client").map { it.asString }

        assertTrue("minecraft.blockentity.MixinVaultSharedDataAccessor" in mixins)
        assertTrue("minecraft.render.MixinSpriteContentsAccessor" in mixins)
    }

    @Test
    fun `runtime preserves chamber continuity and reconciles live anchor block states`() {
        val runtime = source(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/TrialChamberRuntime.kt",
        )

        assertTrue(runtime.contains("TrialChamberSessionContinuity()"))
        assertTrue(runtime.contains("sessionContinuity.observe(selection.cluster)"))
        assertTrue(runtime.contains("resourceState.suspendObservations()"))
        assertFalse(runtime.contains("selectedAnchorPositions"))
        assertTrue(runtime.contains("resolveTrialSpawnerBlockObservation("))
        assertTrue(runtime.contains("reconcileVaultBlockObservation("))
        assertTrue(runtime.contains("state.getValue(VaultBlock.STATE)"))
    }

    @Test
    fun `runtime throttles heavy scans while packet membership stays immediately queryable`() {
        val runtime = source(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/TrialChamberRuntime.kt",
        )

        assertTrue(runtime.contains("refreshPolicy.shouldRefreshSnapshot(currentTick)"))
        assertTrue(runtime.contains("refreshPolicy.shouldRefreshLoot(currentTick)"))
        assertTrue(runtime.contains("refreshPolicy.shouldReconstructWave(currentTick)"))
        assertTrue(runtime.contains("membership.isCurrentTrialMob(uuid)"))
    }

    private fun source(path: String): String = Files.readString(Path.of(path))
}
