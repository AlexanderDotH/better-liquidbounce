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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FlyAutomationArchitectureContractTest {

    @Test
    fun `automation facade uses its module port instead of the runtime implementation`() {
        val facade = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/fly/automation/" +
                "FlyAutomation.kt",
        )
        val module = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/fly/ModuleFly.kt",
        )

        assertFalse(facade.contains(".movement.fly.runtime"))
        assertTrue(module.contains("FlyAutomation.bind(ModuleFlyAutomationPort)"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))
}
