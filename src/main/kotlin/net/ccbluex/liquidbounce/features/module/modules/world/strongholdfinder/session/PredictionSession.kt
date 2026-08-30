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
package net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.session

import net.ccbluex.liquidbounce.utils.world.stronghold.EyeMeasurement
import net.ccbluex.liquidbounce.utils.world.stronghold.PosteriorSnapshot
import net.ccbluex.liquidbounce.utils.world.stronghold.StrongholdBayesianEstimator
import net.ccbluex.liquidbounce.utils.world.stronghold.StrongholdHypothesis
import net.ccbluex.liquidbounce.utils.world.stronghold.StrongholdHypothesisGenerator
import net.minecraft.world.level.ChunkPos

@JvmRecord
internal data class StrongholdPredictionSettings(
    val hypothesisCount: Int,
    val sigma: Float,
    val requireSameStrongholdAcrossThrows: Boolean,
    val showTopCandidates: Int,
    val announcePrediction: Boolean,
)

@JvmRecord
internal data class StrongholdPredictionAnnouncement(
    val chunkPos: ChunkPos,
    val probability: Double,
)

internal class StrongholdPredictionSession {

    private val capturedMeasurements = mutableListOf<EyeMeasurement>()
    private var posterior: PosteriorSnapshot? = null
    private var lastAnnouncedCandidate: ChunkPos? = null
    private var hypothesisCache: List<StrongholdHypothesis> = emptyList()
    private var cachedHypothesisCount = -1

    val measurements: List<EyeMeasurement>
        get() = capturedMeasurements

    val snapshot: PosteriorSnapshot?
        get() = posterior

    val sampleCount: Int
        get() = capturedMeasurements.size

    fun record(measurement: EyeMeasurement) {
        capturedMeasurements += measurement
    }

    fun recompute(
        settings: StrongholdPredictionSettings,
        announce: Boolean,
    ): StrongholdPredictionAnnouncement? {
        posterior = StrongholdBayesianEstimator.estimate(
            measurements = capturedMeasurements,
            hypotheses = getOrCreateHypotheses(settings.hypothesisCount),
            sigmaDeg = settings.sigma.toDouble(),
            requireSameStrongholdAcrossThrows = settings.requireSameStrongholdAcrossThrows,
            topCandidates = settings.showTopCandidates,
        )

        val best = posterior?.candidates?.firstOrNull() ?: return null
        val bestChunk = best.chunkPos
        if (!settings.announcePrediction || !announce || bestChunk == lastAnnouncedCandidate) {
            return null
        }

        return StrongholdPredictionAnnouncement(bestChunk, best.probability)
    }

    fun markAnnounced(chunkPos: ChunkPos) {
        lastAnnouncedCandidate = chunkPos
    }

    fun invalidateHypotheses() {
        cachedHypothesisCount = -1
    }

    fun hasMeasurements(): Boolean = capturedMeasurements.isNotEmpty()

    fun clear() {
        capturedMeasurements.clear()
        posterior = null
        lastAnnouncedCandidate = null
    }

    private fun getOrCreateHypotheses(hypothesisCount: Int): List<StrongholdHypothesis> {
        if (cachedHypothesisCount != hypothesisCount || hypothesisCache.isEmpty()) {
            hypothesisCache = StrongholdHypothesisGenerator.generate(hypothesisCount)
            cachedHypothesisCount = hypothesisCount
        }
        return hypothesisCache
    }
}
