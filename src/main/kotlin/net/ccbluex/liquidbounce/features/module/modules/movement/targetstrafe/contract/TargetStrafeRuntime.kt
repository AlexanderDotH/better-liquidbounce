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

package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract

import net.minecraft.world.entity.LivingEntity

internal object TargetStrafeRuntime {

    private lateinit var followRangeProvider: () -> Float
    private lateinit var orbitRangeProvider: () -> Float
    private lateinit var requirementsMetProvider: () -> Boolean
    private lateinit var firstTargetProvider: () -> LivingEntity?

    lateinit var renderState: TargetStrafeRenderState
        private set

    val followRange: Float
        get() = followRangeProvider()
    val orbitRange: Float
        get() = orbitRangeProvider()
    val requirementsMet: Boolean
        get() = requirementsMetProvider()

    fun bind(
        renderState: TargetStrafeRenderState,
        followRangeProvider: () -> Float,
        orbitRangeProvider: () -> Float,
        requirementsMetProvider: () -> Boolean,
        firstTargetProvider: () -> LivingEntity?,
    ) {
        this.renderState = renderState
        this.followRangeProvider = followRangeProvider
        this.orbitRangeProvider = orbitRangeProvider
        this.requirementsMetProvider = requirementsMetProvider
        this.firstTargetProvider = firstTargetProvider
    }

    fun firstTarget(): LivingEntity? = firstTargetProvider()

}
