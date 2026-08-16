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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal data class SpearKillRouteTargetPrediction(
    val observedPosition: Vec3,
    val position: Vec3,
    val eyePosition: Vec3,
    val boundingBox: AABB,
)

/** Complete target input captured immediately before Direct or A* route calculation. */
internal data class SpearKillRouteTargetSnapshot(
    val observedPosition: Vec3,
    val eyeOffset: Vec3,
    val boundingBox: AABB,
    val velocity: Vec3,
    val predictedPositions: List<Vec3>,
) {
    init {
        require(predictedPositions.isNotEmpty()) { "Target prediction needs its observed position" }
    }

    fun predict(ticks: Int): SpearKillRouteTargetPrediction {
        val predictionIndex = ticks.coerceIn(0, predictedPositions.lastIndex)
        val predictedPosition = predictedPositions[predictionIndex]
            .takeIf(Vec3::hasFiniteSpearKillRouteTargetCoordinates)
            ?: observedPosition
        val predictionOffset = predictedPosition.subtract(observedPosition)
        return SpearKillRouteTargetPrediction(
            observedPosition = observedPosition,
            position = predictedPosition,
            eyePosition = observedPosition.add(eyeOffset).add(predictionOffset),
            boundingBox = boundingBox.move(predictionOffset),
        )
    }

    fun collisionCorridorPositions(
        maximumSamples: Int = SPEAR_KILL_TARGET_SNAPSHOT_CORRIDOR_SAMPLES,
    ): List<Vec3> {
        require(maximumSamples >= 2) { "Collision corridor needs both prediction endpoints" }
        if (predictedPositions.size <= maximumSamples) return predictedPositions

        return (0 until maximumSamples).map { sample ->
            val predictionIndex = sample.toLong() * predictedPositions.lastIndex / (maximumSamples - 1)
            predictedPositions[predictionIndex.toInt()]
        }
    }
}

/** Must be invoked on the Minecraft thread; the returned value has no live-entity references. */
internal fun captureSpearKillRouteTargetSnapshot(
    target: LivingEntity,
    predictionTicks: Int = SPEAR_KILL_DEFAULT_TARGET_SNAPSHOT_TICKS,
): SpearKillRouteTargetSnapshot {
    val observedPosition = target.position()
    val extrapolation = PositionExtrapolation.getBestForEntity(target)
    val snapshotTicks = spearKillTargetSnapshotTicks(predictionTicks)
    return SpearKillRouteTargetSnapshot(
        observedPosition = observedPosition,
        eyeOffset = target.eyePosition.subtract(observedPosition),
        boundingBox = target.boundingBox,
        velocity = observedPosition.subtract(target.lastPos),
        predictedPositions = (0..snapshotTicks).map { tick ->
            extrapolation.getPositionInTicks(tick.toDouble())
        },
    )
}

internal fun spearKillTargetSnapshotTicks(estimatedHitTicks: Int): Int = estimatedHitTicks.coerceIn(
    SPEAR_KILL_DEFAULT_TARGET_SNAPSHOT_TICKS,
    SPEAR_KILL_MAX_TARGET_SNAPSHOT_TICKS,
)

private fun Vec3.hasFiniteSpearKillRouteTargetCoordinates(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_DEFAULT_TARGET_SNAPSHOT_TICKS = 30
private const val SPEAR_KILL_MAX_TARGET_SNAPSHOT_TICKS = 512
private const val SPEAR_KILL_TARGET_SNAPSHOT_CORRIDOR_SAMPLES = 31
