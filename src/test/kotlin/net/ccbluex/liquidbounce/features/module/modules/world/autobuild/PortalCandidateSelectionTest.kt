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
package net.ccbluex.liquidbounce.features.module.modules.world.autobuild

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PortalCandidateSelectionTest {

    @Test
    fun `portal candidates preserve direction height and lateral order`() {
        val center = BlockPos(7, 64, -3)

        val actual = PortalCandidateGeometry.around(center).toList()
        val expected = Direction.BY_2D_DATA.flatMap { direction ->
            OFFSETS.map { (verticalOffset, lateralOffset) ->
                expectedGeometry(center, direction, verticalOffset, lateralOffset)
            }
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `selection scores every candidate once and keeps the first equal best portal`() {
        val candidates = listOf("invalid", "first-best", "equal-best", "lower")
        val scores = mapOf("invalid" to null, "first-best" to 7, "equal-best" to 7, "lower" to 2)
        val scored = mutableListOf<String>()

        val selected = PortalCandidateSelector.selectBest(candidates.asSequence()) { candidate ->
            scored += candidate
            scores.getValue(candidate)
        }

        assertEquals("first-best", selected)
        assertEquals(candidates, scored)
    }

    @Test
    fun `selection replaces the current portal only for a strictly higher score`() {
        val candidates = sequenceOf("first", "higher", "equal-to-higher")
        val scores = mapOf("first" to 3, "higher" to 9, "equal-to-higher" to 9)

        val selected = PortalCandidateSelector.selectBest(candidates, scores::getValue)

        assertEquals("higher", selected)
    }

    private fun expectedGeometry(
        center: BlockPos,
        direction: Direction,
        verticalOffset: Int,
        lateralOffset: Int,
    ): PortalCandidateGeometry {
        val rotated = direction.clockWise
        val origin = center.mutable().move(direction)
        if (lateralOffset == -1) {
            origin.move(rotated.opposite)
        }
        if (verticalOffset == -1) {
            origin.move(Direction.DOWN)
        }
        return PortalCandidateGeometry(origin, verticalOffset == -1, direction, rotated)
    }

    private companion object {
        val OFFSETS = listOf(-1 to 0, -1 to -1, 0 to 0, 0 to -1)
    }
}
