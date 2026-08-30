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
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.VelocityMode
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal class VelocityCoordinator {

    private var targetEntityId: Int? = null
    private var pendingFreeze = false
    private var lastHurtTime = 0
    private val freezeSession = VelocityFreezeSession()
    private val noVelocitySession = VelocityNoVelocitySession()
    private val motionTracker = VelocityMotionTracker()

    fun queueFreezeFromDamage(target: LivingEntity) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }
        if (freezeSession.active) {
            return
        }
        targetEntityId = target.id
        pendingFreeze = true
        if (AmnesiaRuntimeBridge.delayPlayerModelRunning() &&
            AmnesiaRuntimeBridge.fakeVelocityMode() == VelocityMode.FREEZE
        ) {
            PlayerModelDelayState.setFrozen(true)
        }
    }

    fun tick(target: LivingEntity, partialTicks: Float, settings: VelocityTickSettings) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }
        targetEntityId = target.id
        val realPosition = target.interpolateCurrentPosition(partialTicks)
        val now = System.currentTimeMillis()
        val frameDeltaMs = motionTracker.frameDelta(now)
        commitPendingEffect(target, partialTicks, settings, realPosition)
        lastHurtTime = target.hurtTime
        tickActiveSession(target, partialTicks, settings, realPosition, frameDeltaMs, now)
    }

    fun transform(
        entity: LivingEntity,
        partialTicks: Float,
        base: PlayerModelVisualTransform?,
    ): PlayerModelVisualTransform? {
        if (entity.id != targetEntityId) {
            return null
        }
        if (freezeSession.active) {
            return freezeSession.transform(base)
        }
        if (noVelocitySession.active) {
            return noVelocitySession.transform(entity, partialTicks, base)
        }
        return null
    }

    fun visualPosition(entity: LivingEntity): Vec3? {
        if (entity.id != targetEntityId) {
            return null
        }
        return when {
            freezeSession.active -> freezeSession.visualPosition()
            noVelocitySession.active -> noVelocitySession.visualPosition()
            else -> null
        }
    }

    fun reset() {
        freezeSession.reset()
        noVelocitySession.reset()
        motionTracker.reset()
        targetEntityId = null
        pendingFreeze = false
        lastHurtTime = 0
    }

    private fun tickActiveSession(
        target: LivingEntity,
        partialTicks: Float,
        settings: VelocityTickSettings,
        realPosition: Vec3,
        frameDeltaMs: Long,
        now: Long,
    ) {
        when {
            freezeSession.active -> freezeSession.tick(
                target,
                partialTicks,
                realPosition,
                settings.resumeDistance,
                settings.teleportDistance,
                settings.minimumFreezeDuration,
            )
            noVelocitySession.active -> {
                val released = noVelocitySession.tick(
                    realPosition,
                    frameDeltaMs,
                    settings.resumeDistance,
                    settings.teleportDistance,
                    settings.minimumFreezeDuration,
                    settings.recoveryDuration,
                    settings.maxDesync,
                )
                if (released) motionTracker.seed(realPosition, now)
            }
            else -> motionTracker.sample(realPosition, now)
        }
    }

    private fun commitPendingEffect(
        target: LivingEntity,
        partialTicks: Float,
        settings: VelocityTickSettings,
        realPosition: Vec3,
    ) {
        if (pendingFreeze && !isActive()) {
            commitEffect(target, partialTicks, settings, realPosition)
            return
        }
        if (isActive() || lastHurtTime != 0 || target.hurtTime <= 0) {
            return
        }
        pendingFreeze = true
        if (AmnesiaRuntimeBridge.delayPlayerModelRunning() && settings.mode == VelocityMode.FREEZE) {
            PlayerModelDelayState.setFrozen(true)
        }
        commitEffect(target, partialTicks, settings, realPosition)
    }

    private fun commitEffect(
        target: LivingEntity,
        partialTicks: Float,
        settings: VelocityTickSettings,
        realPosition: Vec3,
    ) {
        when (settings.mode) {
            VelocityMode.FREEZE -> freezeSession.commit(target, partialTicks)
            VelocityMode.NO_VELOCITY -> noVelocitySession.commit(
                target,
                partialTicks,
                realPosition,
                motionTracker.retainedVelocity(settings.retainedMotion),
                settings.tinyRecoil,
            )
        }
        pendingFreeze = false
    }

    private fun isActive(): Boolean = freezeSession.active || noVelocitySession.active
}

internal data class VelocityTickSettings(
    val mode: VelocityMode,
    val resumeDistance: Float,
    val teleportDistance: Float,
    val minimumFreezeDuration: Int,
    val retainedMotion: Float,
    val recoveryDuration: Int,
    val maxDesync: Float,
    val tinyRecoil: Float,
)
