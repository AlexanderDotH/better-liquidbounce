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

package net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.autobow

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class AutoBowPackageBoundaryTest {

    @Test
    fun `AutoBow features depend on their owner port instead of the concrete module`() {
        val sourceRoot = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/aimbot/autobow",
        )
        val combinedSource = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .map { Files.readString(it) }
                .toList()
                .joinToString("\n")
        }

        val parentPackage = "import net.ccbluex.liquidbounce.features.module.modules.combat.aimbot."
        val ownPackage = "${parentPackage}autobow."
        combinedSource.lineSequence()
            .filter { it.startsWith("import ") }
            .forEach { importLine ->
                assertFalse(importLine.startsWith(parentPackage) && !importLine.startsWith(ownPackage), importLine)
            }
        listOf(
            "interface AutoBowFeatureOwner : EventListener",
            "fun nextChargeRandomGaussian(): Double",
            "fun hasShotDelayElapsed(delayMillis: Long): Boolean",
            "fun recordShot()",
        ).forEach { ownerContract ->
            assertTrue(ownerContract in combinedSource, ownerContract)
        }
    }
}
