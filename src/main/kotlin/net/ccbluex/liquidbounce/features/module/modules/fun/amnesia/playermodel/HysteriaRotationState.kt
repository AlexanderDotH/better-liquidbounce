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

import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

internal class HysteriaRotationState {

    private var display = HysteriaModelRotation(0f, 0f, 0f)
    private var goal = HysteriaModelRotation(0f, 0f, 0f)
    private var ideal = HysteriaModelRotation(0f, 0f, 0f)
    private var lagEnabled = false
    private var lagUpdateInterval = 500
    private var lagSmoothDuration = 200
    private val snapTimer = Chronometer()

    fun configureLag(updateInterval: Int?, smoothDuration: Int?) {
        lagEnabled = updateInterval != null && smoothDuration != null
        if (lagEnabled) {
            lagUpdateInterval = updateInterval!!.coerceAtLeast(1)
            lagSmoothDuration = smoothDuration!!.coerceAtLeast(1)
        }
    }

    fun initializeTimer() = snapTimer.reset()

    fun syncToReal(target: LivingEntity, partialTicks: Float) {
        val rotation = HysteriaModelRotation(
            target.interpolateBodyYaw(partialTicks),
            target.interpolateHeadYaw(partialTicks),
            target.interpolatePitch(partialTicks),
        )
        display = rotation
        goal = rotation
    }

    fun setGoal(target: LivingEntity, entity: LivingEntity, partialTicks: Float, commitImmediately: Boolean = false) {
        val rotation = HysteriaTargetResolver.aimAt(target, entity, partialTicks)
        if (!lagEnabled) {
            goal = rotation
            return
        }
        ideal = rotation
        if (commitImmediately) {
            goal = ideal
            snapTimer.reset()
        }
    }

    fun step(frameDeltaMs: Int, activeSmoothDuration: Int) {
        if (lagEnabled && snapTimer.hasElapsed(lagUpdateInterval.toLong())) {
            goal = ideal
            snapTimer.reset()
        }
        val duration = if (lagEnabled) lagSmoothDuration else activeSmoothDuration
        val factor = (frameDeltaMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        display = HysteriaModelRotation(
            Mth.rotLerp(factor, display.bodyYaw, goal.bodyYaw),
            Mth.rotLerp(factor, display.headYaw, goal.headYaw),
            Mth.lerp(factor, display.pitch, goal.pitch),
        )
    }

    fun isLookingAt(target: LivingEntity, entity: LivingEntity, partialTicks: Float): Boolean {
        val aimRotation = Rotation.lookingAt(HysteriaTargetResolver.aimPoint(entity), target.getEyePosition(partialTicks))
        val displayRotation = Rotation(display.headYaw, display.pitch)
        return displayRotation.rotationDeltaLengthTo(aimRotation).coerceAtMost(180f) <= COMBAT_LOOK_TOLERANCE
    }

    fun transform(entity: Entity, targetEntityId: Int?): PlayerModelVisualTransform? {
        if (entity.id != targetEntityId) {
            return null
        }
        return PlayerModelVisualTransform(null, display.bodyYaw, display.headYaw, display.pitch)
    }

    fun reset() {
        lagEnabled = false
        snapTimer.reset()
    }

    private companion object {
        const val COMBAT_LOOK_TOLERANCE = 45f
    }
}
