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

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ModuleBaseFinderTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `BaseFinder is a World module with high sensitivity and a zero confidence default`() {
        assertEquals("BaseFinder", ModuleBaseFinder.name)
        assertEquals(ModuleCategories.WORLD, ModuleBaseFinder.category)
        val confidence = ModuleBaseFinder.setting("MinimumConfidence") as RangedValue<*>
        assertEquals(0, confidence.get())
        assertEquals(0..100, confidence.range)
        assertEquals("%", confidence.suffix)
        assertEquals(true, ModuleBaseFinder.setting("HighSensitivity").get())
        assertEquals("0", ModuleBaseFinder.tag)
    }

    @Test
    fun `SeedMismatch is grouped inside Evidence before Scoring Alerts and Render`() {
        val evidence = ModuleBaseFinder.group("Evidence")
        val defaults = mapOf(
            "Storage" to true,
            "Utilities" to true,
            "Automation" to true,
            "Entities" to true,
            "Structural" to true,
            "Geometry" to true,
            "Activity" to true,
            "ChunkTrails" to true,
        )
        defaults.forEach { (name, expected) ->
            assertEquals(expected, evidence.setting(name).get(), name)
        }
        val seedMismatch = evidence.group("SeedMismatch")
        assertEquals(true, seedMismatch.setting("Enabled").get())
        assertEquals("", seedMismatch.setting("WorldSeed").get())
        assertEquals(BaseFinderWorldBackend.FEATURES, seedMismatch.setting("Backend").get())
        assertEquals(12, seedMismatch.setting("ScanChunks").get())
        assertEquals(false, seedMismatch.setting("CompareMaterials").get())
        // Tuning knobs are hardcoded — keep the ClickGUI group lean.
        assertTrue(seedMismatch.inner.none { it.name == "ShowOutlines" })
        assertTrue(seedMismatch.inner.none { it.name == "ShowMismatches" })
        assertTrue(seedMismatch.inner.none { it.name == "FreezesPerTick" })
        assertTrue(seedMismatch.inner.none { it.name == "OverlayYRadius" })
        assertTrue(seedMismatch.inner.none { it.name == "WorkerThreads" })
        assertTrue("SeedCompare" in seedMismatch.aliases)

        assertTrue(ModuleBaseFinder.inner.none { it.name == "SeedMismatch" })
        assertTrue(ModuleBaseFinder.inner.none { it.name == "Performance" })

        val scoring = ModuleBaseFinder.group("Scoring")
        assertEquals(
            BaseFinderScoreGroup.entries.map(BaseFinderScoreGroup::settingName),
            scoring.inner.filterIsInstance<ValueGroup>().map(ValueGroup::name),
        )
        BaseFinderScoreWeight.entries.forEach { weight ->
            val section = scoring.group(weight.group.settingName)
            val setting = section.setting(weight.settingName) as RangedValue<*>
            assertEquals(weight.defaultValue, setting.get(), weight.persistedKey)
            assertEquals(weight.range, setting.range, weight.persistedKey)
        }
        assertEquals(ValueType.ACTION, scoring.setting("ResetToDefaults").type())

        val alerts = ModuleBaseFinder.group("Alerts")
        assertEquals(true, alerts.setting("Notifications").get())
        assertEquals(true, alerts.setting("ChatCoordinates").get())

        val rootOrder = ModuleBaseFinder.inner.map { it.name }
        assertTrue(rootOrder.indexOf("Evidence") < rootOrder.indexOf("Scoring"))
        assertTrue(rootOrder.indexOf("Scoring") < rootOrder.indexOf("Alerts"))
        assertTrue(rootOrder.indexOf("Alerts") < rootOrder.indexOf("Render"))
    }

    @Test
    fun `scoring matrix can be changed and reset to the complete default profile`() {
        val original = ModuleBaseFinder.Scoring.snapshot()
        val weight = BaseFinderScoreWeight.entries.first { it.range.first < it.range.last }
        val changedValue = if (weight.defaultValue < weight.range.last) {
            weight.defaultValue + 1
        } else {
            weight.defaultValue - 1
        }

        try {
            ModuleBaseFinder.Scoring.applyWeights(original.with(weight, changedValue))
            assertEquals(changedValue, ModuleBaseFinder.Scoring.snapshot()[weight])

            ModuleBaseFinder.Scoring.resetToDefaults()

            assertEquals(BaseFinderScoringWeights.DEFAULT, ModuleBaseFinder.Scoring.snapshot())
        } finally {
            ModuleBaseFinder.Scoring.applyWeights(original)
        }
    }

    @Test
    fun `server settings binding restores seed and scoring independently per server`(@TempDir temp: Path) {
        val store = BaseFinderServerSettingsStore(temp)
        val weight = BaseFinderScoreWeight.entries.first { it.range.first < it.range.last }
        val changedValue = (weight.defaultValue + 1).coerceAtMost(weight.range.last)
        var current = BaseFinderServerSettings(
            worldSeed = "legacy-seed",
            scoringWeights = BaseFinderScoringWeights.DEFAULT.with(weight, changedValue),
        )
        val binding = BaseFinderServerSettingsBinding(
            store = store,
            snapshot = { current },
            apply = { current = it },
        )

        binding.bind("example.org")
        assertEquals("legacy-seed", current.worldSeed)
        assertEquals(changedValue, current.scoringWeights[weight])

        current = BaseFinderServerSettings("server-a-seed", BaseFinderScoringWeights.DEFAULT)
        binding.changed()
        binding.bind("other.example.org")
        assertEquals(BaseFinderServerSettings(), current)

        current = BaseFinderServerSettings("server-b-seed", BaseFinderScoringWeights.DEFAULT.with(weight, changedValue))
        binding.changed()
        binding.bind("example.org")
        assertEquals(BaseFinderServerSettings("server-a-seed", BaseFinderScoringWeights.DEFAULT), current)

        binding.bind("other.example.org")
        assertEquals("server-b-seed", current.worldSeed)
        assertEquals(changedValue, current.scoringWeights[weight])
    }

    @Test
    fun `server settings key distinguishes servers and singleplayer worlds without dimensions`() {
        assertEquals(
            "play.example.org",
            baseFinderServerSettingsKey("play.example.org", null, null),
        )
        assertEquals(
            "singleplayer:Survival:1234",
            baseFinderServerSettingsKey(null, "Survival", 1234L),
        )
        assertEquals(
            "singleplayer:Survival",
            baseFinderServerSettingsKey(null, "Survival", null),
        )
    }

    @Test
    fun `seed mismatch block overlay requires BaseFinder SeedMismatch and Debug together`() {
        assertTrue(seedMismatchOverlayEnabled(baseFinder = true, seedMismatch = true, debug = true))
        assertFalse(seedMismatchOverlayEnabled(baseFinder = false, seedMismatch = true, debug = true))
        assertFalse(seedMismatchOverlayEnabled(baseFinder = true, seedMismatch = false, debug = true))
        assertFalse(seedMismatchOverlayEnabled(baseFinder = true, seedMismatch = true, debug = false))
    }

    @Test
    fun `player ring is reserved from sparse work only while the debug overlay owns it`() {
        val player = ChunkCoordinate(10, -4)

        assertFalse(
            seedMismatchSparseChunkReserved(
                chunk = player,
                playerChunk = player,
                scanRadius = 12,
                overlayActive = false,
            ),
        )
        assertTrue(
            seedMismatchSparseChunkReserved(
                chunk = ChunkCoordinate(12, -5),
                playerChunk = player,
                scanRadius = 12,
                overlayActive = true,
            ),
        )
        assertFalse(
            seedMismatchSparseChunkReserved(
                chunk = ChunkCoordinate(23, -4),
                playerChunk = player,
                scanRadius = 12,
                overlayActive = true,
            ),
        )
    }

    @Test
    fun `seed retention keeps sparse snapshot chunks outside the overlay ring`() {
        val overlayChunk = ChunkCoordinate(0, 0)
        val sparseChunk = ChunkCoordinate(12, -7)

        val retained = seedCompareRetentionChunks(
            scanTargets = listOf(overlayChunk),
            snapshots = listOf(ChunkEvidenceSnapshot(sparseChunk)),
        )

        assertEquals(setOf(overlayChunk, sparseChunk), retained)
    }

    @Test
    fun `sparse audit rotates deterministically beyond its first window`() {
        val chunks = (0..4).map { ChunkCoordinate(it, -it) }
        val snapshots = chunks.map(::ChunkEvidenceSnapshot)

        val first = selectSparseCompareCandidates(
            snapshots = snapshots,
            priorityChunks = setOf(chunks.last()),
            auditOffset = 0,
            auditLimit = 2,
        )
        val second = selectSparseCompareCandidates(
            snapshots = snapshots,
            priorityChunks = setOf(chunks.last()),
            auditOffset = 2,
            auditLimit = 2,
        )

        assertEquals(listOf(chunks.last(), chunks[0], chunks[1]), first)
        assertEquals(listOf(chunks.last(), chunks[2], chunks[3]), second)
    }

}
