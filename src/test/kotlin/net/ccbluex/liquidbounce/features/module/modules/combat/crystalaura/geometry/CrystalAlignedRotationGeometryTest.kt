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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.geometry

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CrystalAlignedRotationGeometryTest {

    @Test
    fun `point offsets remain on the two axes of every selected face`() {
        val center = Vec3(10.0, 20.0, 30.0)
        val expected = mapOf(
            Direction.DOWN to Vec3(12.0, 20.0, 33.0),
            Direction.UP to Vec3(12.0, 20.0, 33.0),
            Direction.NORTH to Vec3(12.0, 23.0, 30.0),
            Direction.SOUTH to Vec3(12.0, 23.0, 30.0),
            Direction.WEST to Vec3(10.0, 22.0, 33.0),
            Direction.EAST to Vec3(10.0, 22.0, 33.0),
        )

        expected.forEach { (side, point) ->
            assertVec3Equals(point, pointOnSide(side, 2.0, 3.0, center), 1.0E-12)
        }
    }

    @Test
    fun `predicted crystal box preserves the original two by two by two bounds`() {
        val box = predictedCrystalBox(BlockPos(10, 20, 30))

        assertEquals(9.5, box.minX)
        assertEquals(21.0, box.minY)
        assertEquals(29.5, box.minZ)
        assertEquals(11.5, box.maxX)
        assertEquals(23.0, box.maxY)
        assertEquals(31.5, box.maxZ)
    }

    @Test
    fun `geometry package stays independent from modules and render implementations`() {
        val source = Files.readString(Path.of(SOURCE))

        assertFalse("features.module.modules.combat.crystalaura.ModuleCrystalAura" in source)
        assertFalse("features.module.modules.render.ModuleDebug" in source)
        assertFalse("net.ccbluex.liquidbounce.render." in source)
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/crystalaura/geometry/" +
                "FindClosestPointOnBlockInLineWithCrystal.kt"
    }
}
