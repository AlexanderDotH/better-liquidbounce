/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package net.ccbluex.liquidbounce.features.module.modules.render.trajectories

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrajectoryHypotheticalPolicyTest {

    @Test
    fun `held fishing rod is filtered only while its active bobber trajectory is rendered`() {
        assertTrue(shouldFilterHeldFishingRod(true, true, true))
        assertFalse(shouldFilterHeldFishingRod(false, true, true))
        assertFalse(shouldFilterHeldFishingRod(true, false, true))
        assertFalse(shouldFilterHeldFishingRod(true, true, false))
    }
}
