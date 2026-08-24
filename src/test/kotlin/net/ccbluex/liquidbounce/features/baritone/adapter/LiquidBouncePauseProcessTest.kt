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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.baritone.adapter

import baritone.api.process.PathingCommandType
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseCause
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseController
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseReason
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidBouncePauseProcessTest {

    @Test
    fun `temporary highest priority process requests a pause only for relevant pathing`() {
        var pathingRelevant = true
        val controller = BaritonePauseController(10)
        val process = LiquidBouncePauseProcess(controller) { pathingRelevant }

        controller.tick(listOf(BaritonePauseCause(BaritonePauseReason.USER_INPUT, "User input")))

        assertTrue(process.isActive)
        assertTrue(process.isTemporary)
        assertEquals(Double.MAX_VALUE, process.priority())
        assertEquals(PathingCommandType.REQUEST_PAUSE, process.onTick(false, true).commandType)

        pathingRelevant = false
        assertFalse(process.isActive)
    }
}
