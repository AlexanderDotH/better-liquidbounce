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
import kotlin.test.assertNull

class LitematicaRenderStateStoreTest {

    @Test
    fun `published target positions and lists cannot be mutated by the planner`() {
        val mutablePosition = BlockPos.MutableBlockPos(4, 65, -2)
        val mutableTargets = mutableListOf(
            LitematicaRenderTarget(mutablePosition, LitematicaTargetStyle.PLACE),
        )
        val store = LitematicaRenderStateStore()

        store.update(LitematicaRenderSnapshot(targets = mutableTargets))
        mutablePosition.set(30, 90, 30)
        mutableTargets.clear()

        assertEquals(
            listOf(LitematicaRenderTarget(BlockPos(4, 65, -2), LitematicaTargetStyle.PLACE)),
            store.snapshot().targets,
        )
    }

    @Test
    fun `clear removes world targets and the HUD in one operation`() {
        val store = LitematicaRenderStateStore()
        store.update(
            LitematicaRenderSnapshot(
                targets = listOf(LitematicaRenderTarget(BlockPos.ZERO, LitematicaTargetStyle.PENDING)),
                hud = LitematicaHudSnapshot(
                    placementName = "Starter House",
                    activationMode = "LitematicaKey",
                    counts = LitematicaPlacementCounts(),
                    retryCount = 0,
                ),
            )
        )

        store.clear()

        assertEquals(emptyList(), store.snapshot().targets)
        assertNull(store.snapshot().hud)
    }
}
