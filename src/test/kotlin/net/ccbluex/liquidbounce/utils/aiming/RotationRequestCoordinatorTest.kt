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

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RotationRequestCoordinatorTest {
    @Test
    fun `higher priority request wins without discarding lower priority request`() {
        val coordinator = RotationRequestCoordinator()
        val normal = target(10f)
        val important = target(20f)

        coordinator.request(normal, Priority.NORMAL, Owner())
        coordinator.request(important, Priority.IMPORTANT_FOR_USAGE_2, Owner())

        assertSame(important, coordinator.activeTarget)
    }

    @Test
    fun `new request from same provider replaces the previous request`() {
        val coordinator = RotationRequestCoordinator()
        val owner = Owner()
        val previous = target(10f)
        val replacement = target(20f)

        coordinator.request(previous, Priority.IMPORTANT_FOR_USAGE_2, owner)
        coordinator.request(replacement, Priority.NORMAL, owner)

        assertSame(replacement, coordinator.activeTarget)
    }

    @Test
    fun `request expires after its exact reset tick count`() {
        val coordinator = RotationRequestCoordinator()
        val target = target(10f, ticksUntilReset = 2)
        coordinator.request(target, Priority.NORMAL, Owner())

        coordinator.tick()
        assertSame(target, coordinator.activeTarget)
        coordinator.tick()
        assertNull(coordinator.activeTarget)
    }

    @Test
    fun `change look request keeps the historical single tick lifetime`() {
        val coordinator = RotationRequestCoordinator()
        coordinator.request(
            target(10f, ticksUntilReset = 20, movementCorrection = MovementCorrection.CHANGE_LOOK),
            Priority.NORMAL,
            Owner(),
        )

        coordinator.tick()

        assertNull(coordinator.activeTarget)
    }

    @Test
    fun `clear removes all requests and resets request time`() {
        val coordinator = RotationRequestCoordinator()
        coordinator.request(target(10f), Priority.NORMAL, Owner())

        coordinator.clear()

        assertNull(coordinator.activeTarget)
    }

    private fun target(
        yaw: Float,
        ticksUntilReset: Int = 5,
        movementCorrection: MovementCorrection = MovementCorrection.SILENT,
    ) = RotationTarget(
        rotation = Rotation(yaw, 0f),
        ticksUntilReset = ticksUntilReset,
        resetThreshold = 1f,
        considerInventory = false,
        movementCorrection = movementCorrection,
    )

    private class Owner : EventListener {
        override val running = true
    }
}
