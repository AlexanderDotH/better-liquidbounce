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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleBaseFinderTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `BaseFinder is a World module tagged by its inclusive confidence threshold`() {
        assertEquals("BaseFinder", ModuleBaseFinder.name)
        assertEquals(ModuleCategories.WORLD, ModuleBaseFinder.category)
        val confidence = ModuleBaseFinder.setting("MinimumConfidence") as RangedValue<*>
        assertEquals(65, confidence.get())
        assertEquals(0..100, confidence.range)
        assertEquals("%", confidence.suffix)
        assertEquals("65", ModuleBaseFinder.tag)
    }

    @Test
    fun `all detector families and user output are enabled by default`() {
        val defaults = mapOf(
            "Storage" to true,
            "Utilities" to true,
            "Automation" to true,
            "Entities" to true,
            "Structural" to true,
            "Geometry" to true,
            "Activity" to true,
            "ChunkTrails" to true,
            "Notifications" to true,
            "ChatCoordinates" to true,
        )

        defaults.forEach { (name, expected) ->
            assertEquals(expected, ModuleBaseFinder.setting(name).get(), name)
        }
        assertEquals(2, ModuleBaseFinder.setting("DirtyChunksPerTick").get())
        assertEquals(20, ModuleBaseFinder.setting("EntitySampleInterval").get())
    }

    @Test
    fun `GlowBox has the complete requested visual schema`() {
        val glowBox = ModuleBaseFinder.group("GlowBox")

        assertEquals(true, glowBox.setting("Enabled").get())
        assertEquals(512, glowBox.setting("MaximumDistance").get())
        assertEquals(32, glowBox.setting("RenderLimit").get())
        assertEquals(4, glowBox.setting("BoxRadius").get())
        assertEquals(6, glowBox.setting("BoxHeight").get())
        assertEquals(Color4b(255, 186, 32), glowBox.setting("LowConfidenceColor").get())
        assertEquals(Color4b(255, 60, 180), glowBox.setting("HighConfidenceColor").get())
        assertEquals(true, glowBox.setting("ShowLabels").get())
        assertEquals(8, glowBox.setting("MaxLabels").get())

        val pulse = glowBox.group("Pulse")
        assertEquals(true, pulse.setting("Enabled").get())
        assertEquals(0.8f, pulse.setting("Speed").get())
        assertEquals(15, pulse.setting("Amount").get())

        assertEquals(14f, glowBox.setting("Radius").get())
        assertEquals(1f, glowBox.setting("Softness").get())
        assertEquals(1f, glowBox.setting("Intensity").get())
        assertEquals(1.25f, glowBox.setting("CoreSize").get())
        assertEquals(100, glowBox.setting("Opacity").get())
    }

    @Test
    fun `published render findings are immutable copies`() {
        val source = mutableListOf("alpha", "beta")

        val published = ModuleBaseFinder.immutableCopy(source)
        source += "gamma"

        assertEquals(listOf("alpha", "beta"), published)
        assertTrue(runCatching { (published as MutableList<String>) += "delta" }.isFailure)
    }

    @Test
    fun `findings announce once and only announce again after a tier upgrade`() {
        val announcements = BaseFinderAnnouncementState()

        assertTrue(announcements.shouldAnnounce("base-a", 0))
        assertEquals(false, announcements.shouldAnnounce("base-a", 0))
        assertEquals(false, announcements.shouldAnnounce("base-a", -1))
        assertTrue(announcements.shouldAnnounce("base-a", 1))
        assertEquals(false, announcements.shouldAnnounce("base-a", 1))
        assertTrue(announcements.shouldAnnounce("base-a", 2))
    }

    @Test
    fun `packet sound coordinates use mathematical floor at negative positions`() {
        assertEquals(-1, baseFinderBlockCoordinate(-0.01))
        assertEquals(-2, baseFinderBlockCoordinate(-1.01))
        assertEquals(3, baseFinderBlockCoordinate(3.99))
    }

    @Test
    fun `evidence fingerprint changes with threshold and detector configuration`() {
        val snapshots = listOf(ChunkEvidenceSnapshot(ChunkCoordinate(0, 0)))
        val enabled = BaseSignalFamily.entries.toSet()

        val original = baseFinderEvidenceFingerprint(snapshots, 65, enabled)

        assertTrue(original != baseFinderEvidenceFingerprint(snapshots, 66, enabled))
        assertTrue(original != baseFinderEvidenceFingerprint(snapshots, 65, enabled - BaseSignalFamily.ACTIVITY))
    }

    private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name } as Value<*>

    private fun ValueGroup.group(name: String): ValueGroup = inner.single { it.name == name } as ValueGroup
}
