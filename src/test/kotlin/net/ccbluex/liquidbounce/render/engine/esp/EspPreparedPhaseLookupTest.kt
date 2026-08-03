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

package net.ccbluex.liquidbounce.render.engine.esp

import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.IdentityHashMap

class EspPreparedPhaseLookupTest {

    @Test
    fun `prepared groups keep an effect visible after its source phase is drained`() {
        val drainedPhase = SimpleFeatureRenderPhase()
        val groupsByPhase = IdentityHashMap<FeatureRenderPhase<*>, List<*>>()
        groupsByPhase[drainedPhase] = listOf(Any())

        assertTrue(drainedPhase.isEmpty)
        assertTrue(EspPreparedPhaseLookup.hasPreparedGroups(groupsByPhase, drainedPhase))
    }
}
