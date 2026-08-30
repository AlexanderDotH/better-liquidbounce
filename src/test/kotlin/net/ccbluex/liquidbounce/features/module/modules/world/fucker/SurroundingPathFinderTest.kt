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
package net.ccbluex.liquidbounce.features.module.modules.world.fucker

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SurroundingPathFinderTest {

    @Test
    fun `path ordering keeps the resistance and distance priorities`() {
        val baseline = path(resistance = 2.0, blockerCount = 2, targetDistance = 2.0, eyeDistance = 2.0)

        assertTrue(path(1.0, 99, 99.0, 99.0) < baseline)
        assertTrue(path(2.0, 1, 99.0, 99.0) < baseline)
        assertTrue(path(2.0, 2, 1.0, 99.0) < baseline)
        assertTrue(path(2.0, 2, 2.0, 1.0) < baseline)
    }

    @Test
    fun `later candidate wins a complete comparator tie`() {
        val current = path(1.0, 1, 1.0, 1.0, targetPoint = Vec3.ZERO)
        val candidate = path(1.0, 1, 1.0, 1.0, targetPoint = Vec3(1.0, 1.0, 1.0))

        assertSame(candidate, selectBetterPath(current, candidate))
    }

    @Test
    fun `target after eight blockers keeps blocker order`() {
        val target = BlockPos(20, 0, 0)
        val blockers = (1..8).map { BlockPos(it, 0, 0) }
        val hits = ArrayDeque<BlockPos>().apply {
            addAll(blockers)
            add(target)
        }
        val exclusions = mutableListOf<List<BlockPos>>()

        val result = collectBlockingPath(
            target = target,
            raycastBlock = { ignored ->
                exclusions += ignored.toList()
                hits.removeFirstOrNull()
            },
            isValidBlocker = { true },
        )

        assertEquals(blockers, result)
        assertEquals(blockers.indices.map { blockers.take(it) } + listOf(blockers), exclusions)
    }

    @Test
    fun `ninth blocker exceeds the trace limit`() {
        val target = BlockPos(20, 0, 0)
        val hits = ArrayDeque((1..9).map { BlockPos(it, 0, 0) } + target)

        val result = collectBlockingPath(
            target = target,
            raycastBlock = { hits.removeFirstOrNull() },
            isValidBlocker = { true },
        )

        assertNull(result)
        assertEquals(listOf(target), hits)
    }

    @Test
    fun `miss repeated hit and invalid blocker reject the path`() {
        val target = BlockPos(20, 0, 0)
        val blocker = BlockPos(1, 0, 0)

        assertNull(collectBlockingPath(target, raycastBlock = { null }, isValidBlocker = { true }))

        val repeatedHits = ArrayDeque(listOf(blocker, blocker, target))
        assertNull(
            collectBlockingPath(
                target,
                raycastBlock = { repeatedHits.removeFirstOrNull() },
                isValidBlocker = { true },
            )
        )

        assertNull(
            collectBlockingPath(
                target,
                raycastBlock = { blocker },
                isValidBlocker = { false },
            )
        )
    }

    private fun path(
        resistance: Double,
        blockerCount: Int,
        targetDistance: Double,
        eyeDistance: Double,
        targetPoint: Vec3 = Vec3.ZERO,
    ) = SurroundingPath(
        firstBlock = BlockPos.ZERO,
        blocks = listOf(BlockPos.ZERO),
        info = SurroundingInfo(
            actualTargetPos = BlockPos.ZERO,
            targetPoint = targetPoint,
            resistance = resistance,
            blockerCount = blockerCount,
            firstBlockDistanceToTarget = targetDistance,
            firstBlockDistanceToEyes = eyeDistance,
        ),
    )
}
