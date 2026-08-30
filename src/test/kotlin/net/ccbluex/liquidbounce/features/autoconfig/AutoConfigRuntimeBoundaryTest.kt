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
package net.ccbluex.liquidbounce.features.autoconfig

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoConfigRuntimeBoundaryTest {

    @Test
    fun `auto config reaches modules and render refresh only through its runtime ports`() {
        val autoConfigSources = Files.walk(AUTO_CONFIG_ROOT).use { paths ->
            paths.filter { path -> path.extension == "kt" }
                .map(Files::readString)
                .toList()
        }

        assertFalse(autoConfigSources.any { source ->
            source.lineSequence().any { line ->
                line.startsWith("import net.ccbluex.liquidbounce.features.module")
            }
        })
        assertTrue(read(MODULE_MANAGER).contains("AutoConfigModuleBridge.install("))
        assertTrue(read(MODULE_CLICK_GUI).contains("AutoConfigUiBridge.installClickGuiSync(::sync)"))
        assertTrue(read(MODULE_HUD).contains("AutoConfigUiBridge.installHudReopen(::reopen)"))
    }

    @Test
    fun `module migrations still run before full or selected values are loaded`() {
        val loading = read(AUTO_CONFIG_MODULE_LOADING)
        val migration = loading.indexOf("prepareModuleConfigForLoad(json)")
        val fullLoad = loading.indexOf("deserializeValueGroup(AutoConfigModuleBridge.modulesConfig, json)")
        val selectedLoad = loading.indexOf("modules.forEach")

        assertTrue(migration >= 0)
        assertTrue(fullLoad > migration)
        assertTrue(selectedLoad > migration)
    }

    private fun read(path: Path): String = Files.readString(path)

    private companion object {
        val AUTO_CONFIG_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/autoconfig"
        )
        val AUTO_CONFIG_MODULE_LOADING: Path = AUTO_CONFIG_ROOT.resolve("AutoConfigModuleLoading.kt")
        val MODULE_MANAGER: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/ModuleManager.kt"
        )
        val MODULE_CLICK_GUI: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleClickGui.kt"
        )
        val MODULE_HUD: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleHud.kt"
        )
    }
}
