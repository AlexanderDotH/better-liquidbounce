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
@file:JvmName("FaceTargetPositionFactoryKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.block.planner

import net.ccbluex.liquidbounce.common.debug.DebugParameterSink
import net.ccbluex.liquidbounce.features.block.config.PositionFactoryConfiguration
import net.ccbluex.liquidbounce.features.block.contract.FaceTargetPositionFactory
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.math.yaw
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.ccbluex.liquidbounce.utils.entity.anyHorizontal
import net.ccbluex.liquidbounce.utils.math.geometry.AlignedFace
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.ccbluex.liquidbounce.utils.math.geometry.NormalizedPlane
import net.ccbluex.liquidbounce.utils.math.minus
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

abstract class BaseYawTargetPositionFactory(
    protected val config: PositionFactoryConfiguration,
    private val yawTolerance: Float = 5f
) : FaceTargetPositionFactory() {

    override fun producePositionOnFace(face: AlignedFace, targetPos: BlockPos): Vec3 {
        DebugParameterSink.publish(PositionFactoryDebug, "TargetPos") { targetPos }
        val trimmedFace = trimFace(face)

        // If the player is not moving, we can just aim at the nearest point
        return if (!player.input.keyPresses.anyHorizontal) {
            aimAtNearestPointToRotationLine(targetPos, trimmedFace)
        } else {
            aimAtNearestPointToYaw(targetPos, trimmedFace) ?: aimAtNearestPointToRotationLine(targetPos, trimmedFace)
        }
    }

    protected fun aimAtNearestPointToRotationLine(
        targetPos: BlockPos,
        face: AlignedFace
    ) = NearestRotationTargetPositionFactory(config).aimAtNearestPointToRotationLine(targetPos, face)

    protected fun aimAtNearestPointToYaw(
        targetPos: BlockPos,
        face: AlignedFace
    ): Vec3? {
        if (Mth.equal(face.area, 0.0)) {
            DebugParameterSink.publish(PositionFactoryDebug, "FaceArea") { face.area }
            DebugParameterSink.publish(PositionFactoryDebug, "ReturnedPoint") { face.from }
            return face.from
        }

        val targetYaws = targetYaws()
        val lineSegments = findYawLineSegments(face, targetPos, targetYaws)
        if (lineSegments.high == null && lineSegments.low == null) {
            return null
        }

        val points = findClosestYawPoints(lineSegments, targetYaws)
        val candidates = yawTargetCandidates(points, targetYaws)
        val result = selectYawTarget(candidates.high, candidates.low, yawTolerance)

        DebugParameterSink.publish(PositionFactoryDebug, "ReturnedPoint") { result }
        return result
    }

    private fun targetYaws(): YawTargets {
        val yaw = Mth.wrapDegrees(player.yRot)
        val angle = getAngle()
        val targetYaws = YawTargets(
            high = Mth.wrapDegrees(yaw + angle),
            low = Mth.wrapDegrees(yaw - angle),
        )

        DebugParameterSink.publish(PositionFactoryDebug, "PlayerYaw") { yaw }
        DebugParameterSink.publish(PositionFactoryDebug, "Angle") { angle }
        DebugParameterSink.publish(PositionFactoryDebug, "HighTargetYaw") { targetYaws.high }
        DebugParameterSink.publish(PositionFactoryDebug, "LowTargetYaw") { targetYaws.low }
        return targetYaws
    }

    private fun findYawLineSegments(
        face: AlignedFace,
        targetPos: BlockPos,
        targetYaws: YawTargets,
    ): YawLineSegments {
        val lineSegments = YawLineSegments(
            high = findYawLineSegment(face, targetPos, targetYaws.high),
            low = findYawLineSegment(face, targetPos, targetYaws.low),
        )

        DebugParameterSink.publish(PositionFactoryDebug, "HighLineSegment") { lineSegments.high }
        DebugParameterSink.publish(PositionFactoryDebug, "LowLineSegment") { lineSegments.low }
        return lineSegments
    }

    private fun findClosestYawPoints(
        lineSegments: YawLineSegments,
        targetYaws: YawTargets,
    ): YawTargetPoints {
        val points = YawTargetPoints(
            high = closestPoint(lineSegments.high, targetYaws.high),
            low = closestPoint(lineSegments.low, targetYaws.low),
        )

        DebugParameterSink.publish(PositionFactoryDebug, "HighClosestPoint") { points.high }
        DebugParameterSink.publish(PositionFactoryDebug, "LowClosestPoint") { points.low }
        return points
    }

    private fun yawTargetCandidates(
        points: YawTargetPoints,
        targetYaws: YawTargets,
    ): YawTargetCandidates {
        val candidates = YawTargetCandidates(
            high = YawTargetCandidate(points.high, calculateYawTolerance(points.high, targetYaws.high)),
            low = YawTargetCandidate(points.low, calculateYawTolerance(points.low, targetYaws.low)),
        )

        DebugParameterSink.publish(PositionFactoryDebug, "HighTolerance") { candidates.high.tolerance }
        DebugParameterSink.publish(PositionFactoryDebug, "LowTolerance") { candidates.low.tolerance }
        return candidates
    }

    private fun calculateYawTolerance(point: Vec3?, targetYaw: Float): Float =
        point?.let { calculateYawDifference(it, targetYaw) } ?: Float.MAX_VALUE

    private fun findYawLineSegment(face: AlignedFace, targetPos: BlockPos, targetYaw: Float): LineSegment? {
        val plane = NormalizedPlane.fromParams(
            config.eyePos - targetPos,
            Vec3.Z_AXIS.yRot(targetYaw.toRadians()),
            Vec3.Y_AXIS,
        )
        val intersection = face.toPlane().intersection(plane)
        return runCatching { intersection?.let(face::coerceInFace) }.getOrNull()
    }

    private fun closestPoint(lineSegment: LineSegment?, targetYaw: Float): Vec3? =
        lineSegment?.let { findClosestPointToYaw(it, targetYaw) }

    private fun findClosestPointToYaw(lineSegment: LineSegment, targetYaw: Float): Vec3 {
        val start = lineSegment.start
        val end = lineSegment.end
        val segmentDelta = end.subtract(start)

        val startYaw = calculateYaw(start)
        val endYaw = calculateYaw(end)
        val yawDiff = Mth.wrapDegrees(endYaw - startYaw)
        val targetYawDiff = Mth.wrapDegrees(targetYaw - startYaw)
        val t = if (yawDiff != 0f) targetYawDiff / yawDiff else 0f
        return start.add(segmentDelta.scale(t.toDouble().coerceIn(0.0, 1.0)))
    }

    private fun calculateYaw(point: Vec3): Float {
        return point.subtract(config.eyePos).yaw
    }

    private fun calculateYawDifference(point: Vec3, targetYaw: Float): Float {
        val pointYaw = calculateYaw(point)
        return abs(Mth.wrapDegrees(pointYaw - targetYaw))
    }

    private data class YawTargets(
        val high: Float,
        val low: Float,
    )

    private data class YawLineSegments(
        val high: LineSegment?,
        val low: LineSegment?,
    )

    private data class YawTargetPoints(
        val high: Vec3?,
        val low: Vec3?,
    )

    private data class YawTargetCandidates(
        val high: YawTargetCandidate,
        val low: YawTargetCandidate,
    )

    protected abstract fun getAngle(): Float
}

class ReverseYawTargetPositionFactory(config: PositionFactoryConfiguration) : BaseYawTargetPositionFactory(config) {
    override fun getAngle() = 180f // 180 degrees
}

class DiagonalYawTargetPositionFactory(config: PositionFactoryConfiguration) : BaseYawTargetPositionFactory(config) {
    override fun getAngle() = 75f // 75 degrees
}

class AngleYawTargetPositionFactory(config: PositionFactoryConfiguration) : BaseYawTargetPositionFactory(config) {
    override fun getAngle() = 45f // 45 degrees
}
