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
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains

class InputClipboardRouteTest {

    @Test
    fun `clipboard route exposes both Minecraft clipboard directions`() {
        val source = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/game/" +
                    "InputFunctions.kt"
            )
        )

        assertContains(source, "get(\"/clipboard\")")
        assertContains(source, "put(\"/clipboard\")")
        assertContains(source, "mc.keyboardHandler.clipboard = clipboard.text")
        assertContains(source, "putClipboard()")
    }
}
