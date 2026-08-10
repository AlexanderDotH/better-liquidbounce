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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseFinderSeedMismatchRenderPlannerTest {

    private val settings = SeedMismatchRenderSettings(
        maximumDistance = 32.0,
        renderLimit = 2,
        missingSolidColor = Color4b(1, 2, 3),
        unexpectedSolidColor = Color4b(4, 5, 6),
        utilityMismatchColor = Color4b(7, 8, 9),
        materialSwapColor = Color4b(10, 11, 12),
    )

    @Test
    fun `planner culls by distance and honors render limit nearest first`() {
        val cells = listOf(
            SeedMismatchCell(BaseCoordinate(0, 64, 0), SeedMismatchKind.MISSING_SOLID),
            SeedMismatchCell(BaseCoordinate(100, 64, 0), SeedMismatchKind.UNEXPECTED_SOLID),
            SeedMismatchCell(BaseCoordinate(2, 64, 0), SeedMismatchKind.UTILITY),
            SeedMismatchCell(BaseCoordinate(1, 64, 0), SeedMismatchKind.MISSING_SOLID),
        )

        val batch = BaseFinderSeedMismatchRenderPlanner.plan(
            cells = cells,
            cameraPosition = Vec3(0.5, 64.5, 0.5),
            settings = settings,
        )

        assertEquals(2, batch.entries.size)
        assertEquals(BaseCoordinate(0, 64, 0), batch.entries[0].cell.position)
        assertEquals(BaseCoordinate(1, 64, 0), batch.entries[1].cell.position)
        assertTrue(batch.entries.none { it.cell.position.x == 100 })
    }

    @Test
    fun `planner maps mismatch kinds to configured colors`() {
        val cells = listOf(
            SeedMismatchCell(BaseCoordinate(0, 64, 0), SeedMismatchKind.MISSING_SOLID),
            SeedMismatchCell(BaseCoordinate(1, 64, 0), SeedMismatchKind.UNEXPECTED_SOLID),
            SeedMismatchCell(BaseCoordinate(2, 64, 0), SeedMismatchKind.UTILITY),
        )

        val batch = BaseFinderSeedMismatchRenderPlanner.plan(
            cells = cells,
            cameraPosition = Vec3(0.5, 64.5, 0.5),
            settings = settings.copy(renderLimit = 8),
        )

        assertEquals(settings.missingSolidColor, batch.entries[0].color)
        assertEquals(settings.unexpectedSolidColor, batch.entries[1].color)
        assertEquals(settings.utilityMismatchColor, batch.entries[2].color)
        assertEquals(1.0, batch.entries[0].worldBox.maxX - batch.entries[0].worldBox.minX)
    }
}
