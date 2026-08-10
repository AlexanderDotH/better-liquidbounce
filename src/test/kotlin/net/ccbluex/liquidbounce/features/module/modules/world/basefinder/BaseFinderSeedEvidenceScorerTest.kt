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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseFinderSeedEvidenceScorerTest {

    @Test
    fun `unexpected solids follow every score-band boundary`() {
        assertBands(
            key = "seed_mismatch.unexpected_solid",
            observationsToScore = mapOf(
                0 to 0,
                3 to 0,
                4 to 8,
                7 to 8,
                8 to 16,
                15 to 16,
                16 to 24,
                31 to 24,
                32 to 32,
                63 to 32,
                64 to 40,
            ),
        ) { count -> profile(unexpected = count, missing = if (count == 0) 1 else 0) }
    }

    @Test
    fun `missing solids follow every score-band boundary`() {
        assertBands(
            key = "seed_mismatch.missing_solid",
            observationsToScore = mapOf(
                0 to 0,
                7 to 0,
                8 to 5,
                15 to 5,
                16 to 10,
                31 to 10,
                32 to 15,
                63 to 15,
                64 to 20,
                127 to 20,
                128 to 25,
            ),
        ) { count -> profile(unexpected = if (count == 0) 1 else 0, missing = count) }
    }

    @Test
    fun `utility mismatches remain diagnostic and never score`() {
        assertBands(
            key = "seed_mismatch.utility_mismatch",
            observationsToScore = mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0),
        ) { count -> profile(unexpected = if (count == 0) 1 else 0, utility = count) }
    }

    @Test
    fun `component size remains a standalone gate and never scores`() {
        assertBands(
            key = "seed_mismatch.component_size",
            observationsToScore = mapOf(1 to 0, 7 to 0, 8 to 0, 15 to 0, 16 to 0, 31 to 0, 32 to 0),
        ) { count -> profile(unexpected = count) }
    }

    @Test
    fun `horizontal spread remains a standalone gate and never scores`() {
        assertBands(
            key = "seed_mismatch.horizontal_spread",
            observationsToScore = mapOf(1 to 0, 3 to 0, 4 to 0, 7 to 0, 8 to 0),
        ) { columns -> profile(unexpected = columns, columns = columns) }
    }

    @Test
    fun `dense FEATURES scores only unexpected and missing solids`() {
        val assessment = assess(
            profile(unexpected = 64, missing = 128, utility = 4, columns = 8),
        )

        assertEquals(65, assessment.subtotal)
        assertEquals(0, assessment.contribution("seed_mismatch.utility_mismatch").score)
        assertEquals(0, assessment.contribution("seed_mismatch.component_size").score)
        assertEquals(0, assessment.contribution("seed_mismatch.horizontal_spread").score)
        assertTrue(assessment.contributions.none { it.key.endsWith("_cap") })
        assertEquals(assessment.subtotal, assessment.contributions.sumOf(ScoreContribution::score))
        assertTrue(assessment.denseFeatures)
    }

    @Test
    fun `terrain only FEATURES scoring also tops out at sixty-five`() {
        val assessment = assess(
            profile(unexpected = 64, missing = 128, columns = 8),
        )

        assertEquals(65, assessment.subtotal)
        assertTrue(assessment.contributions.none { it.key.endsWith("_cap") })
        assertEquals(assessment.subtotal, assessment.contributions.sumOf(ScoreContribution::score))
    }

    @Test
    fun `BASE_COLUMN scales dense score proportionally to a maximum of twenty`() {
        val maximum = assess(
            profile(unexpected = 64, missing = 128, utility = 4, columns = 8),
            fidelity = ExpectedTerrainFidelity.BASE_COLUMN,
        )
        val intermediate = assess(
            profile(unexpected = 16, columns = 4),
            fidelity = ExpectedTerrainFidelity.BASE_COLUMN,
        )

        assertEquals(20, maximum.subtotal)
        assertEquals(-45, maximum.contribution("seed_mismatch.base_column_reliability").score)
        assertEquals(7, intermediate.subtotal)
        assertEquals(-17, intermediate.contribution("seed_mismatch.base_column_reliability").score)
        assertEquals(maximum.subtotal, maximum.contributions.sumOf(ScoreContribution::score))
        assertEquals(intermediate.subtotal, intermediate.contributions.sumOf(ScoreContribution::score))
        assertFalse(maximum.standaloneEligible)
        assertFalse(maximum.denseFeatures)
    }

    @Test
    fun `SPARSE and NONE are diagnostic-only and contribute zero`() {
        val strongest = profile(unexpected = 64, missing = 128, utility = 4, columns = 8)

        listOf(SeedComparePhase.NONE, SeedComparePhase.SPARSE).forEach { phase ->
            val assessment = assess(strongest, phase = phase)

            assertEquals(0, assessment.subtotal, phase.name)
            assertTrue(assessment.contributions.isEmpty(), phase.name)
            assertFalse(assessment.standaloneEligible, phase.name)
            assertFalse(assessment.denseFeatures, phase.name)
        }
    }

    @Test
    fun `a sufficiently strong concrete mismatch can independently seed a finding`() {
        val overlay = assess(profile(unexpected = 64, columns = 4))
        val full = assess(profile(unexpected = 64, columns = 4), phase = SeedComparePhase.FULL)

        assertEquals(40, overlay.subtotal)
        assertTrue(overlay.standaloneEligible)
        assertTrue(full.standaloneEligible)
    }

    @Test
    fun `coherence without enough unexpected or missing points remains support only`() {
        val assessment = assess(profile(unexpected = 16, columns = 4))

        assertEquals(24, assessment.subtotal)
        assertFalse(assessment.standaloneEligible)
    }

    @Test
    fun `each standalone gate rejects otherwise strong evidence`() {
        val tooSmall = assess(profile(unexpected = 11, utility = 4, columns = 4))
        val tooNarrow = assess(profile(unexpected = 12, utility = 4, columns = 3))
        val tooWeak = assess(profile(missing = 16, columns = 4))
        val sparse = assess(
            profile(unexpected = 64, columns = 8),
            phase = SeedComparePhase.SPARSE,
        )
        val baseColumn = assess(
            profile(unexpected = 64, columns = 8),
            fidelity = ExpectedTerrainFidelity.BASE_COLUMN,
        )

        assertFalse(tooSmall.standaloneEligible)
        assertFalse(tooNarrow.standaloneEligible)
        assertFalse(tooWeak.standaloneEligible)
        assertFalse(sparse.standaloneEligible)
        assertFalse(baseColumn.standaloneEligible)
    }

    @Test
    fun `one changed block cannot independently seed a finding`() {
        val profiles = listOf(
            profile(unexpected = 1),
            profile(missing = 1),
            profile(utility = 1),
        )

        assertEquals(listOf(0, 0, 0), profiles.map { assess(it).subtotal })
        assertTrue(profiles.none { assess(it).standaloneEligible })
    }

    @Test
    fun `material swaps have no score contribution`() {
        val assessment = assess(profile(unexpected = 16, columns = 4))

        assertTrue(assessment.contributions.none { "material" in it.key })
    }

    @Test
    fun `a missing component produces the zero assessment`() {
        val assessment = BaseFinderSeedEvidenceScorer.assess(
            profile = null,
            phase = SeedComparePhase.OVERLAY,
            fidelity = ExpectedTerrainFidelity.FEATURES,
        )

        assertEquals(0, assessment.subtotal)
        assertTrue(assessment.contributions.isEmpty())
        assertFalse(assessment.standaloneEligible)
        assertTrue(assessment.denseFeatures)
    }

    private fun assertBands(
        key: String,
        observationsToScore: Map<Int, Int>,
        profileFactory: (Int) -> SeedMismatchClusterProfile,
    ) {
        observationsToScore.forEach { (observations, expectedScore) ->
            val contribution = assess(profileFactory(observations)).contribution(key)

            assertEquals(expectedScore, contribution.score, "$key at $observations observations")
            assertEquals(observations, contribution.observations, key)
        }
    }

    private fun assess(
        profile: SeedMismatchClusterProfile,
        phase: SeedComparePhase = SeedComparePhase.OVERLAY,
        fidelity: ExpectedTerrainFidelity = ExpectedTerrainFidelity.FEATURES,
    ) = BaseFinderSeedEvidenceScorer.assess(profile, phase, fidelity)

    private fun SeedMismatchScoreAssessment.contribution(key: String): ScoreContribution =
        contributions.single { it.key == key }

    private fun profile(
        unexpected: Int = 0,
        missing: Int = 0,
        utility: Int = 0,
        columns: Int = 1,
    ): SeedMismatchClusterProfile {
        val cellCount = unexpected + missing + utility
        return SeedMismatchClusterProfile(
            unexpectedSolidCount = unexpected,
            missingSolidCount = missing,
            utilityMismatchCount = utility,
            cellCount = cellCount,
            horizontalColumnCount = columns,
            bounds = BaseFinderBounds(
                minimum = BaseCoordinate(0, 64, 0),
                maximum = BaseCoordinate(maxOf(columns - 1, 0), 64, 0),
            ),
            anchors = listOf(EvidenceAnchor(BaseCoordinate(0, 64, 0), 6, "seed_mismatch.column")),
        )
    }
}
