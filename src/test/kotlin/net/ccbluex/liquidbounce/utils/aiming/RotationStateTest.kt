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
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RotationStateTest {
    @Test
    fun `current rotation remembers fallback then previous managed rotation`() {
        val state = RotationState()
        val fallback = Rotation(10f, 20f)
        val first = Rotation(30f, 40f)
        val second = Rotation(50f, 60f)

        state.updateCurrent(first) { fallback }
        assertEquals(fallback, state.previousRotation)
        state.updateCurrent(second) { Rotation.ZERO }
        assertEquals(first, state.previousRotation)
        state.updateCurrent(null) { Rotation.ZERO }
        assertNull(state.previousRotation)
    }

    @Test
    fun `target and player snapshots preserve exact references`() {
        val state = RotationState()
        val target = target()
        val playerRotation = Rotation(15f, 25f)

        state.previousRotationTarget = target
        state.playerRotation = playerRotation

        assertSame(target, state.previousRotationTarget)
        assertSame(playerRotation, state.playerRotation)
    }

    @Test
    fun `packet rotation always updates theoretical and only accepted packet updates actual`() {
        val state = RotationState()
        val accepted = Rotation(30f, 40f)
        val cancelled = Rotation(50f, 60f)

        state.trackServerRotation(accepted, commitActual = true)
        state.trackServerRotation(cancelled, commitActual = false)

        assertEquals(accepted, state.actualServerRotation)
        assertEquals(cancelled, state.theoreticalServerRotation)
    }

    @Test
    fun `reset clears targets interpolation and both server rotations`() {
        val state = RotationState()
        state.previousRotationTarget = target()
        state.playerRotation = Rotation(10f, 20f)
        state.updateCurrent(Rotation(30f, 40f)) { Rotation.ZERO }
        state.trackServerRotation(Rotation(50f, 60f), commitActual = true)

        state.reset()

        assertNull(state.previousRotationTarget)
        assertNull(state.playerRotation)
        assertNull(state.currentRotation)
        assertNull(state.previousRotation)
        assertEquals(Rotation.ZERO, state.actualServerRotation)
        assertEquals(Rotation.ZERO, state.theoreticalServerRotation)
    }

    private fun target() = RotationTarget(
        rotation = Rotation.ZERO,
        ticksUntilReset = 1,
        resetThreshold = 1f,
        considerInventory = false,
        movementCorrection = MovementCorrection.SILENT,
    )
}
