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
package net.ccbluex.liquidbounce.features.litematica.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LitematicaConflictPolicyTest {

    @Test
    fun `allows printing when no conflicting client activity is present`() {
        assertNull(LitematicaConflictPolicy.firstPause(LitematicaConflictSnapshot()))
    }

    @Test
    fun `maps every protected client activity to a deterministic pause reason`() {
        val cases = listOf(
            LitematicaConflictSnapshot(packetMineRunning = true) to LitematicaConflict.PACKET_MINE,
            LitematicaConflictSnapshot(scaffoldRunning = true) to LitematicaConflict.SCAFFOLD,
            LitematicaConflictSnapshot(autoBuildRunning = true) to LitematicaConflict.AUTO_BUILD,
            LitematicaConflictSnapshot(fuckerRunning = true) to LitematicaConflict.FUCKER,
            LitematicaConflictSnapshot(blinkRunning = true) to LitematicaConflict.BLINK,
            LitematicaConflictSnapshot(foreignSilentHotbar = true) to LitematicaConflict.FOREIGN_SILENT_HOTBAR,
            LitematicaConflictSnapshot(containerScreenOpen = true) to LitematicaConflict.CONTAINER_SCREEN,
            LitematicaConflictSnapshot(usingItem = true) to LitematicaConflict.ITEM_USE,
            LitematicaConflictSnapshot(rotationUnavailable = true) to LitematicaConflict.ROTATION_UNAVAILABLE,
        )

        cases.forEach { (snapshot, expected) ->
            assertEquals(expected, LitematicaConflictPolicy.firstPause(snapshot))
        }
    }

    @Test
    fun `reports module conflicts before transient interaction conflicts`() {
        val snapshot = LitematicaConflictSnapshot(
            packetMineRunning = true,
            foreignSilentHotbar = true,
            containerScreenOpen = true,
            rotationUnavailable = true,
        )

        assertEquals(LitematicaConflict.PACKET_MINE, LitematicaConflictPolicy.firstPause(snapshot))
    }
}
