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
package net.ccbluex.liquidbounce.integration.theme.component.components.seedcracker

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CandidateSource
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCandidate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCandidateKind
import net.ccbluex.liquidbounce.utils.render.Alignment

/** Pure defaults kept separate so layout behavior can be tested without constructing the Minecraft HUD. */
internal object SeedCrackerHudLayout {
    const val WIDTH = 210f
    const val HEIGHT = 54f
    const val LAST_LINE_BOTTOM = 46.3f
    val HORIZONTAL_ALIGNMENT = Alignment.ScreenAxisX.LEFT
    const val HORIZONTAL_OFFSET = 16
    val VERTICAL_ALIGNMENT = Alignment.ScreenAxisY.BOTTOM
    const val VERTICAL_OFFSET = 16
}

internal enum class SeedCrackerHudLineRole(val scaleMultiplier: Float) {
    TITLE(1.0F),
    METRIC(0.92F),
    RESULT(0.92F),
    ACTION(0.92F),
}

internal data class SeedCrackerHudCandidateLinePlan(
    val role: SeedCrackerHudLineRole,
    val translationKey: String,
    val arguments: List<String> = emptyList(),
)

internal fun seedCrackerHudCandidateLinePlan(candidate: SeedCandidate): List<SeedCrackerHudCandidateLinePlan> {
    val seedKey = when (candidate.kind) {
        SeedCandidateKind.WORLD_SEED -> "overlay.candidate.worldSeed"
        SeedCandidateKind.STRUCTURE_SEED_48 -> "overlay.candidate.structureSeed"
        SeedCandidateKind.NETHER_PATTERN_SEED_48 -> "overlay.candidate.netherPatternSeed"
    }
    val detailKey = when {
        candidate.kind != SeedCandidateKind.WORLD_SEED -> "overlay.candidate.needsWorldProof"
        !candidate.isVerified -> "overlay.candidate.unverified"
        candidate.source == CandidateSource.NETHER_BEDROCK -> "overlay.candidate.verified.netherBedrock"
        else -> "overlay.candidate.verified.structures"
    }
    return listOf(
        SeedCrackerHudCandidateLinePlan(SeedCrackerHudLineRole.RESULT, seedKey, listOf(candidate.seed.toString())),
        SeedCrackerHudCandidateLinePlan(SeedCrackerHudLineRole.ACTION, detailKey),
    )
}

internal data class SeedCrackerHudProgressGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(right >= left) { "Progress track width must not be negative" }
        require(bottom >= top) { "Progress track height must not be negative" }
    }

    fun fillRight(progress: Float): Float = left + (right - left) * progress.coerceIn(0.0F, 1.0F)
}

internal fun seedCrackerHudProgressGeometry(width: Float, height: Float): SeedCrackerHudProgressGeometry {
    require(width >= PROGRESS_HORIZONTAL_INSET * 2.0F) { "SeedCracker HUD is too narrow for progress" }
    require(height >= PROGRESS_HEIGHT + PROGRESS_BOTTOM_INSET) { "SeedCracker HUD is too short for progress" }
    return SeedCrackerHudProgressGeometry(
        left = PROGRESS_HORIZONTAL_INSET,
        top = height - PROGRESS_HEIGHT - PROGRESS_BOTTOM_INSET,
        right = width - PROGRESS_HORIZONTAL_INSET,
        bottom = height - PROGRESS_BOTTOM_INSET,
    )
}

internal fun normalizedSeedCrackerHudProgress(value: Double, maximum: Double): Float {
    if (!value.isFinite() || !maximum.isFinite() || maximum <= 0.0) return 0.0F
    return (value / maximum).coerceIn(0.0, 1.0).toFloat()
}

private const val PROGRESS_HORIZONTAL_INSET = 7.0F
private const val PROGRESS_HEIGHT = 2.0F
private const val PROGRESS_BOTTOM_INSET = 3.0F
