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
package net.ccbluex.liquidbounce.features.module.modules.combat.aimbot

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DroneControlScreenContractTest {

    @Test
    fun `zoom remains exposed through the Java compatible float getter`() {
        val getter = DroneControlScreen::class.java.getDeclaredMethod("getZoomFactor")

        assertTrue(Modifier.isPublic(getter.modifiers))
        assertEquals(Float::class.javaPrimitiveType, getter.returnType)
    }

    @Test
    fun `screen no longer hides its function count`() {
        val source = Files.readString(Path.of(SOURCE))

        assertFalse("TooManyFunctions" in source)
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/aimbot/DroneControlScreen.kt"
    }
}
