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
package net.ccbluex.liquidbounce.utils.entity

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Holder
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

internal class FallingPlayerMotion(
    private val player: LocalPlayer,
    private var motionX: Double,
    private var motionY: Double,
    private var motionZ: Double,
    private val yRot: Float,
) {
    private var simulatedTicks: Int = 0

    fun calculateMovementForTick(rotationVec: Vec3): Vec3 {
        if (player.isFallFlying) {
            calculateElytraTick(rotationVec)
        } else {
            applyAirInput()
        }

        return Vec3(motionX, motionY, motionZ)
    }

    fun finishTick(intendedMovement: Vec3, resolvedMovement: Vec3) {
        applyCollisionResponse(intendedMovement, resolvedMovement)
        if (!player.isFallFlying) {
            applyFreeFallForces()
        }
        simulatedTicks++
    }

    /**
     * Applies the player's air input before movement, matching vanilla's
     * {@code LivingEntity.handleRelativeFrictionAndCalculateMovement()}.
     */
    private fun applyAirInput() {
        val speed = player.speed * 0.1f
        if (speed > 0f) {
            val inputVec = Entity.getInputVector(playerMovementInput(), speed, yRot)
            motionX += inputVec.x
            motionZ += inputVec.z
        }
    }

    /**
     * Applies gravity and drag after movement, matching vanilla's
     * {@code LivingEntity.travelInAir()} ordering.
     */
    private fun applyFreeFallForces() {
        motionY -= effectiveGravity()
        motionX *= LivingEntity.BASE_HORIZONTAL_AIR_DRAG.toDouble()
        motionY *= LivingEntity.BASE_VERTICAL_AIR_DRAG.toDouble()
        motionZ *= LivingEntity.BASE_HORIZONTAL_AIR_DRAG.toDouble()
    }

    /**
     * Simulates one tick of elytra flight physics,
     * matching Minecraft 26.2 {@code LivingEntity.updateFallFlyingMovement()}.
     */
    private fun calculateElytraTick(rotationVec: Vec3) {
        val pitchRad: Double = this.player.xRot.toDouble() * Mth.DEG_TO_RAD

        val lookHorLength = sqrt(rotationVec.x * rotationVec.x + rotationVec.z * rotationVec.z)
        val moveHorLength = sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ)
        val gravity = effectiveGravity()

        val m = rotationVec.length()
        var n = Mth.cos(pitchRad)

        n = (n.toDouble() * n.toDouble() * 1.0.coerceAtMost(m / 0.4)).toFloat()

        var vec3d5 = Vec3(this.motionX, this.motionY + gravity * (-1.0 + n.toDouble() * 0.75), this.motionZ)

        var q: Double
        if (vec3d5.y < 0.0 && lookHorLength > 0.0) {
            q = vec3d5.y * -0.1 * n.toDouble()
            vec3d5 = vec3d5.add(rotationVec.x * q / lookHorLength, q, rotationVec.z * q / lookHorLength)
        }

        if (pitchRad < 0.0 && lookHorLength > 0.0) {
            q = moveHorLength * (-Mth.sin(pitchRad)).toDouble() * 0.04
            vec3d5 = vec3d5.add(-rotationVec.x * q / lookHorLength, q * 3.2, -rotationVec.z * q / lookHorLength)
        }

        if (lookHorLength > 0.0) {
            vec3d5 = vec3d5.add(
                (rotationVec.x / lookHorLength * moveHorLength - vec3d5.x) * 0.1,
                0.0,
                (rotationVec.z / lookHorLength * moveHorLength - vec3d5.z) * 0.1,
            )
        }

        this.motionX = vec3d5.x * LivingEntity.ELYTRA_HORIZONTAL_AIR_DRAG.toDouble()
        this.motionY = vec3d5.y * LivingEntity.ELYTRA_VERTICAL_AIR_DRAG.toDouble()
        this.motionZ = vec3d5.z * LivingEntity.ELYTRA_HORIZONTAL_AIR_DRAG.toDouble()
    }

    private fun effectiveGravity(): Double {
        val rawGravity = player.gravity
        return if (motionY <= 0.0 && hasStatusEffect(MobEffects.SLOW_FALLING)) {
            minOf(rawGravity, 0.01)
        } else {
            rawGravity
        }
    }

    private fun hasStatusEffect(effect: Holder<MobEffect>): Boolean {
        val instance = player.getEffect(effect) ?: return false
        return instance.duration >= simulatedTicks
    }

    private fun playerMovementInput() = Vec3(
        player.input.movementSideways.toDouble() * 0.98,
        0.0,
        player.input.movementForward.toDouble() * 0.98,
    )

    /**
     * Matches the zero-restitution player path in Minecraft 26.2
     * {@code Entity.restituteMovementAfterCollisions()}.
     */
    private fun applyCollisionResponse(intendedMovement: Vec3, resolvedMovement: Vec3) {
        motionX = if (resolvedMovement.x != intendedMovement.x) 0.0 else motionX
        motionY = if (resolvedMovement.y != intendedMovement.y) 0.0 else motionY
        motionZ = if (resolvedMovement.z != intendedMovement.z) 0.0 else motionZ
    }
}
