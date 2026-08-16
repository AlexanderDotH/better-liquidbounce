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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillRouteTargetSnapshotTest {

    @Test
    fun `lag paced route predicts beyond the former thirty tick ceiling`() {
        val positions = (0..80).map { tick -> Vec3(tick.toDouble(), 0.0, 0.0) }
        val snapshot = SpearKillRouteTargetSnapshot(
            observedPosition = Vec3.ZERO,
            eyeOffset = Vec3(0.0, 1.62, 0.0),
            boundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            velocity = Vec3(1.0, 0.0, 0.0),
            predictedPositions = positions,
        )

        assertEquals(60.0, snapshot.predict(60).position.x)
    }

    @Test
    fun `prediction horizon follows route timing within a defensive cap`() {
        assertEquals(30, spearKillTargetSnapshotTicks(estimatedHitTicks = 12))
        assertEquals(140, spearKillTargetSnapshotTicks(estimatedHitTicks = 140))
        assertEquals(512, spearKillTargetSnapshotTicks(estimatedHitTicks = 2_000))
    }

    @Test
    fun `long prediction keeps collision seeding bounded and includes both endpoints`() {
        val positions = (0..512).map { tick -> Vec3(tick.toDouble(), 0.0, 0.0) }
        val snapshot = SpearKillRouteTargetSnapshot(
            observedPosition = Vec3.ZERO,
            eyeOffset = Vec3(0.0, 1.62, 0.0),
            boundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            velocity = Vec3(1.0, 0.0, 0.0),
            predictedPositions = positions,
        )

        val corridorPositions = snapshot.collisionCorridorPositions()

        assertTrue(corridorPositions.size <= 31)
        assertEquals(positions.first(), corridorPositions.first())
        assertEquals(positions.last(), corridorPositions.last())
    }
}
