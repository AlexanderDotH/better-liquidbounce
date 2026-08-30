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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.BhopStyle

import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

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
    private var style = BhopStyle.NORMAL
    private var hopHeight = 0.42f
    private var hopInterval = 380
    private var strafeAmount = 0.05f

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        visualPos: Vec3,
        style: BhopStyle,
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
        val movement = BhopAnimationMath.movementVector(lastVisualPos, visualPos, target.deltaMovement, frameDeltaMs)
        lastVisualPos = visualPos

        val moving = movement.horizontalSpeedPerTick >= minMoveSpeed.toDouble()
        if (moving) {
            updateDirection(target, partialTicks, movement.vector)
            phaseMs = (phaseMs + frameDeltaMs) % BhopAnimationMath.styleInterval(style, hopInterval)
        }

        displayStrength = BhopAnimationMath.updateStrength(
            displayStrength,
            if (moving) 1.0 else 0.0,
            frameDeltaMs,
            smoothStopDuration,
        )
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
        style = BhopStyle.NORMAL
        hopHeight = 0.42f
        hopInterval = 380
        strafeAmount = 0.05f
    }

    private fun updateDirection(target: LivingEntity, partialTicks: Float, movement: Vec3) {
        val horizontal = movement.horizontal()
        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return
        }

        val normalized = horizontal.normalize()
        directionX = normalized.x
        directionZ = normalized.z
        movementYaw = BhopAnimationMath.movementYaw(normalized, target.getViewYRot(partialTicks))
        hasMovementDirection = true
    }

    private fun currentOffset(): Vec3 = BhopAnimationMath.offset(
        phaseMs,
        style,
        hopHeight,
        hopInterval,
        strafeAmount,
        displayStrength,
        directionX,
        directionZ,
    )

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

}
