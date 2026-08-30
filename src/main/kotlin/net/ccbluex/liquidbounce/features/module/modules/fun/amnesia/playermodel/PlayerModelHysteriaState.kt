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
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

object PlayerModelHysteriaState {

    private val coordinator = HysteriaCoordinator()

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        switchInterval: Int,
        hysteriaSmoothDuration: Int,
        @Suppress("UNUSED_PARAMETER") returnSmoothDuration: Int,
        combatSnapDuration: Int,
        range: Float,
        @Suppress("UNUSED_PARAMETER") randomWhenEmpty: Boolean,
        delayRotationUpdateInterval: Int? = null,
        delayRotationSmoothDuration: Int? = null,
    ) = coordinator.tick(
        target,
        partialTicks,
        HysteriaTickSettings(
            switchInterval,
            hysteriaSmoothDuration,
            combatSnapDuration,
            range,
            delayRotationUpdateInterval,
            delayRotationSmoothDuration,
        ),
    )

    fun triggerCombatSnapFromDamage(
        target: LivingEntity,
        entity: LivingEntity,
        partialTicks: Float,
    ) = coordinator.triggerCombatSnapFromDamage(target, entity, partialTicks)

    fun getTransform(entity: Entity): PlayerModelVisualTransform? = coordinator.transform(entity)

    fun reset() = coordinator.reset()
}
