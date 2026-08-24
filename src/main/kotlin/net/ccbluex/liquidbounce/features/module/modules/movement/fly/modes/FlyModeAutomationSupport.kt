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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationInput
import net.ccbluex.liquidbounce.utils.entity.getMovementDirectionOfInput
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

internal fun LocalPlayer.flyAutomationDirectionalInput(): DirectionalInput {
    return FlyAutomationInput.directional(DirectionalInput(input))
}

internal fun LocalPlayer.flyAutomationMoving(): Boolean = flyAutomationDirectionalInput().isMoving

internal fun flyAutomationJump(physical: Boolean): Boolean = FlyAutomationInput.jump(physical)

internal fun flyAutomationSneak(physical: Boolean): Boolean = FlyAutomationInput.sneak(physical)

internal fun flyAutomationYaw(physical: Float): Float {
    val physicalInput = DirectionalInput(Minecraft.getInstance().options)
    return FlyAutomationInput.desiredYaw(physical, physicalInput)
}

internal fun Vec3.withFlyAutomationStrafe(
    player: LocalPlayer,
    speed: Double = horizontalDistance(),
    strength: Double = 1.0,
): Vec3 {
    val physicalInput = DirectionalInput(Minecraft.getInstance().options)
    val resolvedInput = FlyAutomationInput.directional(physicalInput)
    val physicalYaw = player.getMovementDirectionOfInput(physicalInput)

    return withStrafe(
        speed = speed,
        strength = strength,
        input = resolvedInput,
        yaw = FlyAutomationInput.desiredYaw(physicalYaw, physicalInput),
    )
}

internal class FlyAutomaticEndSignal {

    private var pending: FlyAutomationEnd? = null

    fun reset() {
        pending = null
    }

    fun mark(reason: String) {
        pending = FlyAutomationEnd(reason)
    }

    fun consume(): FlyAutomationEnd? = pending.also { pending = null }

}
