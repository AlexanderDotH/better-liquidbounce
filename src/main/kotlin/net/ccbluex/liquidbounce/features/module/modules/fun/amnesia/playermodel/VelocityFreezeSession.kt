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
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal class VelocityFreezeSession {

    var active = false
        private set

    private var position: Vec3? = null
    private var rotation = VelocityModelRotation(0f, 0f, 0f)
    private val minimumDuration = Chronometer()

    fun commit(target: LivingEntity, partialTicks: Float) {
        rotation = VelocityVisualCapture.rotation(target, partialTicks)
        position = VelocityVisualCapture.position(target, partialTicks)
        active = true
        minimumDuration.reset()
        PlayerModelDelayState.setFrozen(true)
    }

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        realPosition: Vec3,
        resumeDistance: Float,
        teleportDistance: Float,
        minimumDurationMs: Int,
    ) {
        if (!minimumDuration.hasElapsed(minimumDurationMs.toLong())) {
            return
        }
        val frozenPosition = position ?: return
        val distance = realPosition.distanceTo(frozenPosition)
        val teleportThreshold = teleportDistance.coerceAtLeast(resumeDistance + 0.5f)
        when {
            distance <= resumeDistance -> exit(target, partialTicks, resume = true)
            distance >= teleportThreshold -> exit(target, partialTicks, resume = false)
        }
    }

    fun transform(base: PlayerModelVisualTransform?): PlayerModelVisualTransform? {
        val frozenPosition = position ?: return null
        val rotationSource = base ?: PlayerModelVisualTransform(
            position = null,
            bodyYaw = rotation.bodyYaw,
            headYaw = rotation.headYaw,
            pitch = rotation.pitch,
            freezeWalkAnimation = true,
        )
        return rotationSource.copy(position = frozenPosition, freezeWalkAnimation = true)
    }

    fun visualPosition(): Vec3? = position

    fun reset() {
        PlayerModelDelayState.setFrozen(false)
        active = false
        position = null
        rotation = VelocityModelRotation(0f, 0f, 0f)
        minimumDuration.reset()
    }

    private fun exit(target: LivingEntity, partialTicks: Float, resume: Boolean) {
        PlayerModelDelayState.setFrozen(false)
        if (AmnesiaRuntimeBridge.delayPlayerModelRunning()) {
            if (resume) {
                PlayerModelDelayState.resyncToEntity(target, partialTicks)
            } else {
                PlayerModelDelayState.snapToEntity(target, partialTicks)
            }
        }
        active = false
        position = null
        minimumDuration.reset()
    }
}
