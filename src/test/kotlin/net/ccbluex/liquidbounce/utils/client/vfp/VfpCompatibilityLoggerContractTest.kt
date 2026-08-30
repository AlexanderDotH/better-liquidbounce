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
package net.ccbluex.liquidbounce.utils.client.vfp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class VfpCompatibilityLoggerContractTest {

    @Test
    fun `vfp failures use the client logger without depending on bootstrap`() {
        val source = Path.of(
            "src/main/java/net/ccbluex/liquidbounce/utils/client/vfp/VfpCompatibility.java",
        ).readText()

        assertFalse("import net.ccbluex.liquidbounce.LiquidBounce;" in source)
        assertTrue("import net.ccbluex.liquidbounce.utils.client.ClientUtilsKt;" in source)
        assertEquals(18, source.countOccurrences("ClientUtilsKt.getLogger().error("))
        assertEquals(0, source.countOccurrences("LiquidBounce.INSTANCE.getLogger()"))
    }

    private fun String.countOccurrences(needle: String): Int = windowed(needle.length).count { it == needle }
}
