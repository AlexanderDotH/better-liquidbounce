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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.InputStreamReader
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

    @Test
    fun `English and German locales cover scored SeedMismatch evidence`() {
        val requiredKeys = setOf(
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.unexpected_solid",
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.missing_solid",
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.utility_mismatch",
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.component_size",
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.horizontal_spread",
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.features_cap",
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.terrain_only_cap",
            "liquidbounce.module.baseFinder.messages.contribution.seed_mismatch.base_column_reliability",
            "liquidbounce.module.baseFinder.messages.breakdown.unavailable",
            "liquidbounce.module.baseFinder.messages.observation.block",
            "liquidbounce.module.baseFinder.messages.observation.blocks",
            "liquidbounce.module.baseFinder.messages.observation.cell",
            "liquidbounce.module.baseFinder.messages.observation.cells",
            "liquidbounce.module.baseFinder.messages.observation.column",
            "liquidbounce.module.baseFinder.messages.observation.columns",
            "liquidbounce.command.basefinder.subcommand.report.result.contribution.storage.block",
            "liquidbounce.command.basefinder.subcommand.report.result.contribution.storage.minecart_container",
            "liquidbounce.command.basefinder.subcommand.report.result.contribution.storage.minecart_furnace",
            "liquidbounce.command.basefinder.subcommand.report.result.contribution.family_cap",
            "liquidbounce.command.basefinder.subcommand.report.result.contribution.seed_mismatch.terrain_only_cap",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.block",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.blocks",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.cell",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.cells",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.column",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.columns",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.category",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.categories",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.point",
            "liquidbounce.command.basefinder.subcommand.report.result.observation.points",
        )

        listOf("en_us", "de_de").forEach { locale ->
            val translations = readLocale(locale)
            assertTrue(translations.keySet().containsAll(requiredKeys), locale)
            val listRow = translations["liquidbounce.command.basefinder.subcommand.list.result.row"].asString
            assertEquals(5, listRow.split("%s").size - 1, locale)
        }
    }

    @Test
    fun `family score labels can hide a carrier subtotal`() {
        assertEquals("Seed mismatch", baseFinderFamilyScoreLabel("Seed mismatch", 65, showScore = false))
        assertEquals("Compact base +32", baseFinderFamilyScoreLabel("Compact base", 32))
        assertFalse(BaseSignalFamily.SEED_MISMATCH.showFamilyScore)
        assertTrue(BaseSignalFamily.STORAGE.showFamilyScore)
    }

    @Test
    fun `debug readout exposes strongest component score and standalone eligibility`() {
        val profile = SeedMismatchClusterProfile(
            unexpectedSolidCount = 16,
            missingSolidCount = 8,
            cellCount = 24,
            horizontalColumnCount = 4,
        )

        val readout = seedMismatchDebugReadout(
            profile,
            SeedComparePhase.OVERLAY,
            ExpectedTerrainFidelity.FEATURES,
        )

        assertEquals("cells=24 cols=4 u=16 m=8 util=0", readout.component)
        assertEquals(29, readout.score)
        assertFalse(readout.standaloneEligible)

        val customized = seedMismatchDebugReadout(
            profile,
            SeedComparePhase.OVERLAY,
            ExpectedTerrainFidelity.FEATURES,
            BaseFinderScoringWeights.DEFAULT.with(BaseFinderScoreWeight.SEED_UNEXPECTED_16_TO_31, 31),
        )
        assertEquals(36, customized.score)
        assertTrue(customized.standaloneEligible)
    }

    @Test
    fun `render evidence projection keeps scored contributions and marks legacy detail unavailable`() {
        val enriched = baseFinderLabelEvidence(
            summary = EvidenceSummary(
                family = BaseSignalFamily.SEED_MISMATCH,
                score = 40,
                keys = listOf("seed_mismatch.column"),
                contributions = listOf(ScoreContribution("seed_mismatch.unexpected_solid", 40, 64)),
            ),
            family = "Seed mismatch",
            legacyUnavailable = "Unavailable",
            contributionLabel = { "label:$it" },
            observationText = { contribution -> contribution.observations?.let { "$it blocks" } },
        )

        assertEquals(emptyList<String>(), enriched.detections)
        assertEquals(
            listOf(BaseFinderLabelContribution("label:seed_mismatch.unexpected_solid", 40, "64 blocks")),
            enriched.contributions,
        )

        val legacy = baseFinderLabelEvidence(
            summary = EvidenceSummary(BaseSignalFamily.STORAGE, 12, listOf("storage.chest")),
            family = "Storage",
            legacyUnavailable = "Unavailable",
            contributionLabel = { it },
            observationText = { null },
        )

        assertEquals(listOf("Unavailable"), legacy.detections)
        assertEquals(emptyList<BaseFinderLabelContribution>(), legacy.contributions)
    }

    @Test
    fun `Render has Glow or Box mode labels colors and no Pulse`() {
        val render = ModuleBaseFinder.group("Render")

        assertEquals(true, render.setting("Enabled").get())
        assertEquals(512, render.setting("MaximumDistance").get())
        assertEquals(32, render.setting("RenderLimit").get())
        assertEquals("Fixed", ModuleBaseFinder.Render.boxMode.activeMode.name)
        assertEquals(BaseFinderBoxMode.FIXED, ModuleBaseFinder.Render.activeBoxMode)
        assertEquals(4, ModuleBaseFinder.Render.FixedBox.boxRadius)
        assertEquals(6, ModuleBaseFinder.Render.FixedBox.boxHeight)
        assertEquals(1, ModuleBaseFinder.Render.DynamicBox.dynamicPadding)
        assertEquals(Color4b(255, 186, 32), render.setting("LowConfidenceColor").get())
        assertEquals(Color4b(255, 60, 180), render.setting("HighConfidenceColor").get())
        assertTrue("GlowBox" in render.aliases)
        assertTrue(render.inner.none { it.name == "Pulse" })
        // ESP style lives under Mode/Glow only — not duplicated on the Render root.
        assertTrue(render.inner.none { it.name == "Radius" })
        assertTrue(render.inner.none { it.name == "Softness" })
        // Box size settings live under BoxMode choices, not on the Render root.
        assertTrue(render.inner.none { it.name == "BoxRadius" })
        assertTrue(render.inner.none { it.name == "BoxHeight" })
        assertTrue(render.inner.none { it.name == "DynamicPadding" })

        assertEquals("Glow", ModuleBaseFinder.Render.mode.activeMode.name)

        val glow = ModuleBaseFinder.Render.Glow
        assertEquals(14f, glow.setting("Radius").get())
        assertEquals(1f, glow.setting("Softness").get())
        assertEquals(1f, glow.setting("Intensity").get())
        assertEquals(1.25f, glow.setting("CoreSize").get())
        assertEquals(100, glow.setting("Opacity").get())

        val labels = render.group("Labels")
        assertEquals(true, labels.setting("ShowLabels").get())
        assertEquals(8, labels.setting("MaxLabels").get())
        assertEquals("", labels.setting("LabelText").get())
        assertEquals(1f, labels.setting("LabelScale").get())
        assertEquals(true, labels.setting("ShowEvidenceDetails").get())
        assertEquals(4, labels.setting("MaxEvidenceDetails").get())
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
        val weight = BaseFinderScoreWeight.entries.first { it.range.first < it.range.last }
        val changedWeights = BaseFinderScoringWeights.DEFAULT.with(
            weight,
            (weight.defaultValue + 1).coerceAtMost(weight.range.last),
        )

        val original = baseFinderEvidenceFingerprint(
            snapshots,
            minimumConfidence = 0,
            highSensitivity = true,
            enabledFamilies = enabled,
            scoringWeights = BaseFinderScoringWeights.DEFAULT,
        )

        assertTrue(
            original != baseFinderEvidenceFingerprint(
                snapshots,
                1,
                true,
                enabled,
                BaseFinderScoringWeights.DEFAULT,
            ),
        )
        assertTrue(
            original != baseFinderEvidenceFingerprint(
                snapshots,
                0,
                false,
                enabled,
                BaseFinderScoringWeights.DEFAULT,
            ),
        )
        assertTrue(
            original != baseFinderEvidenceFingerprint(
                snapshots,
                0,
                true,
                enabled - BaseSignalFamily.ACTIVITY,
                BaseFinderScoringWeights.DEFAULT,
            ),
        )
        assertTrue(original != baseFinderEvidenceFingerprint(snapshots, 0, true, enabled, changedWeights))
    }

    @Test
    fun `legacy default confidence migrates only when high sensitivity is absent`() {
        val legacyDefault = baseFinderConfig(minimumConfidence = 65)

        migrateLegacyBaseFinderSensitivity(legacyDefault)

        assertEquals(0, storedBaseFinderValue(legacyDefault, "MinimumConfidence").asInt)

        listOf(0, 1, 64, 66, 100).forEach { confidence ->
            val custom = baseFinderConfig(minimumConfidence = confidence)

            migrateLegacyBaseFinderSensitivity(custom)

            assertEquals(confidence, storedBaseFinderValue(custom, "MinimumConfidence").asInt)
        }

        val modern = baseFinderConfig(minimumConfidence = 65, highSensitivity = false)

        migrateLegacyBaseFinderSensitivity(modern)

        assertEquals(65, storedBaseFinderValue(modern, "MinimumConfidence").asInt)
    }

    @Test
    fun `flat legacy settings migrate into nested groups`() {
        val legacy = JsonObject().apply {
            addProperty("name", "BaseFinder")
            add("value", JsonArray().apply {
                add(storedBaseFinderValue("WorldSeed", "12345"))
                add(storedBaseFinderValue("Storage", false))
                add(storedBaseFinderValue("DirtyChunksPerTick", 3))
                add(storedBaseFinderValue("EntitySampleInterval", 12))
                add(storedBaseFinderValue("Notifications", false))
                add(storedBaseFinderValue("ChatCoordinates", true))
                add(JsonObject().apply {
                    addProperty("name", "SeedCompare")
                    add("value", JsonArray().apply {
                        add(storedBaseFinderValue("Enabled", true))
                        add(storedBaseFinderValue("ShowMismatches", true))
                    })
                })
            })
        }

        migrateBaseFinderGroupedSettings(legacy)

        assertEquals(
            "12345",
            nestedBaseFinderValue(legacy, "Evidence", "SeedMismatch", "WorldSeed").asString,
        )
        assertEquals(
            true,
            nestedBaseFinderValue(legacy, "Evidence", "SeedMismatch", "Enabled").asBoolean,
        )
        assertEquals(false, nestedBaseFinderValue(legacy, "Evidence", "Storage").asBoolean)
        assertEquals(false, nestedBaseFinderValue(legacy, "Alerts", "Notifications").asBoolean)
        assertEquals(true, nestedBaseFinderValue(legacy, "Alerts", "ChatCoordinates").asBoolean)

        val rootNames = legacy.getAsJsonArray("value").map { it.asJsonObject["name"].asString }
        assertTrue("SeedCompare" !in rootNames)
        assertTrue("WorldSeed" !in rootNames)
        assertTrue("Storage" !in rootNames)
        assertTrue("DirtyChunksPerTick" !in rootNames)
        assertTrue("Performance" !in rootNames)
        assertTrue("Notifications" !in rootNames)
        assertTrue("SeedMismatch" !in rootNames)
        val evidenceNames = storedBaseFinderValue(legacy, "Evidence").asJsonArray
            .map { it.asJsonObject["name"].asString }
        assertTrue("SeedMismatch" in evidenceNames)
    }

    @Test
    fun `evidence SeedMismatch boolean migrates only when group Enabled is absent`() {
        val legacy = JsonObject().apply {
            addProperty("name", "BaseFinder")
            add("value", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("name", "Evidence")
                    add("value", JsonArray().apply {
                        add(storedBaseFinderValue("SeedMismatch", false))
                        add(storedBaseFinderValue("Storage", true))
                    })
                })
            })
        }

        migrateBaseFinderGroupedSettings(legacy)

        assertEquals(
            false,
            nestedBaseFinderValue(legacy, "Evidence", "SeedMismatch", "Enabled").asBoolean,
        )
        val evidenceNames = storedBaseFinderValue(legacy, "Evidence").asJsonArray
            .map { it.asJsonObject["name"].asString }
        assertTrue("SeedMismatch" in evidenceNames)
        assertTrue("Storage" in evidenceNames)
    }

    private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name } as Value<*>

    private fun ValueGroup.group(name: String): ValueGroup = inner.single { it.name == name } as ValueGroup

    private fun baseFinderConfig(minimumConfidence: Int, highSensitivity: Boolean? = null) = JsonObject().apply {
        addProperty("name", "BaseFinder")
        add("value", JsonArray().apply {
            add(storedBaseFinderValue("MinimumConfidence", minimumConfidence))
            highSensitivity?.let { add(storedBaseFinderValue("HighSensitivity", it)) }
        })
    }

    private fun storedBaseFinderValue(name: String, value: Any) = JsonObject().apply {
        addProperty("name", name)
        when (value) {
            is Boolean -> addProperty("value", value)
            is Int -> addProperty("value", value)
            is String -> addProperty("value", value)
        }
    }

    private fun storedBaseFinderValue(config: JsonObject, name: String) = config.getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it["name"].asString == name }
        .get("value")

    private fun nestedBaseFinderValue(config: JsonObject, group: String, name: String) =
        storedBaseFinderValue(config, group).asJsonArray
            .map { it.asJsonObject }
            .single { it["name"].asString == name }
            .get("value")

    private fun nestedBaseFinderValue(
        config: JsonObject,
        parentGroup: String,
        group: String,
        name: String,
    ) = nestedBaseFinderValue(config, parentGroup, group).asJsonArray
        .map { it.asJsonObject }
        .single { it["name"].asString == name }
        .get("value")

    private fun readLocale(locale: String): JsonObject {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
        )
        return resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
    }
}
