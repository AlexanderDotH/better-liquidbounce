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

import net.minecraft.world.phys.Vec3

internal object TargetStrafeEnvironment {
    private lateinit var speedRunningProvider: () -> Boolean
    private lateinit var lowHopShouldStrafeProvider: () -> Boolean
    private lateinit var killAuraRunningProvider: () -> Boolean

    val speedRunning: Boolean
        get() = speedRunningProvider()
    val lowHopShouldStrafe: Boolean
        get() = lowHopShouldStrafeProvider()
    val killAuraRunning: Boolean
        get() = killAuraRunningProvider()

    fun bindSpeedRunning(provider: () -> Boolean) {
        speedRunningProvider = provider
    }

    fun bindLowHopShouldStrafe(provider: () -> Boolean) {
        lowHopShouldStrafeProvider = provider
    }

    fun bindKillAuraRunning(provider: () -> Boolean) {
        killAuraRunningProvider = provider
    }
}

internal object TargetStrafePointValidation {
    private lateinit var validator: (Vec3) -> Boolean

    fun bind(validator: (Vec3) -> Boolean) {
        this.validator = validator
    }

    fun validatePoint(point: Vec3): Boolean = validator(point)
}

internal object TargetStrafePlannerConfiguration {
    private lateinit var controlDirectionProvider: () -> Boolean
    private lateinit var adaptiveRangeEnabledProvider: () -> Boolean
    private lateinit var adaptiveRangeStepProvider: () -> Float
    private lateinit var adaptiveRangeMaximumProvider: () -> Float

    val controlDirection: Boolean
        get() = controlDirectionProvider()
    val adaptiveRangeEnabled: Boolean
        get() = adaptiveRangeEnabledProvider()
    val adaptiveRangeStep: Float
        get() = adaptiveRangeStepProvider()
    val adaptiveRangeMaximum: Float
        get() = adaptiveRangeMaximumProvider()

    fun bind(
        controlDirectionProvider: () -> Boolean,
        adaptiveRangeEnabledProvider: () -> Boolean,
        adaptiveRangeStepProvider: () -> Float,
        adaptiveRangeMaximumProvider: () -> Float,
    ) {
        this.controlDirectionProvider = controlDirectionProvider
        this.adaptiveRangeEnabledProvider = adaptiveRangeEnabledProvider
        this.adaptiveRangeStepProvider = adaptiveRangeStepProvider
        this.adaptiveRangeMaximumProvider = adaptiveRangeMaximumProvider
    }
}
