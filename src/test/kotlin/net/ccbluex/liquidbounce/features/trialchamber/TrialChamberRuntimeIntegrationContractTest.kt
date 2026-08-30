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
        val compositionRoot = source(
            "src/main/kotlin/net/ccbluex/liquidbounce/bootstrap/liquidbounce/ClientManagerInitializer.kt",
        )
        val runtime = source(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/TrialChamberRuntime.kt",
        )

        assertTrue(compositionRoot.contains("TrialChamberRuntime.initialize()"))
        assertTrue(runtime.contains("ChunkScanner.subscribe(TrialChamberAnchorScanner)"))
        assertTrue(runtime.contains("if (enabled)"))
        assertTrue(runtime.contains("ChunkScanner.subscribe(TrialChamberLootScanner)"))
        assertTrue(runtime.contains("ChunkScanner.unsubscribe(TrialChamberLootScanner)"))
    }

    @Test
    fun `vault connected players accessor is registered without removing existing mixins`() {
        val runtime = runtimeImplementation()
        val mixins = JsonParser.parseString(
            source("src/main/resources/liquidbounce.mixins.json"),
        ).asJsonObject.getAsJsonArray("client").map { it.asString }

        assertTrue("minecraft.blockentity.MixinVaultSharedDataAccessor" in mixins)
        assertTrue("minecraft.render.MixinSpriteContentsAccessor" in mixins)
        assertTrue(runtime.contains("VaultSharedDataAccess"))
        assertFalse(runtime.contains("MixinVaultSharedDataAccessor"))
    }

    @Test
    fun `runtime preserves chamber continuity and reconciles live anchor block states`() {
        val runtime = runtimeImplementation()

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
        val runtime = runtimeImplementation()

        assertTrue(runtime.contains("refreshPolicy.shouldRefreshSnapshot(currentTick)"))
        assertTrue(runtime.contains("refreshPolicy.shouldRefreshLoot(currentTick)"))
        assertTrue(runtime.contains("refreshPolicy.shouldReconstructWave(currentTick)"))
        assertTrue(runtime.contains("membership.isCurrentTrialMob(uuid)"))
    }

    @Test
    fun `runtime delegates resource interaction and snapshot materialization without structural suppression`() {
        val runtime = source(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/TrialChamberRuntime.kt",
        )
        val coordinator = source(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/SnapshotRefreshCoordinator.kt",
        )

        assertFalse(runtime.contains("@Suppress(\"TooManyFunctions\")"))
        assertTrue(runtime.contains("ResourceInteractionTracker(resourceState)"))
        assertTrue(runtime.contains("SnapshotRefreshCoordinator("))
        assertTrue(coordinator.contains("TrialChamberSelector.select("))
        assertInOrder(
            coordinator,
            "membership.retainCurrentOrigins(currentOrigins)",
            "val spawners = selectedAnchors.mapNotNull(worldSnapshots::spawnerSnapshot)",
            "mobSnapshots.prune(currentOrigins, currentTick)",
            "worldSnapshots.syncResources(",
            "val refreshedResources = resourceState.snapshot()",
            "TrialChamberSnapshot.create(",
        )
    }

    private fun runtimeImplementation(): String = listOf(
        "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/TrialChamberRuntime.kt",
        "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/SnapshotRefreshCoordinator.kt",
        "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/WorldSnapshotReader.kt",
        "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/MobSnapshotCollector.kt",
        "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/ResourceInteractionTracker.kt",
    ).joinToString("\n", transform = ::source)

    private fun assertInOrder(source: String, vararg fragments: String) {
        val positions = fragments.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing fragment in source order contract: $positions")
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right }, "Unexpected source order: $positions")
    }

    private fun source(path: String): String = Files.readString(Path.of(path))
}
