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
package net.ccbluex.liquidbounce.utils.math

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurveUtilStructureContractTest {

    @Test
    fun `curve utility retains its JVM entry points without structural suppression`() {
        assertFalse("TooManyFunctions" in source)
        assertFalse("TooManyFunctions" in interpolationSource)
        assertInOrder(
            source,
            "@JvmOverloads",
            "@JvmStatic",
            "fun transform(",
        )
        assertTrue(
            Regex("""@JvmStatic\s+internal fun transformNormalized\s*\(""").containsMatchIn(source),
        )

        val staticTransformArities = CurveUtil::class.java.declaredMethods
            .filter { it.name == "transform" && Modifier.isStatic(it.modifiers) }
            .map { it.parameterCount }
            .toSet()
        assertEquals(setOf(3, 4), staticTransformArities)
    }

    @Test
    fun `curve facade delegates normalization and interpolation responsibilities`() {
        assertInOrder(
            source,
            "CurveDataNormalizer.sortAndDeduplicateByX(data)",
            "CurveDataNormalizer.normalizeTension(tension)",
            "return transformNormalized(normalizedData, xPos, normalizedTension, onOutOfBounds)",
            "return CurveInterpolation.transformNormalized(data, xPos, tension, onOutOfBounds)",
        )
        assertTrue("internal object CurveInterpolation" in interpolationSource)
        assertTrue("internal object CurveDataNormalizer" in interpolationSource)
    }

    @Test
    fun `curve utility keeps its public type and out of bounds modes`() {
        assertTrue("object CurveUtil" in source)
        assertTrue("enum class OnOutOfBounds(override val tag: String) : Tagged" in source)
        assertEquals("net.ccbluex.liquidbounce.utils.math.CurveUtil", CurveUtil::class.java.name)
        assertEquals(listOf("CLAMP", "EXTEND"), CurveUtil.OnOutOfBounds.entries.map { it.name })
        assertEquals(listOf("Clamp", "Extend"), CurveUtil.OnOutOfBounds.entries.map { it.tag })
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val source: String = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/math/CurveUtil.kt"),
        )
        val interpolationSource: String = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/math/CurveInterpolation.kt"),
        )
    }
}
