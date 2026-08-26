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
package net.ccbluex.liquidbounce.features.module.modules.world.trialchamber

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TrialChamberRenderPlanCacheTest {

    private val targets = listOf(TrialChamberRenderTarget(
        id = "spawner",
        kind = TrialChamberRenderTargetKind.SPAWNER,
        position = Vec3(4.0, 64.0, 4.0),
        worldBox = AABB(3.5, 63.5, 3.5, 4.5, 64.5, 4.5),
        label = "Trial Spawner",
        color = Color4b(255, 132, 48),
    ))

    @Test
    fun `unchanged snapshot and sub-block camera motion reuse the same plan`() {
        val cache = TrialChamberRenderPlanCache(cameraReplanDistance = 1.0)
        val key = TrialChamberRenderSnapshotKey(worldEpoch = 1, revision = 7)
        val first = cache.resolve(key, Vec3.ZERO, targets, TrialChamberRenderSettings())

        val second = cache.resolve(key, Vec3(0.5, 0.0, 0.0), targets, TrialChamberRenderSettings())

        assertSame(first, second)
    }

    @Test
    fun `meaningful camera motion snapshot changes and setting changes invalidate the plan`() {
        val cache = TrialChamberRenderPlanCache(cameraReplanDistance = 1.0)
        val firstKey = TrialChamberRenderSnapshotKey(worldEpoch = 1, revision = 7)
        val first = cache.resolve(firstKey, Vec3.ZERO, targets, TrialChamberRenderSettings())
        val moved = cache.resolve(firstKey, Vec3(1.0, 0.0, 0.0), targets, TrialChamberRenderSettings())
        val revised = cache.resolve(
            firstKey.copy(revision = 8),
            Vec3(1.0, 0.0, 0.0),
            targets,
            TrialChamberRenderSettings(),
        )
        val settingsChanged = cache.resolve(
            firstKey.copy(revision = 8),
            Vec3(1.0, 0.0, 0.0),
            targets,
            TrialChamberRenderSettings(showGlow = false),
        )

        assertNotSame(first, moved)
        assertNotSame(moved, revised)
        assertNotSame(revised, settingsChanged)
    }

    @Test
    fun `reset invalidates an otherwise reusable plan`() {
        val cache = TrialChamberRenderPlanCache()
        val key = TrialChamberRenderSnapshotKey(worldEpoch = 1, revision = 7)
        val first = cache.resolve(key, Vec3.ZERO, targets, TrialChamberRenderSettings())

        cache.reset()
        val afterReset = cache.resolve(key, Vec3.ZERO, targets, TrialChamberRenderSettings())

        assertNotSame(first, afterReset)
    }
}
