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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillCollisionSnapshotTest {

    @Test
    fun `route snapshot and calculation complete on the caller thread`() {
        val callerThread = Thread.currentThread().name
        val captureThreads = mutableSetOf<String>()
        val builder = SpearKillCollisionSnapshotBuilder(
            SpearKillRouteSnapshotBounds(0, 0, 0, 2, 0, 0),
        )

        val calculationThread = calculateSpearKillRouteSynchronously(
            snapshotBuilder = builder,
            collisionBoxesAt = {
                captureThreads += Thread.currentThread().name
                emptyList()
            },
        ) { Thread.currentThread().name }

        assertEquals(callerThread, calculationThread)
        assertEquals(setOf(callerThread), captureThreads)
        assertTrue(builder.complete)
    }

    @Test
    fun `bounded snapshot capture obeys its block budget`() {
        val bounds = SpearKillRouteSnapshotBounds(0, 0, 0, 2, 0, 0)
        val builder = SpearKillCollisionSnapshotBuilder(bounds)
        val captured = mutableListOf<BlockPos>()

        assertFalse(builder.captureSlice(maxBlocks = 2) { position ->
            captured += position.immutable()
            emptyList()
        })
        assertEquals(2, captured.size)

        assertTrue(builder.captureSlice(maxBlocks = 2) { position ->
            captured += position.immutable()
            emptyList()
        })
        assertEquals(3, captured.distinct().size)
    }

    @Test
    fun `immutable collision boxes reject a swept player segment`() {
        val bounds = SpearKillRouteSnapshotBounds(-1, -1, -1, 4, 3, 1)
        val builder = SpearKillCollisionSnapshotBuilder(bounds)
        builder.captureSlice(maxBlocks = Int.MAX_VALUE) { position ->
            if (position == BlockPos(2, 0, 0)) {
                listOf(AABB(2.0, 0.0, 0.0, 3.0, 2.0, 1.0))
            } else {
                emptyList()
            }
        }
        val snapshot = builder.build()
        val playerBox = AABB(0.1, 0.0, 0.1, 0.9, 1.8, 0.9)

        assertFalse(snapshot.isSegmentClear(playerBox, Vec3(3.0, 0.0, 0.0)))
        assertTrue(snapshot.isSegmentClear(playerBox, Vec3(0.0, 0.0, 0.8)))
    }

    @Test
    fun `a segment leaving the captured world fails closed`() {
        val bounds = SpearKillRouteSnapshotBounds(0, 0, 0, 1, 2, 1)
        val builder = SpearKillCollisionSnapshotBuilder(bounds)
        builder.captureSlice(maxBlocks = Int.MAX_VALUE) { emptyList() }
        val snapshot = builder.build()
        val playerBox = AABB(0.1, 0.0, 0.1, 0.9, 1.8, 0.9)

        assertFalse(snapshot.isSegmentClear(playerBox, Vec3(4.0, 0.0, 0.0)))
    }

    @Test
    fun `synchronous calculation resolves uncovered route cells on the caller thread`() {
        val callerThread = Thread.currentThread().name
        val queriedThreads = mutableSetOf<String>()
        val builder = SpearKillCollisionSnapshotBuilder(
            SpearKillRouteSnapshotBounds(0, -1, 0, 1, 2, 1),
        )
        val playerBox = AABB(0.1, 0.0, 0.1, 0.9, 1.8, 0.9)

        val segmentClear = calculateSpearKillRouteSynchronously(
            snapshotBuilder = builder,
            collisionBoxesAt = {
                queriedThreads += Thread.currentThread().name
                emptyList()
            },
        ) { snapshot ->
            snapshot.isSegmentClear(playerBox, Vec3(4.0, 0.0, 0.0))
        }

        assertTrue(segmentClear)
        assertEquals(setOf(callerThread), queriedThreads)
    }

    @Test
    fun `uncovered live collision still blocks a synchronous route`() {
        val builder = SpearKillCollisionSnapshotBuilder(
            SpearKillRouteSnapshotBounds(0, -1, 0, 1, 2, 1),
        )
        val playerBox = AABB(0.1, 0.0, 0.1, 0.9, 1.8, 0.9)

        val segmentClear = calculateSpearKillRouteSynchronously(
            snapshotBuilder = builder,
            collisionBoxesAt = { position ->
                if (position.x == 2) {
                    listOf(AABB(2.0, 0.0, 0.0, 3.0, 2.0, 1.0))
                } else {
                    emptyList()
                }
            },
        ) { snapshot ->
            snapshot.isSegmentClear(playerBox, Vec3(4.0, 0.0, 0.0))
        }

        assertFalse(segmentClear)
    }

    @Test
    fun `capped seed corridor resolves the remaining long route on demand`() {
        val destination = Vec3(100.0, 0.0, 100.0)
        val builder = SpearKillCollisionSnapshotBuilder.corridor(
            points = listOf(Vec3.ZERO, destination),
            horizontalMargin = 10,
            verticalMargin = 6,
            maxCells = 256,
        )
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)

        val segmentClear = calculateSpearKillRouteSynchronously(
            snapshotBuilder = builder,
            collisionBoxesAt = { emptyList() },
        ) { snapshot ->
            snapshot.isSegmentClear(playerBox, destination)
        }

        assertEquals(256, builder.capturedBlockCount)
        assertTrue(segmentClear)
    }

    @Test
    fun `long diagonal validates against its narrow captured corridor`() {
        val destination = Vec3(100.0, 0.0, 100.0)
        val builder = SpearKillCollisionSnapshotBuilder.corridor(
            points = listOf(Vec3.ZERO, destination),
            horizontalMargin = 0,
            verticalMargin = 0,
            maxCells = 65_536,
        )
        while (!builder.complete) {
            builder.captureSlice(maxBlocks = 4_096) { emptyList() }
        }
        val snapshot = builder.build()
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)

        assertTrue(snapshot.isSegmentClear(playerBox, destination))
    }
}
