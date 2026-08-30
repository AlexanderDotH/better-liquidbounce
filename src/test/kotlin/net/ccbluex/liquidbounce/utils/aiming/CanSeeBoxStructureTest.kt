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
package net.ccbluex.liquidbounce.utils.aiming

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class CanSeeBoxStructureTest {

    @Test
    fun `box grid scanning and upper-side admission have named responsibilities`() {
        val source = Files.readString(SOURCE)
        val scan = declaration(source, "internal inline fun scanBoxPoints(")
        val raytrace = declaration(source, "fun raytraceUpperBlockSide(")

        assertTrue("scanBoxGridPoints(box, fn)" in scan)
        assertTrue("resolveUpperSideCandidate(" in raytrace)
        assertTrue("bestRotationTracker.considerRotation(candidate.rotation, candidate.visible)" in raytrace)
    }

    private fun declaration(source: String, marker: String): String =
        source.substringAfter(marker).substringBefore("\n}\n")

    private companion object {
        val SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/aiming/utils/CanSeeBox.kt"
        )
    }
}
