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

package net.ccbluex.liquidbounce.features.module.modules.misc.betterchat

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ChatNameHighlightHookTest {

    @Test
    fun `hook keeps feature activation and policy mapping behind the injection seam`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/misc/betterchat/ChatNameHighlightHook.kt"
        ))

        assertTrue(source.contains("ModuleBetterChat.running && ModuleBetterChat.NameHighlight.running"))
        assertTrue(source.contains("ModuleBetterChat.running && ModuleBetterChat.Copy.running"))
        assertTrue(source.contains("ModuleBetterChat.resolveMessageBounds(lines, messageIndex)"))
        assertTrue(source.contains("ModuleBetterChat.NameHighlight.color"))
        assertTrue(source.contains("ChatNameHighlightPolicy.colorFor("))
    }
}
