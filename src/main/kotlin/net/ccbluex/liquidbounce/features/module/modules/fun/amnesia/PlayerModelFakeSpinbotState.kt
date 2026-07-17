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

import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentRotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

@Suppress("TooManyFunctions")
object PlayerModelFakeSpinbotState {

    private var targetEntityId: Int? = null
    private var displayYaw = 0f
    private var displayPitch = 90f
    private var spinYaw = 0f
    private var initialized = false
    private var wasSwinging = false
    private var snapUntil = 0L
    private var snapBodyYaw = 0f
    private var snapHeadYaw = 0f
    private var snapPitch = 0f
    private var lastFrameTime = 0L

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        spinSpeed: Float,
        pitch: Float,
        smoothDuration: Int,
        attackSnapDuration: Int,
        attackRange: Float,
    ) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        targetEntityId = target.id
        val frameDeltaMs = updateFrameTime()
        if (!initialized) {
            initialized = true
            spinYaw = target.getViewYRot(partialTicks)
            displayYaw = spinYaw
            displayPitch = pitch
            wasSwinging = target.swinging
            return
        }

        if (target.swinging && !wasSwinging) {
            findCombatEntity(target, partialTicks, attackRange)?.let {
                beginSnap(target, it, partialTicks, attackSnapDuration)
            }
        }
        wasSwinging = target.swinging

        spinYaw = Mth.wrapDegrees(spinYaw + spinSpeed * (frameDeltaMs.toFloat() / 1000f))
        if (isSnapping()) {
            displayYaw = snapHeadYaw
            displayPitch = snapPitch
            return
        }

        val factor = smoothFactor(frameDeltaMs, smoothDuration)
        displayYaw = Mth.rotLerp(factor, displayYaw, spinYaw)
        displayPitch = Mth.lerp(factor, displayPitch, pitch.coerceIn(-90f, 90f))
    }

    fun getTransform(entity: Entity): PlayerModelVisualTransform? {
        if (entity.id != targetEntityId || !initialized) {
            return null
        }

        val yaw = if (isSnapping()) snapHeadYaw else displayYaw
        val pitch = if (isSnapping()) snapPitch else displayPitch
        return PlayerModelVisualTransform(
            position = null,
            bodyYaw = if (isSnapping()) snapBodyYaw else yaw,
            headYaw = yaw,
            pitch = pitch,
        )
    }

    fun reset() {
        targetEntityId = null
        displayYaw = 0f
        displayPitch = 90f
        spinYaw = 0f
        initialized = false
        wasSwinging = false
        snapUntil = 0L
        snapBodyYaw = 0f
        snapHeadYaw = 0f
        snapPitch = 0f
        lastFrameTime = 0L
    }

    private fun beginSnap(
        target: LivingEntity,
        entity: LivingEntity,
        partialTicks: Float,
        attackSnapDuration: Int,
    ) {
        val rotation = Rotation.lookingAt(entity.eyePosition, target.getEyePosition(partialTicks))
        snapBodyYaw = rotation.yaw
        snapHeadYaw = rotation.yaw
        snapPitch = rotation.pitch.coerceIn(-90f, 90f)
        displayYaw = snapHeadYaw
        displayPitch = snapPitch
        snapUntil = System.currentTimeMillis() + attackSnapDuration.coerceAtLeast(1)
    }

    private fun findCombatEntity(
        target: LivingEntity,
        partialTicks: Float,
        attackRange: Float,
    ): LivingEntity? {
        val range = attackRange.coerceAtLeast(1f)
        val rotation = target.interpolateCurrentRotation(partialTicks)
        val rayHit = target.findEntityInCrosshair(range.toDouble(), rotation) { entity ->
            entity is LivingEntity && entity.id != target.id && isValidTarget(entity)
        }?.entity as? LivingEntity

        if (rayHit != null) {
            return rayHit
        }

        return nearestTarget(target, range)
    }

    private fun nearestTarget(target: LivingEntity, range: Float): LivingEntity? {
        val rangeSq = range.sq()
        var best: LivingEntity? = null
        var bestDistance = Double.POSITIVE_INFINITY

        for (entity in world.entitiesForRendering()) {
            if (entity !is LivingEntity || entity.id == target.id || !isValidTarget(entity)) {
                continue
            }

            val distance = target.squaredBoxedDistanceTo(entity)
            if (distance > rangeSq || distance >= bestDistance) {
                continue
            }

            best = entity
            bestDistance = distance
        }

        return best
    }

    private fun isValidTarget(entity: LivingEntity): Boolean {
        return !entity.isRemoved
            && entity.isAlive
            && !entity.isSpectator
            && (entity !is Player || !ModuleAntiBot.isBot(entity))
    }

    private fun updateFrameTime(): Long {
        val now = System.currentTimeMillis()
        val previous = lastFrameTime
        lastFrameTime = now
        if (previous == 0L) {
            return 1L
        }

        return (now - previous).coerceIn(1L, 100L)
    }

    private fun smoothFactor(frameDeltaMs: Long, smoothDuration: Int): Float {
        if (smoothDuration <= 0) {
            return 1f
        }

        return (frameDeltaMs.toFloat() / smoothDuration.toFloat()).coerceIn(0f, 1f)
    }

    private fun isSnapping(): Boolean = System.currentTimeMillis() <= snapUntil
}
