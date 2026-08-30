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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SpeedRuntimeArchitectureContractTest {

    @Test
    fun `speed runtime uses the module port instead of ClientModule`() {
        val runtime = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/speed/runtime/" +
                "SpeedModuleControl.kt",
        )
        val module = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/speed/ModuleSpeed.kt",
        )

        assertFalse(runtime.contains("features.module.ClientModule"))
        assertTrue(runtime.contains("speed.contract.SpeedModulePort"))
        assertTrue(module.contains("SpeedModuleControl.bind(ModuleSpeedPort)"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))
}
