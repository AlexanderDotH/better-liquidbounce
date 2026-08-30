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
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal object VelocityVisualCapture {

    fun position(target: LivingEntity, partialTicks: Float): Vec3 {
        if (AmnesiaRuntimeBridge.delayPlayerModelRunning()) {
            PlayerModelDelayState.getTransform(target)?.pos?.let { return it }
        }
        return target.interpolateCurrentPosition(partialTicks)
    }

    fun rotation(target: LivingEntity, partialTicks: Float): VelocityModelRotation {
        if (AmnesiaRuntimeBridge.fakeKillAuraRunning()) {
            PlayerModelHysteriaState.getTransform(target)?.let {
                return VelocityModelRotation(it.bodyYaw, it.headYaw, it.pitch)
            }
        }
        if (AmnesiaRuntimeBridge.delayPlayerModelRunning()) {
            PlayerModelDelayState.getTransform(target)?.let {
                return VelocityModelRotation(it.bodyYaw, it.headYaw, it.pitch)
            }
        }
        return VelocityModelRotation(
            target.interpolateBodyYaw(partialTicks),
            target.interpolateHeadYaw(partialTicks),
            target.interpolatePitch(partialTicks),
        )
    }

    fun currentRotationFallback(entity: LivingEntity, partialTicks: Float) = PlayerModelVisualTransform(
        position = null,
        bodyYaw = entity.interpolateBodyYaw(partialTicks),
        headYaw = entity.interpolateHeadYaw(partialTicks),
        pitch = entity.interpolatePitch(partialTicks),
    )
}

internal data class VelocityModelRotation(
    val bodyYaw: Float,
    val headYaw: Float,
    val pitch: Float,
)
