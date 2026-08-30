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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.modules.render.tracers.TracerRenderBatch
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.TracerSegment
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.TracerLineDraw
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.forEachLine
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleTracersGlowTest {

    @AfterEach
    fun restoreLineMode() {
        mode().restore()
    }

    @Test
    fun `tracers defaults to line and keeps shader controls inside glow`() {
        val mode = mode()
        mode.restore()

        assertEquals(ValueType.CHOICE, mode.valueType)
        assertEquals("Line", mode.activeMode.name)
        assertEquals(listOf("Line", "Glow"), mode.modes.map(Mode::name))
        assertTrue(mode.modes.single { it.name == "Line" }.inner.isEmpty())

        val glow = mode.modes.single { it.name == "Glow" }
        assertEquals(
            listOf("Radius", "Softness", "Intensity", "Opacity"),
            glow.inner.map { it.name },
        )
        assertRange(glow, "Radius", 4f, 24f, "px")
        assertRange(glow, "Softness", 0.5f, 1.5f, "")
        assertRange(glow, "Intensity", 0f, 2f, "")

        val rootNames = ModuleTracers.inner.map { it.name }
        assertTrue("ColorMode" in rootNames)
        assertTrue("LineWidth" in rootNames)
        assertTrue("MaximumDistance" in rootNames)
        assertFalse(glow.inner.any { it.name in rootNames && it.name != "Mode" })
    }

    @Test
    fun `empty tracer batch does not create a glow contribution`() {
        val batch = TracerRenderBatch(emptyList(), lineWidth = 3f)
        var contributionCount = 0

        val contributed = batch.contributeGlowIfPresent {
            contributionCount++
        }

        assertFalse(contributed)
        assertEquals(0, contributionCount)
    }

    @Test
    fun `one tracer glow contribution preserves batched geometry colors and width`() {
        val first = TracerSegment(
            color = Color4b(12, 34, 56, 78),
            eyePosition = Vec3f(1f, 2f, 3f),
            targetPosition = Vec3f(4f, 5f, 6f),
        )
        val second = TracerSegment(
            color = Color4b(90, 80, 70, 60),
            eyePosition = Vec3f(1f, 2f, 3f),
            targetPosition = Vec3f(7f, 8f, 9f),
        )
        val batch = TracerRenderBatch(listOf(first, second), lineWidth = 4.5f)
        var contributionCount = 0
        var captured: TracerRenderBatch? = null

        val contributed = batch.contributeGlowIfPresent {
            contributionCount++
            captured = it
        }

        assertTrue(contributed)
        assertEquals(1, contributionCount)
        assertSame(batch, captured)
        assertEquals(4.5f, captured?.lineWidth)
        assertEquals(listOf(first, second), captured?.segments)
        assertEquals(Color4b(12, 34, 56, 255), first.glowMaskColor)
        assertEquals(Color4b(90, 80, 70, 255), second.glowMaskColor)
    }

    @Test
    fun `one pixel tracer uses a stable two pixel glow mask`() {
        val segment = TracerSegment(
            color = Color4b.WHITE,
            eyePosition = Vec3f.ZERO,
            targetPosition = Vec3f.Z_AXIS,
        )

        assertEquals(2f, TracerRenderBatch(listOf(segment), lineWidth = 1f).glowMaskLineWidth)
        assertEquals(4.5f, TracerRenderBatch(listOf(segment), lineWidth = 4.5f).glowMaskLineWidth)
    }

    @Test
    fun `one target emits one identical direct line for the core and halo mask`() {
        val eye = Vec3f(1f, 2f, 3f)
        val target = Vec3f(4f, 5f, 6f)
        val batch = TracerRenderBatch(
            listOf(TracerSegment(Color4b(12, 34, 56, 78), eye, target)),
            lineWidth = 1f,
        )
        val coreDraws = mutableListOf<TracerLineDraw>()
        val haloDraws = mutableListOf<TracerLineDraw>()

        batch.forEachLine(glowMask = false, draw = coreDraws::add)
        batch.forEachLine(glowMask = true, draw = haloDraws::add)

        assertEquals(listOf(TracerLineDraw(Color4b(12, 34, 56, 78), 1f, eye, target)), coreDraws)
        assertEquals(
            listOf(TracerLineDraw(Color4b(12, 34, 56, 255), 2f, eye, target, depthTested = true)),
            haloDraws,
        )
    }

    @Test
    fun `Glow tracer core is depth tested so it cannot paint over player skin`() {
        val batch = TracerRenderBatch(
            listOf(TracerSegment(Color4b.WHITE, Vec3f.ZERO, Vec3f.Z_AXIS)),
            lineWidth = 1f,
        )
        val draws = mutableListOf<TracerLineDraw>()

        batch.forEachLine(glowMask = false, depthTested = true, draw = draws::add)

        assertTrue(draws.single().depthTested)
    }

    @Test
    fun `Glow mode requests a depth tested direct tracer core`() {
        val source = java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleTracers.kt"
            )
        )

        assertTrue(source.contains("depthTested = glowMode"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun mode(): ModeValueGroup<Mode> {
        MinecraftBootstrap.ensureInitialized()
        return ModuleTracers.inner.single { it.name == "Mode" } as ModeValueGroup<Mode>
    }

    private fun assertRange(
        mode: Mode,
        name: String,
        from: Float,
        to: Float,
        suffix: String,
    ) {
        val value = mode.inner.single { it.name == name } as RangedValue<*>
        assertEquals(from, value.range.start)
        assertEquals(to, value.range.endInclusive)
        assertEquals(suffix, value.suffix)
    }
}
