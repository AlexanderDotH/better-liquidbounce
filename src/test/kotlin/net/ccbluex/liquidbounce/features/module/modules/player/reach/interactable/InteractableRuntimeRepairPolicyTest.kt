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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableEntityKind
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InteractableRuntimeRepairPolicyTest {

    @Test
    fun `one-block transitions use a collision sweep that clears the step before crossing it`() {
        val origin = Vec3(0.5, 64.0, 0.5)
        val higher = Vec3(1.5, 65.0, 0.5)
        val lower = Vec3(2.5, 64.0, 0.5)

        assertEquals(listOf(Vec3(0.5, 65.0, 0.5), higher), interactableSweepWaypoints(origin, higher))
        assertEquals(listOf(Vec3(2.5, 65.0, 0.5), lower), interactableSweepWaypoints(higher, lower))
    }

    @Test
    fun `outline candidates include every face center instead of only block center`() {
        val points = interactionOutlinePoints(
            BlockPos(10, 64, -2),
            listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
        )

        assertTrue(Vec3(10.0, 64.5, -1.5) in points)
        assertTrue(Vec3(11.0, 64.5, -1.5) in points)
        assertTrue(Vec3(10.5, 65.0, -1.5) in points)
    }

    @Test
    fun `special vanilla menu blocks and container boats use their required interaction policy`() {
        assertTrue(isInteractableMenuAvailable(hasMenuProvider = false, opensMenuWithoutProvider = true))
        assertFalse(isInteractableMenuAvailable(hasMenuProvider = false, opensMenuWithoutProvider = false))
        assertTrue(requiresSecondaryUse(InteractableEntityKind.CHEST_BOAT))
        assertTrue(requiresSecondaryUse(InteractableEntityKind.CHEST_RAFT))
        assertFalse(requiresSecondaryUse(InteractableEntityKind.CONTAINER_MINECART))
    }

    @Test
    fun `every emitted hand interaction packet must reach the final pipeline`() {
        assertTrue(interactionDeliveryConfirmed(true, listOf(InteractablePacketDisposition.DELIVERED)))
        assertTrue(
            interactionDeliveryConfirmed(
                true,
                listOf(InteractablePacketDisposition.DELIVERED, InteractablePacketDisposition.DELIVERED),
            ),
        )
        assertFalse(interactionDeliveryConfirmed(false, listOf(InteractablePacketDisposition.DELIVERED)))
        assertFalse(interactionDeliveryConfirmed(true, emptyList()))
        assertFalse(
            interactionDeliveryConfirmed(
                true,
                listOf(InteractablePacketDisposition.QUEUED, InteractablePacketDisposition.DELIVERED),
            ),
        )
        assertFalse(
            interactionDeliveryConfirmed(
                true,
                listOf(InteractablePacketDisposition.DELIVERED, InteractablePacketDisposition.CANCELLED),
            ),
        )
    }
}
