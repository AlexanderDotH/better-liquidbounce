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

package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickGuiScreenBackgroundTest {

    @Test
    fun `modern click gui suppresses minecraft native background`() {
        assertTrue(
            shouldSuppressNativeClickGuiBackground(
                CustomScreenType.CLICK_GUI,
                ClickGuiTheme.MODERN,
            )
        )
    }

    @Test
    fun `classic click gui preserves minecraft native background`() {
        assertFalse(
            shouldSuppressNativeClickGuiBackground(
                CustomScreenType.CLICK_GUI,
                ClickGuiTheme.CLASSIC,
            )
        )
    }

    @Test
    fun `other browser screens preserve minecraft native background`() {
        assertFalse(
            shouldSuppressNativeClickGuiBackground(
                CustomScreenType.TITLE,
                ClickGuiTheme.MODERN,
            )
        )
    }

}
