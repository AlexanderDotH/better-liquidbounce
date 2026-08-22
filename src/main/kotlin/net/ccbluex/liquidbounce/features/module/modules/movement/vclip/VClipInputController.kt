/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

internal enum class VClipDirection(val verticalSign: Int) {
    UP(1),
    DOWN(-1),
}

internal class VClipInputController {

    private var cooldownTicks = 0

    fun resolve(
        spacePressed: Boolean,
        shiftPressed: Boolean,
        repeatDelayTicks: Int,
    ): VClipDirection? {
        require(repeatDelayTicks >= 0) { "Repeat delay cannot be negative" }

        if (cooldownTicks > 0) {
            cooldownTicks--
            return null
        }

        val direction = inputDirection(spacePressed, shiftPressed) ?: return null
        cooldownTicks = repeatDelayTicks
        return direction
    }

    fun reset() {
        cooldownTicks = 0
    }

    private fun inputDirection(spacePressed: Boolean, shiftPressed: Boolean) = when {
        spacePressed == shiftPressed -> null
        spacePressed -> VClipDirection.UP
        else -> VClipDirection.DOWN
    }
}
