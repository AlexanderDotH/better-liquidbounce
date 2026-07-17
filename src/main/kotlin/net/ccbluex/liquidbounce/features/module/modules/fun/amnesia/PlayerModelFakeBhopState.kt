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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

@Suppress("TooManyFunctions")
object PlayerModelFakeBhopState {

    private const val MIN_HORIZONTAL_MOVEMENT_SQ = 1.0E-5
    private const val ACTIVE_EPSILON = 0.01

    private var targetEntityId: Int? = null
    private var lastVisualPos: Vec3? = null
    private var lastFrameTime = 0L
    private var phaseMs = 0L
    private var displayStrength = 0.0
    private var directionX = 0.0
    private var directionZ = 1.0
    private var movementYaw = 0f
    private var hasMovementDirection = false
    private var rotateToMovement = true
    private var pitch = 8f
    private var groundPose = true
    private var style = FakeBhop.BhopStyle.NORMAL
    private var hopHeight = 0.42f
    private var hopInterval = 380
    private var strafeAmount = 0.05f

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        visualPos: Vec3,
        style: FakeBhop.BhopStyle,
        hopHeight: Float,
        hopInterval: Int,
        minMoveSpeed: Float,
        strafeAmount: Float,
        rotateToMovement: Boolean,
        pitch: Float,
        spoofGroundPose: Boolean,
        smoothStopDuration: Int,
    ) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        targetEntityId = target.id
        this.style = style
        this.hopHeight = hopHeight
        this.hopInterval = hopInterval
        this.strafeAmount = strafeAmount
        this.rotateToMovement = rotateToMovement
        this.pitch = pitch.coerceIn(-90f, 90f)
        groundPose = spoofGroundPose

        val frameDeltaMs = updateFrameTime()
        val movement = movementVector(target, visualPos, frameDeltaMs)
        lastVisualPos = visualPos

        val moving = movement.horizontalSpeedPerTick >= minMoveSpeed.toDouble()
        if (moving) {
            updateDirection(target, partialTicks, movement.vector)
            phaseMs = (phaseMs + frameDeltaMs) % styleInterval().coerceAtLeast(1)
        }

        updateStrength(if (moving) 1.0 else 0.0, frameDeltaMs, smoothStopDuration)
    }

    fun getTransform(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ): PlayerModelVisualTransform? {
        if (!isActiveFor(entity)) {
            return null
        }

        val offset = if (velocityPositionActive) Vec3.ZERO else currentOffset()
        if (offset.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ && !hasRotation(entity)) {
            return null
        }

        return PlayerModelVisualTransform(
            position = if (offset.lengthSqr() > MIN_HORIZONTAL_MOVEMENT_SQ) basePosition.add(offset) else null,
            bodyYaw = if (hasRotation(entity)) movementYaw else entity.interpolateBodyYaw(partialTicks),
            headYaw = if (hasRotation(entity)) movementYaw else entity.interpolateHeadYaw(partialTicks),
            pitch = if (hasRotation(entity)) pitch else entity.interpolatePitch(partialTicks),
        )
    }

    fun hasRotation(entity: LivingEntity): Boolean =
        entity.id == targetEntityId && rotateToMovement && hasMovementDirection && displayStrength > ACTIVE_EPSILON

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (!isActiveFor(entity) || !groundPose) {
            return null
        }

        return PlayerModelActionState(groundPose = true)
    }

    fun reset() {
        targetEntityId = null
        lastVisualPos = null
        lastFrameTime = 0L
        phaseMs = 0L
        displayStrength = 0.0
        directionX = 0.0
        directionZ = 1.0
        movementYaw = 0f
        hasMovementDirection = false
        rotateToMovement = true
        pitch = 8f
        groundPose = true
        style = FakeBhop.BhopStyle.NORMAL
        hopHeight = 0.42f
        hopInterval = 380
        strafeAmount = 0.05f
    }

    private fun movementVector(target: LivingEntity, visualPos: Vec3, frameDeltaMs: Long): MovementVector {
        val previous = lastVisualPos
        val rawMovement = previous?.let(visualPos::subtract) ?: target.deltaMovement
        val movement = if (rawMovement.horizontalDistanceSqr() > MIN_HORIZONTAL_MOVEMENT_SQ) {
            rawMovement
        } else {
            target.deltaMovement
        }
        val speedPerTick = if (previous == null) {
            movement.horizontalDistance()
        } else {
            movement.horizontalDistance() * (50.0 / frameDeltaMs.toDouble())
        }

        return MovementVector(movement, speedPerTick)
    }

    private fun updateDirection(target: LivingEntity, partialTicks: Float, movement: Vec3) {
        val horizontal = movement.horizontal()
        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return
        }

        val normalized = horizontal.normalize()
        directionX = normalized.x
        directionZ = normalized.z
        movementYaw = movementYaw(target, partialTicks, normalized)
        hasMovementDirection = true
    }

    private fun movementYaw(target: LivingEntity, partialTicks: Float, movement: Vec3): Float {
        if (movement.horizontalDistanceSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return target.getViewYRot(partialTicks)
        }

        return Mth.wrapDegrees(Math.toDegrees(atan2(movement.z, movement.x)).toFloat() - 90f)
    }

    private fun currentOffset(): Vec3 {
        val progress = hopProgress()
        val vertical = sin(progress * PI).coerceAtLeast(0.0) * styleHeight() * displayStrength
        val side = sin(progress * PI * 2.0) * styleStrafe() * displayStrength

        return Vec3(-directionZ * side, vertical, directionX * side)
    }

    private fun hopProgress(): Double =
        phaseMs.toDouble() / styleInterval().coerceAtLeast(1).toDouble()

    private fun styleHeight(): Double = when (style) {
        FakeBhop.BhopStyle.NORMAL -> hopHeight.toDouble()
        FakeBhop.BhopStyle.LOW_HOP -> hopHeight.toDouble() * 0.55
        FakeBhop.BhopStyle.STRAFE -> hopHeight.toDouble() * 0.85
    }.coerceAtLeast(0.0)

    private fun styleInterval(): Long = when (style) {
        FakeBhop.BhopStyle.NORMAL -> hopInterval
        FakeBhop.BhopStyle.LOW_HOP -> (hopInterval * 0.75f).toInt()
        FakeBhop.BhopStyle.STRAFE -> (hopInterval * 0.85f).toInt()
    }.coerceAtLeast(1).toLong()

    private fun styleStrafe(): Double = when (style) {
        FakeBhop.BhopStyle.NORMAL -> strafeAmount.toDouble() * 0.4
        FakeBhop.BhopStyle.LOW_HOP -> strafeAmount.toDouble() * 0.25
        FakeBhop.BhopStyle.STRAFE -> strafeAmount.toDouble()
    }.coerceAtLeast(0.0)

    private fun updateStrength(targetStrength: Double, frameDeltaMs: Long, smoothStopDuration: Int) {
        if (targetStrength >= displayStrength || smoothStopDuration <= 0) {
            displayStrength = targetStrength
            return
        }

        val t = (frameDeltaMs.toDouble() / smoothStopDuration.toDouble()).coerceIn(0.0, 1.0)
        displayStrength = Mth.lerp(t, displayStrength, targetStrength)
    }

    private fun updateFrameTime(): Long {
        val now = System.currentTimeMillis()
        val previous = lastFrameTime
        lastFrameTime = now
        if (previous == 0L) {
            return 50L
        }

        return (now - previous).coerceIn(1L, 100L)
    }

    private fun isActiveFor(entity: LivingEntity): Boolean =
        entity.id == targetEntityId && displayStrength > ACTIVE_EPSILON

    private data class MovementVector(
        val vector: Vec3,
        val horizontalSpeedPerTick: Double,
    )
}
