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
package net.ccbluex.liquidbounce.features.module.modules.movement.liquidwalk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LiquidWalkRuntimeArchitectureContractTest {

    @Test
    fun `liquidwalk runtime obtains its timer owner through the module port`() {
        val runtime = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/liquidwalk/runtime/" +
                "LiquidWalkModuleProvider.kt",
        )
        val module = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/liquidwalk/" +
                "ModuleLiquidWalk.kt",
        )

        assertFalse(runtime.contains("features.module.ClientModule"))
        assertTrue(runtime.contains("liquidwalk.contract.LiquidWalkModulePort"))
        assertTrue(module.contains("LiquidWalkModuleProvider.bind(ModuleLiquidWalkPort)"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))
}
