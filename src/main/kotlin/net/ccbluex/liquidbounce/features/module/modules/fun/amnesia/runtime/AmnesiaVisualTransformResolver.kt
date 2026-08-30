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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaVisualEffects
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.DelayedTransform
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelDelayState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeBhopState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeCriticalsState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeVelocityState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelHysteriaState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal object AmnesiaVisualTransformResolver {

    fun resolve(entity: LivingEntity, partialTicks: Float): PlayerModelVisualTransform? {
        val delayed = delayedTransform(entity)
        val aura = fakeKillAuraTransform(entity)
        val basePosition = getBaseVisualPosition(entity, partialTicks)
        val velocityPositionActive = AmnesiaRuntimeBridge.fakeVelocityRunning() &&
            PlayerModelFakeVelocityState.hasPositionOverride(entity)
        val effects = AmnesiaRuntimeBridge.visualEffects(
            entity,
            partialTicks,
            basePosition,
            velocityPositionActive,
        )
        val delayedVisual = delayed?.toVisualTransform()
        val rotation = selectRotationSource(aura, effects, delayedVisual)
        val position = effects.criticals?.position ?: effects.bhop?.position ?: effects.jesus?.position
            ?: delayed?.pos
        val base = composeBaseTransform(entity, partialTicks, rotation, position)

        return if (AmnesiaRuntimeBridge.fakeVelocityRunning()) {
            PlayerModelFakeVelocityState.getTransform(entity, partialTicks, base) ?: base
        } else {
            base
        }
    }

    fun auxiliaryPosition(entity: LivingEntity, partialTicks: Float): Vec3? {
        if (AmnesiaRuntimeBridge.fakeVelocityRunning()) {
            PlayerModelFakeVelocityState.getVisualPosition(entity)?.let { return it }
        }

        return getBaseVisualPosition(entity, partialTicks)
    }

    private fun delayedTransform(entity: LivingEntity): DelayedTransform? {
        if (!AmnesiaRuntimeBridge.delayPlayerModelRunning()) {
            return null
        }

        return PlayerModelDelayState.getTransform(entity)
    }

    private fun fakeKillAuraTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!AmnesiaRuntimeBridge.fakeKillAuraRunning()) {
            return null
        }

        return PlayerModelHysteriaState.getTransform(entity)
    }

    private fun getBaseVisualPosition(entity: LivingEntity, partialTicks: Float): Vec3 {
        if (AmnesiaRuntimeBridge.delayPlayerModelRunning()) {
            PlayerModelDelayState.getTransform(entity)?.pos?.let { return it }
        }

        return entity.interpolateCurrentPosition(partialTicks)
    }

    private fun selectRotationSource(
        aura: PlayerModelVisualTransform?,
        effects: AmnesiaVisualEffects,
        delayed: PlayerModelVisualTransform?,
    ): PlayerModelVisualTransform? {
        val criticalsRotation = effects.criticals?.takeIf { effects.criticalsHasRotation }
        val bhopRotation = effects.bhop?.takeIf { effects.bhopHasRotation }
        return aura ?: effects.spinbot ?: criticalsRotation ?: effects.scaffold ?: bhopRotation ?: delayed
            ?: effects.criticals
    }

    private fun composeBaseTransform(
        entity: LivingEntity,
        partialTicks: Float,
        rotationSource: PlayerModelVisualTransform?,
        visualPosition: Vec3?,
    ): PlayerModelVisualTransform? = when {
        rotationSource != null -> rotationSource.copy(position = visualPosition)
        visualPosition != null -> currentTransform(entity, partialTicks).copy(position = visualPosition)
        else -> null
    }

    private fun currentTransform(entity: LivingEntity, partialTicks: Float) = PlayerModelVisualTransform(
        position = null,
        bodyYaw = entity.interpolateBodyYaw(partialTicks),
        headYaw = entity.interpolateHeadYaw(partialTicks),
        pitch = entity.interpolatePitch(partialTicks),
    )

    private fun DelayedTransform.toVisualTransform() = PlayerModelVisualTransform(
        position = pos,
        bodyYaw = bodyYaw,
        headYaw = headYaw,
        pitch = pitch,
    )
}
