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
package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.kotlin.Priority

/**
 * Continuously spins the server-visible head yaw while keeping a configurable pitch.
 */
object ModuleSpinBot : ClientModule("SpinBot", ModuleCategories.FUN) {

    private val pitch by float("Pitch", 90f, -90f..90f, "°")
    private val speed by int("Speed", 50, -180..180, "°/tick")

    // Keep raw spin steps intact instead of exposing smoothing that can dampen the effect.
    private val rotations = RotationsValueGroup(this)
    private val spinBotRotation = SpinBotRotation()

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        val yaw = spinBotRotation.nextYaw(player.yRot, speed.toFloat())
        val target = rotations.toRotationTarget(Rotation(yaw, pitch))

        RotationManager.setRotationTarget(target, Priority.NOT_IMPORTANT, this@ModuleSpinBot)
    }

    override fun onDisabled() {
        spinBotRotation.reset()
    }

}
