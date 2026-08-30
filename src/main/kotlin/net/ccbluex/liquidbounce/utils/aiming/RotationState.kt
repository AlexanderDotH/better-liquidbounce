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

package net.ccbluex.liquidbounce.utils.aiming

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation

internal class RotationState {
    var previousRotationTarget: RotationTarget? = null
    var currentRotation: Rotation? = null
        private set
    var playerRotation: Rotation? = null
    var previousRotation: Rotation? = null
        internal set
    var actualServerRotation: Rotation = Rotation.ZERO
        internal set
    var theoreticalServerRotation: Rotation = Rotation.ZERO
        internal set

    fun updateCurrent(value: Rotation?, fallback: () -> Rotation) {
        previousRotation = if (value == null) null else currentRotation ?: fallback()
        currentRotation = value
    }

    fun trackServerRotation(rotation: Rotation, commitActual: Boolean) {
        if (commitActual) {
            actualServerRotation = rotation
        }
        theoreticalServerRotation = rotation
    }

    fun reset() {
        previousRotationTarget = null
        currentRotation = null
        playerRotation = null
        previousRotation = null
        actualServerRotation = Rotation.ZERO
        theoreticalServerRotation = Rotation.ZERO
    }
}
