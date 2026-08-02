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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EspPostProcessPlanTest {

    @Test
    fun `targetless frame schedules no post processing`() {
        assertEquals(emptyList<EspPostProcessPass>(), EspPostProcessPlan.create(hasGlow = false, hasOutline = false))
    }

    @Test
    fun `glow schedules downsample two blur passes and composite`() {
        assertEquals(
            listOf(
                EspPostProcessPass.DOWNSAMPLE,
                EspPostProcessPass.BLUR_HORIZONTAL,
                EspPostProcessPass.BLUR_VERTICAL,
                EspPostProcessPass.GLOW_COMPOSITE,
            ),
            EspPostProcessPlan.create(hasGlow = true, hasOutline = false),
        )
    }

    @Test
    fun `outline schedules only its composite`() {
        assertEquals(
            listOf(EspPostProcessPass.OUTLINE_COMPOSITE),
            EspPostProcessPlan.create(hasGlow = false, hasOutline = true),
        )
    }

    @Test
    fun `half resolution uses ceiling division and never reaches zero`() {
        assertEquals(EspTargetSize(841, 705), EspTargetSize.halfOf(1681, 1409))
        assertEquals(EspTargetSize(1, 1), EspTargetSize.halfOf(1, 1))
        assertEquals(EspTargetSize(1, 1), EspTargetSize.halfOf(0, 0))
    }
}
