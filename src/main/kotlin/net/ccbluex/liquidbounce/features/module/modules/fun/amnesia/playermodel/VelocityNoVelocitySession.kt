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
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal class VelocityNoVelocitySession {

    var active = false
        private set

    private var position: Vec3? = null
    private var velocityPerMs = Vec3.ZERO
    private val minimumDuration = Chronometer()

    fun commit(
        target: LivingEntity,
        partialTicks: Float,
        realPosition: Vec3,
        retainedVelocity: Vec3,
        tinyRecoil: Float,
    ) {
        val visualStart = VelocityVisualCapture.position(target, partialTicks)
        position = visualStart.add(VelocityPositionMath.tinyRecoil(visualStart, realPosition, tinyRecoil))
        velocityPerMs = retainedVelocity
        active = true
        minimumDuration.reset()
        PlayerModelDelayState.setFrozen(false)
    }

    fun tick(
        realPosition: Vec3,
        frameDeltaMs: Long,
        resumeDistance: Float,
        teleportDistance: Float,
        minimumDurationMs: Int,
        recoveryDuration: Int,
        maxDesync: Float,
    ): Boolean {
        val visualPosition = position ?: return false
        val predicted = visualPosition.add(velocityPerMs.scale(frameDeltaMs.toDouble()))
        val capped = VelocityPositionMath.capDesync(predicted, realPosition, maxDesync)
        val correction = (frameDeltaMs.toFloat() / recoveryDuration.toFloat()).coerceIn(0f, 1f)
        position = VelocityPositionMath.lerp(capped, realPosition, correction)
        val distance = position?.distanceTo(realPosition) ?: return false
        val released = distance >= teleportDistance ||
            minimumDuration.hasElapsed(minimumDurationMs.toLong()) &&
            distance <= resumeDistance.coerceAtMost(MIN_RELEASE_DISTANCE)
        if (released) {
            reset()
        }
        return released
    }

    fun transform(entity: LivingEntity, partialTicks: Float, base: PlayerModelVisualTransform?): PlayerModelVisualTransform? {
        val visualPosition = position ?: return null
        val rotationSource = base ?: VelocityVisualCapture.currentRotationFallback(entity, partialTicks)
        return rotationSource.copy(position = visualPosition, freezeWalkAnimation = false)
    }

    fun visualPosition(): Vec3? = position

    fun reset() {
        active = false
        position = null
        velocityPerMs = Vec3.ZERO
        minimumDuration.reset()
    }

    private companion object {
        const val MIN_RELEASE_DISTANCE = 0.15f
    }
}
