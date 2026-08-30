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
package net.ccbluex.liquidbounce.render.engine.type

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Color4bTransformationContractTest {

    @Test
    fun `fade keeps alpha truncation and full-strength identity`() {
        val color = Color4b(20, 40, 60, 101)

        assertSame(color, color.fade(1f))
        assertEquals(Color4b(20, 40, 60, 50), color.fade(0.5f))
    }

    @Test
    fun `darker keeps per-channel floor and alpha`() {
        assertEquals(Color4b(70, 35, 0, 77), Color4b(100, 50, 1, 77).darker())
    }

    @Test
    fun `interpolation keeps component factors and truncation`() {
        val source = Color4b(10, 20, 30, 40)
        val target = Color4b(110, 220, 230, 240)

        assertEquals(Color4b(60, 120, 130, 140), source.interpolateTo(target, 0.5))
        assertEquals(
            Color4b(110, 70, 80, 40),
            source.interpolateTo(target, 1.0, 0.25, 0.25, 0.0),
        )
    }

    @Test
    fun `transformations move behind a small public behavior contract`() {
        assertTrue("Color4bTransformations" in colorSource)
        assertFalse("TooManyFunctions" in colorSource)
        listOf("fade", "darker", "interpolateTo").forEach { functionName ->
            assertTrue(Regex("""fun\s+$functionName\s*\(""").containsMatchIn(transformSource))
        }

        assertEquals(Color4b::class.java, Color4b::class.java.getMethod("fade", Float::class.java).returnType)
        assertEquals(Color4b::class.java, Color4b::class.java.getMethod("darker").returnType)
        assertEquals(
            Color4b::class.java,
            Color4b::class.java.getMethod(
                "interpolateTo",
                Color4b::class.java,
                Double::class.java,
            ).returnType,
        )
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/render/engine/type")
        val colorSource: String = Files.readString(SOURCE_ROOT.resolve("Color4b.kt"))
        val transformSource: String = Files.readString(SOURCE_ROOT.resolve("Color4bTransformations.kt"))
    }
}
