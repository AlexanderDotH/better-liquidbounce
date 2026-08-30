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
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseFinderPresentationConfigurationTest {
    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
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
            val translations = readBaseFinderLocale(locale)
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
        assertEquals(4, BaseFinderRenderSettings.FixedBox.boxRadius)
        assertEquals(6, BaseFinderRenderSettings.FixedBox.boxHeight)
        assertEquals(1, BaseFinderRenderSettings.DynamicBox.dynamicPadding)
        assertEquals(Color4b(255, 186, 32), render.setting("LowConfidenceColor").get())
        assertEquals(Color4b(255, 60, 180), render.setting("HighConfidenceColor").get())
        assertTrue("GlowBox" in render.aliases)
        assertTrue(render.inner.none { it.name == "Pulse" })
        assertTrue(render.inner.none { it.name == "Radius" })
        assertTrue(render.inner.none { it.name == "Softness" })
        assertTrue(render.inner.none { it.name == "BoxRadius" })
        assertTrue(render.inner.none { it.name == "BoxHeight" })
        assertTrue(render.inner.none { it.name == "DynamicPadding" })
        assertEquals("Glow", ModuleBaseFinder.Render.mode.activeMode.name)

        val glow = BaseFinderRenderSettings.Glow
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

        assertTrue(original != baseFinderEvidenceFingerprint(snapshots, 1, true, enabled, BaseFinderScoringWeights.DEFAULT))
        assertTrue(original != baseFinderEvidenceFingerprint(snapshots, 0, false, enabled, BaseFinderScoringWeights.DEFAULT))
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
}
