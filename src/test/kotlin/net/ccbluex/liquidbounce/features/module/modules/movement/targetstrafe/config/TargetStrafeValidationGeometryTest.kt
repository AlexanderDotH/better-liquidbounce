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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.config

import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TargetStrafeValidationGeometryTest {

    @Test
    fun `standing collision box keeps click tp precision deflation`() {
        val dimensions = EntityDimensions.scalable(0.6f, 1.8f)
        val position = Vec3(4.0, 12.0, -3.0)

        val expected = dimensions.makeBoundingBox(position).deflate(1.0E-7)
        val actual = targetStrafeStandingCollisionBox(position, dimensions)

        assertEquals(expected.minX, actual.minX)
        assertEquals(expected.minY, actual.minY)
        assertEquals(expected.minZ, actual.minZ)
        assertEquals(expected.maxX, actual.maxX)
        assertEquals(expected.maxY, actual.maxY)
        assertEquals(expected.maxZ, actual.maxZ)
    }
}
