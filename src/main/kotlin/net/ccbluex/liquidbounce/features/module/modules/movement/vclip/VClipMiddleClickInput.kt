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

internal class VClipMiddleClickInput {

    private val inputController = VClipInputController()

    var isHeld = false
        private set

    fun press() {
        if (isHeld) return
        isHeld = true
        inputController.reset()
    }

    fun release() {
        isHeld = false
        inputController.reset()
    }

    fun resolveDirection(
        jumpPressed: Boolean,
        shiftPressed: Boolean,
        repeatDelayTicks: Int,
    ): VClipDirection? {
        if (!isHeld) return null
        return inputController.resolve(jumpPressed, shiftPressed, repeatDelayTicks)
    }

    fun reset() = release()
}

internal data class VClipInputSuppression(
    val jump: Boolean,
    val sneak: Boolean,
) {
    companion object {
        fun resolve(smartLockActive: Boolean, modifierHeld: Boolean): VClipInputSuppression {
            if (!smartLockActive || modifierHeld) {
                return VClipInputSuppression(jump = true, sneak = true)
            }

            return VClipInputSuppression(jump = false, sneak = false)
        }
    }
}
