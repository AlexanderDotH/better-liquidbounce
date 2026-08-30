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

import net.ccbluex.liquidbounce.common.debug.DebugGeometrySink
import net.ccbluex.liquidbounce.common.debug.DebuggedBox
import net.ccbluex.liquidbounce.common.debug.DebuggedLineSegment
import net.ccbluex.liquidbounce.common.debug.DebuggedOwner
import net.ccbluex.liquidbounce.common.debug.DebuggedPoint
import net.ccbluex.liquidbounce.features.block.config.PositionFactoryConfiguration
import net.ccbluex.liquidbounce.features.block.contract.FaceTargetPositionFactory
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.math.yaw
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.ccbluex.liquidbounce.utils.entity.anyHorizontal
import net.ccbluex.liquidbounce.utils.math.vertices
import net.ccbluex.liquidbounce.utils.math.geometry.AlignedFace
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.math.unaryMinus
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

internal object PositionFactoryDebug : DebuggedOwner

internal fun infiniteDebugLine(line: Line, argb: Int): DebuggedLineSegment {
    val normalizedDirection = line.direction.normalize()
    return DebuggedLineSegment(
        from = line.position.subtract(normalizedDirection.scale(100.0)),
        to = line.position.add(normalizedDirection.scale(100.0)),
        argb = argb,
    )
}

/**
 * Always targets the point with the nearest rotation angle to the current rotation angle
 */
class NearestRotationTargetPositionFactory(val config: PositionFactoryConfiguration) : FaceTargetPositionFactory() {
    override fun producePositionOnFace(face: AlignedFace, targetPos: BlockPos): Vec3 {
        val trimmedFace = trimFace(face)

        return aimAtNearestPointToRotationLine(targetPos, trimmedFace)
    }

    fun aimAtNearestPointToRotationLine(
        targetPos: BlockPos,
        face: AlignedFace
    ): Vec3 {
        if (Mth.equal(face.area, 0.0)) {
            return face.from
        }

        val currentRotation = RotationManager.serverRotation

        val rotationLine = Line(config.eyePos - targetPos, currentRotation.directionVector)

        val pointOnFace = face.nearestPointTo(rotationLine)

        DebugGeometrySink.publish(PositionFactoryDebug, "targetFace") {
            DebuggedBox(
                AABB(
                face.from,
                face.to
            ).move(targetPos), Color4b.RED.argb)
        }

        DebugGeometrySink.publish(PositionFactoryDebug, "targetPoint") {
            DebuggedPoint(
                pointOnFace + targetPos,
                Color4b.BLUE.argb,
                size = 0.05
            )
        }

        DebugGeometrySink.publish(PositionFactoryDebug, "daLine") {
            infiniteDebugLine(
                Line(
                    config.eyePos,
                    currentRotation.directionVector
                ), Color4b.BLUE.argb
            )
        }

        return pointOnFace
    }
}

/**
 * Always targets the point with the nearest rotation angle to the current rotation angle.
 * If you have questions, you have to ask @superblaubeere27 because I am too stupid to explain this without a picture.
 */
class StabilizedRotationTargetPositionFactory(
    val config: PositionFactoryConfiguration,
    private val optimalLine: Line?
) : FaceTargetPositionFactory() {
    override fun producePositionOnFace(face: AlignedFace, targetPos: BlockPos): Vec3 {
        val trimmedFace = trimFace(face).offset(targetPos)

        val targetFace = getTargetFace(player, trimmedFace) ?: trimmedFace

        return NearestRotationTargetPositionFactory(this.config).aimAtNearestPointToRotationLine(
            targetPos,
            targetFace.offset(-targetPos)
        )
    }

    private fun getTargetFace(
        player: LocalPlayer,
        trimmedFace: AlignedFace
    ): AlignedFace? {
        val optimalLine = optimalLine ?: return null

        val nearestPointToOptimalLine = optimalLine.getNearestPointTo(player.position())
        val directionToOptimalLine = player.position().subtract(nearestPointToOptimalLine).normalize()

        val optimalLineFromPlayer = Line(config.eyePos, optimalLine.direction)
        val collisionWithFacePlane = trimmedFace.toPlane().intersection(optimalLineFromPlayer) ?: return null

        val b = player.position().add(directionToOptimalLine.scale(2.0))

        val cropBox = AABB(
            collisionWithFacePlane.x,
            player.position().y - 2.0,
            collisionWithFacePlane.z,
            b.x,
            player.position().y + 1.0,
            b.z,
        )

        val clampedFace = trimmedFace.clamp(cropBox)

        // Not much left of the area? Then don't try to sample a point on the face
        if (clampedFace.area < 0.0001) {
            return null
        }

        return clampedFace
    }
}

object RandomTargetPositionFactory : FaceTargetPositionFactory() {
    override fun producePositionOnFace(face: AlignedFace, targetPos: BlockPos): Vec3 {
        val trimmedFace = trimFace(face)

        return trimmedFace.randomPointOnFace()
    }
}

object CenterTargetPositionFactory : FaceTargetPositionFactory() {
    override fun producePositionOnFace(face: AlignedFace, targetPos: BlockPos): Vec3 {
        return face.center
    }
}
