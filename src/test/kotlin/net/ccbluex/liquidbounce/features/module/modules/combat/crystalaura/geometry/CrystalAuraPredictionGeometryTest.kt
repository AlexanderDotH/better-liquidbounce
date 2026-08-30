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

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CrystalAuraPredictionGeometryTest {

    @Test
    fun `predicted box keeps width and height while centering on the simulated position`() {
        val original = AABB(4.7, 10.0, 7.7, 5.3, 11.8, 8.3)

        val predicted = predictedEntityBoundingBox(original, Vec3(20.0, 30.0, 40.0))

        assertEquals(19.7, predicted.minX, 1.0E-12)
        assertEquals(30.0, predicted.minY, 1.0E-12)
        assertEquals(39.7, predicted.minZ, 1.0E-12)
        assertEquals(20.3, predicted.maxX, 1.0E-12)
        assertEquals(31.8, predicted.maxY, 1.0E-12)
        assertEquals(40.3, predicted.maxZ, 1.0E-12)
    }
}
