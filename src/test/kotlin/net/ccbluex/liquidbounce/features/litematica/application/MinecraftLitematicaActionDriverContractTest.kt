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
package net.ccbluex.liquidbounce.features.litematica.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MinecraftLitematicaActionDriverContractTest {

    @Test
    fun `controlled Easy Place has exactly one LiquidBounce swing owner`() {
        val source = Files.readString(Path.of(SOURCE))
        val method = source.substringAfter("private fun executeEasyPlace(")
            .substringBefore("private fun findSlot(")

        assertEquals(1, "swingMode.swing".toRegex().findAll(method).count())
        assertFalse(method.contains("player.swing"))
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/litematica/application/" +
                "MinecraftLitematicaActionDriver.kt"
    }
}
