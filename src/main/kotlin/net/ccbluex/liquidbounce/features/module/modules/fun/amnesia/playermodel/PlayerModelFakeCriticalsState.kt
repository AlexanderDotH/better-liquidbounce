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
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.CriticalsMode

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.client.model.HumanoidModel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

object PlayerModelFakeCriticalsState {

    private var targetEntityId: Int? = null
    private var victimEntityId: Int? = null
    private var activeUntil = 0L
    private var startedAt = 0L
    private var lastTriggerAt = 0L
    private var windowMs = 250
    private var mode = CriticalsMode.BOTH
    private var microHopHeight = 0.12f
    private var packetJitter = 0.06f
    private var rotateToVictim = true
    private var swing = true
    private var bodyYaw = 0f
    private var headYaw = 0f
    private var pitch = 0f

    fun tick(target: LivingEntity) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
            return
        }

        targetEntityId = target.id
        if (!isActive()) {
            victimEntityId = null
        }
    }

    fun trigger(
        target: LivingEntity,
        victim: LivingEntity,
        partialTicks: Float,
        mode: CriticalsMode,
        triggerWindow: Int,
        cooldown: Int,
        criticalParticles: Int,
        magicParticles: Int,
        microHopHeight: Float,
        packetJitter: Float,
        rotateToVictim: Boolean,
        swing: Boolean,
    ) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < cooldown.coerceAtLeast(0)) {
            return
        }

        targetEntityId = target.id
        victimEntityId = victim.id
        startedAt = now
        activeUntil = now + triggerWindow.coerceAtLeast(1)
        lastTriggerAt = now
        windowMs = triggerWindow.coerceAtLeast(1)
        this.mode = mode
        this.microHopHeight = microHopHeight
        this.packetJitter = packetJitter
        this.rotateToVictim = rotateToVictim
        this.swing = swing

        if (rotateToVictim) {
            aimAtVictim(target, victim, partialTicks)
        }

        spawnParticles(victim, criticalParticles, magicParticles)
    }

    fun getTransform(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ): PlayerModelVisualTransform? {
        if (entity.id != targetEntityId || !isActive()) {
            return null
        }

        val offset = if (velocityPositionActive) 0.0 else currentVerticalOffset().toDouble()
        if (!rotateToVictim && offset == 0.0) {
            return null
        }

        return PlayerModelVisualTransform(
            position = if (offset != 0.0) basePosition.add(0.0, offset, 0.0) else null,
            bodyYaw = if (rotateToVictim) bodyYaw else entity.interpolateBodyYaw(partialTicks),
            headYaw = if (rotateToVictim) headYaw else entity.interpolateHeadYaw(partialTicks),
            pitch = if (rotateToVictim) pitch else entity.interpolatePitch(partialTicks),
        )
    }

    fun hasRotation(entity: LivingEntity): Boolean = entity.id == targetEntityId && isActive() && rotateToVictim

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (entity.id != targetEntityId || !isActive() || !swing) {
            return null
        }

        return PlayerModelActionState(
            swingProgress = CriticalsAnimationMath.swingProgress(progress()),
            armPose = HumanoidModel.ArmPose.ITEM,
        )
    }

    fun reset() {
        targetEntityId = null
        victimEntityId = null
        activeUntil = 0L
        startedAt = 0L
        lastTriggerAt = 0L
        windowMs = 250
        mode = CriticalsMode.BOTH
        microHopHeight = 0.12f
        packetJitter = 0.06f
        rotateToVictim = true
        swing = true
        bodyYaw = 0f
        headYaw = 0f
        pitch = 0f
    }

    private fun aimAtVictim(target: LivingEntity, victim: LivingEntity, partialTicks: Float) {
        val rotation = Rotation.lookingAt(victim.eyePosition, target.getEyePosition(partialTicks))
        bodyYaw = rotation.yaw
        headYaw = rotation.yaw
        pitch = rotation.pitch.coerceIn(-90f, 90f)
    }

    private fun spawnParticles(victim: Entity, criticalParticles: Int, magicParticles: Int) {
        val localPlayer = mc.player ?: return

        repeat(criticalParticles.coerceAtLeast(0)) {
            localPlayer.crit(victim)
        }

        repeat(magicParticles.coerceAtLeast(0)) {
            localPlayer.magicCrit(victim)
        }
    }

    private fun currentVerticalOffset(): Float =
        CriticalsAnimationMath.verticalOffset(mode, microHopHeight, packetJitter, progress())

    private fun progress(): Float {
        val elapsed = System.currentTimeMillis() - startedAt
        return (elapsed.toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
    }

    private fun isActive(): Boolean = System.currentTimeMillis() <= activeUntil
}
