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
package net.ccbluex.liquidbounce.config.autoconfig

import net.ccbluex.liquidbounce.config.OptionalInclusion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AutoConfigInclusionResetTest {

    @Test
    fun `failed serialization scope always restores the default inclusion policy`() {
        val selected = IncludeConfiguration(optionalInclusions = setOf(OptionalInclusion.RENDER))

        assertFailsWith<IllegalStateException> {
            withAutoConfigInclusion(selected) {
                assertEquals(selected, AutoConfig.includeConfiguration)
                error("serialization failed")
            }
        }

        assertEquals(IncludeConfiguration.DEFAULT, AutoConfig.includeConfiguration)
    }

}
