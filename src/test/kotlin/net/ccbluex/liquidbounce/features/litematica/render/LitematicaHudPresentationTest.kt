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
package net.ccbluex.liquidbounce.features.litematica.render

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LitematicaHudPresentationTest {

    @Test
    fun `active printer HUD contains all local and optional verifier status`() {
        val snapshot = LitematicaHudSnapshot(
            placementName = "Starter House",
            activationMode = "Continuous",
            counts = LitematicaPlacementCounts(correct = 12, missing = 3, wrong = 2, extra = 1, pending = 4),
            currentTarget = BlockPos(4, 65, -2),
            missingMaterial = "minecraft:oak_stairs x2",
            pauseReason = "Scaffold active",
            retryCount = 7,
            verifierTotals = LitematicaVerifierTotals(correct = 90, missing = 5, wrong = 3, extra = 2),
        )

        val presentation = LitematicaHudPresenter.present(snapshot)

        assertEquals(
            listOf(
                LitematicaHudLine("Litematica | Starter House | Continuous", LitematicaHudTone.TITLE),
                LitematicaHudLine("Local: 12 correct | 3 missing | 2 wrong | 1 extra | 4 pending"),
                LitematicaHudLine("Target: 4, 65, -2"),
                LitematicaHudLine("Missing: minecraft:oak_stairs x2", LitematicaHudTone.WARNING),
                LitematicaHudLine("Paused: Scaffold active", LitematicaHudTone.ERROR),
                LitematicaHudLine("Retries: 7", LitematicaHudTone.WARNING),
                LitematicaHudLine("Verifier: 90 correct | 5 missing | 3 wrong | 2 extra", LitematicaHudTone.MUTED),
            ),
            presentation.lines,
        )
    }

    @Test
    fun `optional HUD fields stay absent when no placement or verifier is available`() {
        val snapshot = LitematicaHudSnapshot(
            placementName = null,
            activationMode = "LitematicaKey",
            counts = LitematicaPlacementCounts(missing = 1),
            retryCount = 0,
        )

        val presentation = LitematicaHudPresenter.present(snapshot)

        assertEquals(
            listOf(
                LitematicaHudLine("Litematica | No placement | LitematicaKey", LitematicaHudTone.TITLE),
                LitematicaHudLine("Local: 0 correct | 1 missing | 0 wrong | 0 extra | 0 pending"),
                LitematicaHudLine("Retries: 0"),
            ),
            presentation.lines,
        )
    }

    @Test
    fun `negative counters are rejected before they reach rendering`() {
        assertFailsWith<IllegalArgumentException> {
            LitematicaPlacementCounts(missing = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LitematicaHudSnapshot(
                placementName = null,
                activationMode = "Continuous",
                counts = LitematicaPlacementCounts(),
                retryCount = -1,
            )
        }
    }
}
