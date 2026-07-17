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

import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

@Suppress("TooManyFunctions")
object PlayerModelFakeVelocityState {

    private const val MIN_RELEASE_DISTANCE = 0.15f
    private const val MAX_SAMPLE_DELTA_SQ = 16.0

    private var targetEntityId: Int? = null
    private var pendingFreeze = false
    private var frozen = false
    private var frozenPos: Vec3? = null
    private var frozenBodyYaw = 0f
    private var frozenHeadYaw = 0f
    private var frozenPitch = 0f
    private var noVelocityActive = false
    private var noVelocityPos: Vec3? = null
    private var noVelocityVelocityPerMs = Vec3.ZERO
    private var recentVelocityPerMs = Vec3.ZERO
    private var lastSamplePos: Vec3? = null
    private var lastSampleTime = 0L
    private var lastFrameTime = 0L
    private var lastHurtTime = 0
    private val minFreezeTimer = Chronometer()

    fun queueFreezeFromDamage(target: LivingEntity) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }
        if (frozen) {
            return
        }
        targetEntityId = target.id
        pendingFreeze = true
        if (DelayPlayerModel.running && FakeVelocity.mode == FakeVelocity.VelocityMode.FREEZE) {
            PlayerModelDelayState.setFrozen(true)
        }
    }

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        mode: FakeVelocity.VelocityMode,
        resumeDistance: Float,
        teleportDistance: Float,
        minFreezeDuration: Int,
        retainedMotion: Float,
        recoveryDuration: Int,
        maxDesync: Float,
        tinyRecoil: Float,
    ) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        targetEntityId = target.id
        val realPos = target.interpolateCurrentPosition(partialTicks)
        val now = System.currentTimeMillis()
        val frameDeltaMs = updateFrameTime(now)

        if (pendingFreeze && !isActive()) {
            commitVelocityEffect(target, partialTicks, mode, realPos, retainedMotion, tinyRecoil)
        } else if (!isActive() && lastHurtTime == 0 && target.hurtTime > 0) {
            pendingFreeze = true
            if (DelayPlayerModel.running && mode == FakeVelocity.VelocityMode.FREEZE) {
                PlayerModelDelayState.setFrozen(true)
            }
            commitVelocityEffect(target, partialTicks, mode, realPos, retainedMotion, tinyRecoil)
        }

        lastHurtTime = target.hurtTime

        when {
            frozen -> tickFreeze(target, partialTicks, realPos, resumeDistance, teleportDistance, minFreezeDuration)
            noVelocityActive -> tickNoVelocity(
                realPos = realPos,
                frameDeltaMs = frameDeltaMs,
                resumeDistance = resumeDistance,
                teleportDistance = teleportDistance,
                minFreezeDuration = minFreezeDuration,
                recoveryDuration = recoveryDuration,
                maxDesync = maxDesync,
            )
            else -> updateMotionSample(realPos, now)
        }
    }

    fun getTransform(
        entity: LivingEntity,
        partialTicks: Float,
        base: PlayerModelVisualTransform?,
    ): PlayerModelVisualTransform? {
        if (entity.id != targetEntityId) {
            return null
        }

        if (frozen) {
            val frozenPosition = frozenPos ?: return null
            val rotationSource = base ?: getFrozenRotationFallback(entity, partialTicks)
            return rotationSource.copy(
                position = frozenPosition,
                freezeWalkAnimation = true,
            )
        }

        if (noVelocityActive) {
            val visualPosition = noVelocityPos ?: return null
            val rotationSource = base ?: getCurrentRotationFallback(entity, partialTicks)
            return rotationSource.copy(
                position = visualPosition,
                freezeWalkAnimation = false,
            )
        }

        return null
    }

    fun getVisualPosition(entity: LivingEntity): Vec3? {
        if (entity.id != targetEntityId) {
            return null
        }

        return when {
            frozen -> frozenPos
            noVelocityActive -> noVelocityPos
            else -> null
        }
    }

    fun hasPositionOverride(entity: LivingEntity): Boolean = getVisualPosition(entity) != null

    private fun tickFreeze(
        target: LivingEntity,
        partialTicks: Float,
        realPos: Vec3,
        resumeDistance: Float,
        teleportDistance: Float,
        minFreezeDuration: Int,
    ) {
        if (!minFreezeTimer.hasElapsed(minFreezeDuration.toLong())) {
            return
        }

        val frozenPosition = frozenPos ?: return
        val dist = realPos.distanceTo(frozenPosition)
        val effectiveTeleportDistance = teleportDistance.coerceAtLeast(resumeDistance + 0.5f)

        when {
            dist <= resumeDistance -> exitFreeze(target, partialTicks, resume = true)
            dist >= effectiveTeleportDistance -> exitFreeze(target, partialTicks, resume = false)
        }
    }

    private fun tickNoVelocity(
        realPos: Vec3,
        frameDeltaMs: Long,
        resumeDistance: Float,
        teleportDistance: Float,
        minFreezeDuration: Int,
        recoveryDuration: Int,
        maxDesync: Float,
    ) {
        val visualPos = noVelocityPos ?: return
        val predicted = visualPos.add(noVelocityVelocityPerMs.scale(frameDeltaMs.toDouble()))
        val capped = capDesync(predicted, realPos, maxDesync)
        val correction = (frameDeltaMs.toFloat() / recoveryDuration.toFloat()).coerceIn(0f, 1f)
        noVelocityPos = lerp(capped, realPos, correction)

        val correctedPos = noVelocityPos ?: return
        val dist = correctedPos.distanceTo(realPos)
        if (dist >= teleportDistance) {
            exitNoVelocity(realPos)
            return
        }

        val releaseDistance = resumeDistance.coerceAtMost(MIN_RELEASE_DISTANCE)
        if (minFreezeTimer.hasElapsed(minFreezeDuration.toLong()) && dist <= releaseDistance) {
            exitNoVelocity(realPos)
        }
    }

    private fun capDesync(predicted: Vec3, realPos: Vec3, maxDesync: Float): Vec3 {
        val offset = realPos.subtract(predicted)
        val maxDistance = maxDesync.toDouble()
        if (offset.lengthSqr() <= maxDistance * maxDistance) {
            return predicted
        }

        return realPos.subtract(offset.normalize().scale(maxDistance))
    }

    private fun getFrozenRotationFallback(
        @Suppress("UNUSED_PARAMETER") entity: LivingEntity,
        @Suppress("UNUSED_PARAMETER") partialTicks: Float,
    ): PlayerModelVisualTransform {
        return PlayerModelVisualTransform(
            position = null,
            bodyYaw = frozenBodyYaw,
            headYaw = frozenHeadYaw,
            pitch = frozenPitch,
            freezeWalkAnimation = true,
        )
    }

    private fun getCurrentRotationFallback(entity: LivingEntity, partialTicks: Float): PlayerModelVisualTransform {
        return PlayerModelVisualTransform(
            position = null,
            bodyYaw = entity.interpolateBodyYaw(partialTicks),
            headYaw = entity.interpolateHeadYaw(partialTicks),
            pitch = entity.interpolatePitch(partialTicks),
        )
    }

    fun reset() {
        PlayerModelDelayState.setFrozen(false)
        targetEntityId = null
        pendingFreeze = false
        frozen = false
        frozenPos = null
        frozenBodyYaw = 0f
        frozenHeadYaw = 0f
        frozenPitch = 0f
        noVelocityActive = false
        noVelocityPos = null
        noVelocityVelocityPerMs = Vec3.ZERO
        recentVelocityPerMs = Vec3.ZERO
        lastSamplePos = null
        lastSampleTime = 0L
        lastFrameTime = 0L
        lastHurtTime = 0
        minFreezeTimer.reset()
    }

    private fun isActive(): Boolean = frozen || noVelocityActive

    private fun commitVelocityEffect(
        target: LivingEntity,
        partialTicks: Float,
        mode: FakeVelocity.VelocityMode,
        realPos: Vec3,
        retainedMotion: Float,
        tinyRecoil: Float,
    ) {
        when (mode) {
            FakeVelocity.VelocityMode.FREEZE -> commitFreeze(target, partialTicks)
            FakeVelocity.VelocityMode.NO_VELOCITY -> commitNoVelocity(
                target,
                partialTicks,
                realPos,
                retainedMotion,
                tinyRecoil,
            )
        }
    }

    private fun commitFreeze(target: LivingEntity, partialTicks: Float) {
        val rotation = captureVisualRotation(target, partialTicks)
        frozenPos = captureVisualPosition(target, partialTicks)
        frozenBodyYaw = rotation.bodyYaw
        frozenHeadYaw = rotation.headYaw
        frozenPitch = rotation.pitch
        pendingFreeze = false
        frozen = true
        minFreezeTimer.reset()
        PlayerModelDelayState.setFrozen(true)
    }

    private fun commitNoVelocity(
        target: LivingEntity,
        partialTicks: Float,
        realPos: Vec3,
        retainedMotion: Float,
        tinyRecoil: Float,
    ) {
        val visualStart = captureVisualPosition(target, partialTicks)
        noVelocityPos = visualStart.add(calculateTinyRecoil(visualStart, realPos, tinyRecoil))
        noVelocityVelocityPerMs = recentVelocityPerMs.scale(retainedMotion.toDouble())
        pendingFreeze = false
        noVelocityActive = true
        minFreezeTimer.reset()
        PlayerModelDelayState.setFrozen(false)
    }

    private fun exitFreeze(target: LivingEntity, partialTicks: Float, resume: Boolean) {
        PlayerModelDelayState.setFrozen(false)
        if (DelayPlayerModel.running) {
            if (resume) {
                PlayerModelDelayState.resyncToEntity(target, partialTicks)
            } else {
                PlayerModelDelayState.snapToEntity(target, partialTicks)
            }
        }
        frozen = false
        frozenPos = null
        pendingFreeze = false
        minFreezeTimer.reset()
    }

    private fun exitNoVelocity(realPos: Vec3) {
        noVelocityActive = false
        noVelocityPos = null
        noVelocityVelocityPerMs = Vec3.ZERO
        pendingFreeze = false
        minFreezeTimer.reset()
        lastSamplePos = realPos
        lastSampleTime = System.currentTimeMillis()
    }

    private fun updateFrameTime(now: Long): Long {
        val previous = lastFrameTime
        lastFrameTime = now
        if (previous == 0L) {
            return 1L
        }

        return (now - previous).coerceIn(1L, 100L)
    }

    private fun updateMotionSample(realPos: Vec3, now: Long) {
        val previousPos = lastSamplePos
        val previousTime = lastSampleTime
        if (previousPos != null && previousTime > 0L) {
            val delta = realPos.subtract(previousPos)
            if (delta.lengthSqr() <= MAX_SAMPLE_DELTA_SQ) {
                val elapsed = (now - previousTime).coerceAtLeast(1L)
                recentVelocityPerMs = delta.scale(1.0 / elapsed.toDouble())
            }
        }

        lastSamplePos = realPos
        lastSampleTime = now
    }

    private fun calculateTinyRecoil(visualStart: Vec3, realPos: Vec3, tinyRecoil: Float): Vec3 {
        if (tinyRecoil <= 0f) {
            return Vec3.ZERO
        }

        val hitOffset = realPos.subtract(visualStart)
        if (hitOffset.lengthSqr() <= 1.0E-6) {
            return Vec3.ZERO
        }

        val recoilDistance = hitOffset.length().coerceAtMost(tinyRecoil.toDouble())
        return hitOffset.normalize().scale(recoilDistance)
    }

    private fun lerp(from: Vec3, to: Vec3, factor: Float): Vec3 {
        val t = factor.toDouble()
        return Vec3(
            Mth.lerp(t, from.x, to.x),
            Mth.lerp(t, from.y, to.y),
            Mth.lerp(t, from.z, to.z),
        )
    }

    private fun captureVisualPosition(target: LivingEntity, partialTicks: Float): Vec3 {
        if (DelayPlayerModel.running) {
            PlayerModelDelayState.getTransform(target)?.pos?.let { return it }
        }
        return target.interpolateCurrentPosition(partialTicks)
    }

    private fun captureVisualRotation(target: LivingEntity, partialTicks: Float): ModelRotation {
        if (FakeKillAura.running) {
            PlayerModelHysteriaState.getTransform(target)?.let {
                return ModelRotation(it.bodyYaw, it.headYaw, it.pitch)
            }
        }
        if (DelayPlayerModel.running) {
            PlayerModelDelayState.getTransform(target)?.let {
                return ModelRotation(it.bodyYaw, it.headYaw, it.pitch)
            }
        }
        return ModelRotation(
            bodyYaw = target.interpolateBodyYaw(partialTicks),
            headYaw = target.interpolateHeadYaw(partialTicks),
            pitch = target.interpolatePitch(partialTicks),
        )
    }

    private data class ModelRotation(
        val bodyYaw: Float,
        val headYaw: Float,
        val pitch: Float,
    )
}
