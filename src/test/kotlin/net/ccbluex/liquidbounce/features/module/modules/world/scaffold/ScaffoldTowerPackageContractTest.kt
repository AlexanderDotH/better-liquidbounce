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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ScaffoldTowerPackageContractTest {

    @Test
    fun `tower strategies depend on their neutral runtime contract`() {
        TOWER_STRATEGIES.forEach { strategy ->
            val path = TOWER_ROOT.resolve("$strategy.kt")

            assertTrue(Files.isRegularFile(path), "$path must retain its public package")
            val source = Files.readString(path)
            assertTrue("package $TOWER_PACKAGE" in source, "$strategy must retain its public package")
            assertFalse(SCAFFOLD_MODULE_IMPORT in source, "$strategy must use the neutral runtime contract")
            assertFalse("ModuleScaffold." in source, "$strategy must not reach back into the owning module")
        }
    }

    @Test
    fun `tower configuration order remains stable`() {
        val source = Files.readString(SCAFFOLD_ROOT.resolve("ModuleScaffold.kt"))
        val towerConfiguration = source.substringAfter("val towerMode = choices")
            .substringBefore("internal val isTowering")
        val registeredStrategies = TOWER_STRATEGY_PATTERN.findAll(towerConfiguration)
            .map { it.value }
            .toList()

        assertEquals(TOWER_STRATEGIES.drop(1), registeredStrategies)
    }

    private companion object {
        const val SCAFFOLD_PACKAGE = "net.ccbluex.liquidbounce.features.module.modules.world.scaffold"
        const val TOWER_PACKAGE = "$SCAFFOLD_PACKAGE.tower"
        const val SCAFFOLD_MODULE_IMPORT = "import $SCAFFOLD_PACKAGE.ModuleScaffold"
        val SCAFFOLD_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold",
        )
        val TOWER_ROOT: Path = SCAFFOLD_ROOT.resolve("tower")
        val TOWER_STRATEGIES = listOf(
            "ScaffoldTower",
            "ScaffoldTowerNone",
            "ScaffoldTowerMotion",
            "ScaffoldTowerPulldown",
            "ScaffoldTowerKarhu",
            "ScaffoldTowerVulcan",
            "ScaffoldTowerHypixel",
        )
        val TOWER_STRATEGY_PATTERN = Regex(
            "ScaffoldTower(?:None|Motion|Pulldown|Karhu|Vulcan|Hypixel)",
        )
    }
}
