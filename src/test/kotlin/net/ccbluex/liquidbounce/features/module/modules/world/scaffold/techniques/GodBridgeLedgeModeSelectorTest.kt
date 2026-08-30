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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques

import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldGodBridgeTechnique.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.EnumSet

class GodBridgeLedgeModeSelectorTest {

    @Test
    fun `non-jump mode does not evaluate jump capability`() {
        var capabilityChecks = 0

        val selected = GodBridgeLedgeModeSelector.select(
            currentMode = Mode.BACKWARDS,
            availableModes = EnumSet.of(Mode.JUMP, Mode.BACKWARDS),
        ) {
            capabilityChecks++
            true
        }

        assertEquals(Mode.BACKWARDS, selected)
        assertEquals(0, capabilityChecks)
    }

    @Test
    fun `jump mode remains selected when two-block jump is unavailable`() {
        var capabilityChecks = 0

        val selected = GodBridgeLedgeModeSelector.select(
            currentMode = Mode.JUMP,
            availableModes = EnumSet.of(Mode.JUMP, Mode.BACKWARDS),
        ) {
            capabilityChecks++
            false
        }

        assertEquals(Mode.JUMP, selected)
        assertEquals(1, capabilityChecks)
    }

    @Test
    fun `capable jump selects the sole configured non-jump mode`() {
        val selected = GodBridgeLedgeModeSelector.select(
            currentMode = Mode.JUMP,
            availableModes = EnumSet.of(Mode.JUMP, Mode.STOP_INPUT),
            canJumpTwoBlocksHigh = { true },
        )

        assertEquals(Mode.STOP_INPUT, selected)
    }

    @Test
    fun `capable jump falls back to sneak when no alternative is configured`() {
        val selected = GodBridgeLedgeModeSelector.select(
            currentMode = Mode.JUMP,
            availableModes = EnumSet.of(Mode.JUMP),
            canJumpTwoBlocksHigh = { true },
        )

        assertEquals(Mode.SNEAK, selected)
    }
}
