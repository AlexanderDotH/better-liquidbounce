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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InteractableOpenLifecycleTest {

    @Test
    fun `delivered server open is owned only after matching menu and screen are visible`() {
        val lifecycle = InteractableOpenLifecycle(confirmationGraceTicks = 2)

        assertNull(lifecycle.observe(7, tick = 20, InteractablePacketDisposition.DELIVERED, opening = true))
        assertNull(lifecycle.evaluate(tick = 20, screenContainerId = null, playerMenuId = 7))
        assertEquals(
            InteractableOpenLifecycleAction.Confirm(7),
            lifecycle.evaluate(tick = 21, screenContainerId = 7, playerMenuId = 7),
        )
    }

    @Test
    fun `queued server open is closed because the server already owns that menu`() {
        val lifecycle = InteractableOpenLifecycle(confirmationGraceTicks = 2)

        assertEquals(
            InteractableOpenLifecycleAction.CloseAndAbort(9),
            lifecycle.observe(9, tick = 20, InteractablePacketDisposition.QUEUED, opening = true),
        )
    }

    @Test
    fun `screen cancellation closes an unconfirmed server menu after its grace period`() {
        val lifecycle = InteractableOpenLifecycle(confirmationGraceTicks = 2)
        lifecycle.observe(11, tick = 20, InteractablePacketDisposition.DELIVERED, opening = true)

        assertNull(lifecycle.evaluate(tick = 21, screenContainerId = null, playerMenuId = 11))
        assertEquals(
            InteractableOpenLifecycleAction.CloseAndAbort(11),
            lifecycle.evaluate(tick = 22, screenContainerId = null, playerMenuId = 11),
        )
    }
}
