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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model

/** One transparent, persistable part of a BaseFinder family score. */
internal data class ScoreContribution(
    val key: String,
    val score: Int,
    val observations: Int? = null,
) {
    init {
        require(key.isNotBlank()) { "Score contribution key must not be blank" }
        require(observations == null || observations >= 0) { "Observation count must be non-negative" }
    }
}

/** Pure SeedMismatch score plus the policy facts needed by cluster acceptance. */
internal data class SeedMismatchScoreAssessment(
    val subtotal: Int,
    val contributions: List<ScoreContribution>,
    val standaloneEligible: Boolean,
    val denseFeatures: Boolean,
) {
    init {
        require(subtotal >= 0) { "SeedMismatch subtotal must be non-negative" }
        require(contributions.sumOf(ScoreContribution::score) == subtotal) {
            "SeedMismatch contributions must reconcile with the subtotal"
        }
        require(!standaloneEligible || denseFeatures) {
            "Standalone SeedMismatch evidence requires a dense FEATURES comparison"
        }
    }
}

/** Immutable scoring profile for one connected seed-mismatch component. */
internal data class SeedMismatchClusterProfile(
    val unexpectedSolidCount: Int = 0,
    val missingSolidCount: Int = 0,
    val utilityMismatchCount: Int = 0,
    val cellCount: Int = 0,
    val horizontalColumnCount: Int = 0,
    val bounds: BaseFinderBounds? = null,
    val anchor: BaseCoordinate? = null,
    val anchors: List<EvidenceAnchor> = emptyList(),
) {
    val weightedMass: Int
        get() = utilityMismatchCount * UTILITY_WEIGHT +
            unexpectedSolidCount * UNEXPECTED_SOLID_WEIGHT +
            missingSolidCount * MISSING_SOLID_WEIGHT

    private companion object {
        const val UTILITY_WEIGHT = 4
        const val UNEXPECTED_SOLID_WEIGHT = 2
        const val MISSING_SOLID_WEIGHT = 1
    }
}
