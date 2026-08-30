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
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.VelocityMode
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

object PlayerModelFakeVelocityState {

    private val coordinator = VelocityCoordinator()

    fun queueFreezeFromDamage(target: LivingEntity) = coordinator.queueFreezeFromDamage(target)

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        mode: VelocityMode,
        resumeDistance: Float,
        teleportDistance: Float,
        minFreezeDuration: Int,
        retainedMotion: Float,
        recoveryDuration: Int,
        maxDesync: Float,
        tinyRecoil: Float,
    ) = coordinator.tick(
        target,
        partialTicks,
        VelocityTickSettings(
            mode,
            resumeDistance,
            teleportDistance,
            minFreezeDuration,
            retainedMotion,
            recoveryDuration,
            maxDesync,
            tinyRecoil,
        ),
    )

    fun getTransform(
        entity: LivingEntity,
        partialTicks: Float,
        base: PlayerModelVisualTransform?,
    ): PlayerModelVisualTransform? = coordinator.transform(entity, partialTicks, base)

    fun getVisualPosition(entity: LivingEntity): Vec3? = coordinator.visualPosition(entity)

    fun hasPositionOverride(entity: LivingEntity): Boolean = getVisualPosition(entity) != null

    fun reset() = coordinator.reset()
}
