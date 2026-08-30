/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.utils.entity

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ExplosionExposureSamplerTest {

    @Test
    fun `sampling retains vanilla axis order and inclusive steps`() {
        val samples = mutableListOf<Vec3>()

        val exposure = ExplosionExposureSampler.calculate(AABB(1.0, 2.0, 3.0, 2.0, 3.0, 4.0)) { sample ->
            samples += sample
            sample.x <= 4.0 / 3.0
        }

        assertEquals(64, samples.size)
        assertEquals(0.5F, exposure)
        assertEquals(Vec3(1.0, 2.0, 3.0), samples[0])
        assertEquals(Vec3(1.0, 2.0, 10.0 / 3.0), samples[1])
        assertEquals(Vec3(1.0, 2.0, 11.0 / 3.0), samples[2])
        assertEquals(Vec3(1.0, 2.0, 4.0), samples[3])
        assertEquals(Vec3(1.0, 7.0 / 3.0, 3.0), samples[4])
        assertEquals(Vec3(2.0, 3.0, 4.0), samples.last())
    }

    @Test
    fun `sampling retains vanilla centering offsets for partial steps`() {
        val samples = mutableListOf<Vec3>()
        val box = AABB(1.0, 2.0, 3.0, 1.6, 2.2, 3.6)

        ExplosionExposureSampler.calculate(box) { sample ->
            samples += sample
            false
        }

        val step = 1.0 / 2.2
        val offset = (1.0 - kotlin.math.floor(1.0 / step) * step) / 2.0
        assertEquals(Vec3(1.0 + offset, 2.0, 3.0 + offset), samples.first())
        assertEquals(0.0F, ExplosionExposureSampler.calculate(box) { false })
    }

    @Test
    fun `entity facade retains customized world raycast contract`() {
        val source = Files.readString(EXPLOSION_DAMAGE_SOURCE)

        assertTrue("entityBoundingBox ?: boundingBox" in source)
        assertTrue("EntityCollisionContext(" in source)
        assertTrue("this.level().raycast(" in source)
        assertTrue("ClipContext.Block.COLLIDER" in source)
        assertTrue("ClipContext.Fluid.NONE" in source)
        assertTrue("exclude,\n            include,\n            maxBlastResistance," in source)
        assertTrue(").type == HitResult.Type.MISS" in source)
    }

    private companion object {
        val EXPLOSION_DAMAGE_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/LivingEntityExplosionDamage.kt",
        )
    }
}
