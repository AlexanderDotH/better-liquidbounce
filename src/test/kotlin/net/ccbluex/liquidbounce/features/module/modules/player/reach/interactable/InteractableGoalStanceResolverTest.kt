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

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InteractableGoalStanceResolverTest {

    @Test
    fun `only supported visible stances inside interaction range are retained`() {
        val target = BlockPos(4, 64, 0)
        val origin = Vec3(0.25, 64.0, 0.25)
        val accepted = setOf(BlockPos(2, 64, 0), BlockPos(4, 64, 2))

        val stances = resolveInteractableGoalStances(
            targetNode = target,
            origin = origin,
            interactionRange = 4.5,
            canStand = accepted::contains,
            canInteract = { it == BlockPos(2, 64, 0) },
        )

        assertEquals(listOf(BlockPos(2, 64, 0)), stances.map { it.node })
        assertTrue(stances.all { stance -> stance.position.distanceTo(Vec3(4.5, 64.5, 0.5)) <= 4.5 })
    }

    @Test
    fun `valid stances are ordered from the exact fractional origin`() {
        val target = BlockPos(4, 64, 0)
        val near = BlockPos(2, 64, 0)
        val far = BlockPos(5, 64, 2)

        val stances = resolveInteractableGoalStances(
            targetNode = target,
            origin = Vec3(0.9, 64.0, 0.5),
            interactionRange = 4.5,
            canStand = { it == near || it == far },
            canInteract = { true },
        )

        assertEquals(listOf(near, far), stances.map { it.node })
    }
}
