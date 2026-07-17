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
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

data class DelayedTransform(
    val pos: Vec3,
    val bodyYaw: Float,
    val headYaw: Float,
    val pitch: Float,
)

object PlayerModelDelayState {

    private var targetEntityId: Int? = null
    private var displayPos: Vec3? = null
    private var displayBodyYaw: Float? = null
    private var displayHeadYaw: Float? = null
    private var displayPitch: Float? = null
    private var targetPos: Vec3? = null
    private var targetBodyYaw: Float? = null
    private var targetHeadYaw: Float? = null
    private var targetPitch: Float? = null
    private var initialized = false
    private var positionRotationFrozen = false
    private val snapTimer = Chronometer()
    private var lastFrameTime = System.currentTimeMillis()

    fun setFrozen(frozen: Boolean) {
        positionRotationFrozen = frozen
    }

    fun snapToEntity(entity: LivingEntity, partialTicks: Float) {
        syncToEntity(entity, partialTicks)
    }

    fun resyncToEntity(entity: LivingEntity, partialTicks: Float) {
        syncToEntity(entity, partialTicks)
    }

    private fun syncToEntity(entity: LivingEntity, partialTicks: Float) {
        val currentPos = entity.interpolateCurrentPosition(partialTicks)
        val currentBodyYaw = entity.interpolateBodyYaw(partialTicks)
        val currentHeadYaw = entity.interpolateHeadYaw(partialTicks)
        val currentPitch = entity.interpolatePitch(partialTicks)

        displayPos = currentPos
        displayBodyYaw = currentBodyYaw
        displayHeadYaw = currentHeadYaw
        displayPitch = currentPitch
        targetPos = currentPos
        targetBodyYaw = currentBodyYaw
        targetHeadYaw = currentHeadYaw
        targetPitch = currentPitch
        snapTimer.reset()
        lastFrameTime = System.currentTimeMillis()
    }

    fun tick(
        entity: LivingEntity,
        partialTicks: Float,
        updateInterval: Int,
        smoothDuration: Int,
    ) {
        if (initialized && targetEntityId != null && targetEntityId != entity.id) {
            reset()
        }

        targetEntityId = entity.id
        val currentPos = entity.interpolateCurrentPosition(partialTicks)
        val currentBodyYaw = entity.interpolateBodyYaw(partialTicks)
        val currentHeadYaw = entity.interpolateHeadYaw(partialTicks)
        val currentPitch = entity.interpolatePitch(partialTicks)

        if (!initialized) {
            displayPos = currentPos
            displayBodyYaw = currentBodyYaw
            displayHeadYaw = currentHeadYaw
            displayPitch = currentPitch
            targetPos = currentPos
            targetBodyYaw = currentBodyYaw
            targetHeadYaw = currentHeadYaw
            targetPitch = currentPitch
            initialized = true
            snapTimer.reset()
            lastFrameTime = System.currentTimeMillis()
            return
        }

        if (positionRotationFrozen) {
            return
        }

        if (snapTimer.hasElapsed(updateInterval.toLong())) {
            targetPos = currentPos
            targetBodyYaw = currentBodyYaw
            targetHeadYaw = currentHeadYaw
            targetPitch = currentPitch
            snapTimer.reset()
        }

        val now = System.currentTimeMillis()
        val frameDeltaMs = (now - lastFrameTime).coerceAtLeast(1)
        lastFrameTime = now

        val t = (frameDeltaMs.toFloat() / smoothDuration.toFloat()).coerceIn(0f, 1f)

        val display = displayPos ?: currentPos
        val target = targetPos ?: currentPos
        displayPos = Vec3(
            Mth.lerp(t.toDouble(), display.x, target.x),
            Mth.lerp(t.toDouble(), display.y, target.y),
            Mth.lerp(t.toDouble(), display.z, target.z),
        )

        displayBodyYaw = Mth.rotLerp(t, displayBodyYaw ?: currentBodyYaw, targetBodyYaw ?: currentBodyYaw)
        displayHeadYaw = Mth.rotLerp(t, displayHeadYaw ?: currentHeadYaw, targetHeadYaw ?: currentHeadYaw)
        displayPitch = Mth.lerp(t, displayPitch ?: currentPitch, targetPitch ?: currentPitch)
    }

    fun getTransform(entity: Entity): DelayedTransform? {
        if (entity.id != targetEntityId || !initialized) {
            return null
        }

        val pos = displayPos ?: return null
        val bodyYaw = displayBodyYaw ?: return null
        val headYaw = displayHeadYaw ?: return null
        val pitch = displayPitch ?: return null
        return DelayedTransform(pos, bodyYaw, headYaw, pitch)
    }

    fun reset() {
        targetEntityId = null
        displayPos = null
        displayBodyYaw = null
        displayHeadYaw = null
        displayPitch = null
        targetPos = null
        targetBodyYaw = null
        targetHeadYaw = null
        targetPitch = null
        initialized = false
        positionRotationFrozen = false
        snapTimer.reset()
        lastFrameTime = System.currentTimeMillis()
    }
}
