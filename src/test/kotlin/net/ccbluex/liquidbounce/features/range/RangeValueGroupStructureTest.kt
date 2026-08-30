/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.range

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RangeValueGroupStructureTest {

    @Test
    fun `range values use the canonical Minecraft shortcuts contract`() {
        val source = Files.readString(Path.of(RANGE_VALUE_GROUP))

        assertTrue(source.contains("import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts"))
        assertFalse(source.contains("import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts"))
        assertTrue(source.contains(") : ValueGroup(name), MinecraftShortcuts {"))
    }

    private companion object {
        const val RANGE_VALUE_GROUP =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/range/RangeValueGroup.kt"
    }
}
