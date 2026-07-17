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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.revive

import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.copy
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects

private const val REVIVE_BASE_MOVE_SPEED = 0.2873

internal fun LocalPlayer.reviveBaseMoveSpeed(): Double {
    val speedLevel = (getEffect(MobEffects.SPEED)?.amplifier ?: -1) + 1
    return REVIVE_BASE_MOVE_SPEED * (1.0 + 0.2 * speedLevel.coerceAtLeast(0))
}

internal fun LocalPlayer.setReviveSpeed(speed: Double) {
    deltaMovement = deltaMovement.withStrafe(speed = speed)
}

internal fun LocalPlayer.offsetReviveMotionY(offset: Double) {
    deltaMovement = deltaMovement.copy(y = deltaMovement.y + offset)
}

internal fun requestReviveTimer(timerSpeed: Float) {
    Timer.requestTimerSpeed(timerSpeed, Priority.IMPORTANT_FOR_USAGE_1, ModuleSpeed)
}
