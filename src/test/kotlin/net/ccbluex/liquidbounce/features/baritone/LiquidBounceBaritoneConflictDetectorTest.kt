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
package net.ccbluex.liquidbounce.features.baritone

import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LiquidBounceBaritoneConflictDetectorTest {

    @Test
    fun `user input has the highest automatic precedence`() {
        val causes = collectLiquidBouncePauseCauses(
            userInput = true,
            rotationOwned = true,
            hotbarOwned = true,
            inventoryOwned = true,
            blinkActive = true,
            remoteMovementOwned = true,
            movementOwners = listOf("Scaffold"),
        )

        assertEquals(BaritonePauseReason.USER_INPUT, causes.maxBy { it.reason.precedence }.reason)
    }

    @Test
    fun `movement owners are stable deduplicated causes`() {
        val causes = collectLiquidBouncePauseCauses(
            userInput = false,
            rotationOwned = false,
            hotbarOwned = false,
            inventoryOwned = false,
            blinkActive = false,
            remoteMovementOwned = false,
            movementOwners = listOf("Speed", "FightBot", "Speed", ""),
        )

        assertEquals(listOf("FightBot", "Speed"), causes.mapNotNull { it.owner })
        assertFalse(causes.any { it.reason != BaritonePauseReason.MOVEMENT_OWNER })
    }

    @Test
    fun `only the Fly owner backed by an active Baritone lease is exempt`() {
        val leased = collectLiquidBouncePauseCauses(
            userInput = false,
            rotationOwned = false,
            hotbarOwned = false,
            inventoryOwned = false,
            blinkActive = false,
            remoteMovementOwned = false,
            movementOwners = listOf("Fly", "Speed"),
            exemptLeasedFly = true,
        )
        val manual = collectLiquidBouncePauseCauses(
            userInput = false,
            rotationOwned = false,
            hotbarOwned = false,
            inventoryOwned = false,
            blinkActive = false,
            remoteMovementOwned = false,
            movementOwners = listOf("Fly"),
            exemptLeasedFly = false,
        )

        assertEquals(listOf("Speed"), leased.mapNotNull { it.owner })
        assertEquals(listOf("Fly"), manual.mapNotNull { it.owner })
    }
}
