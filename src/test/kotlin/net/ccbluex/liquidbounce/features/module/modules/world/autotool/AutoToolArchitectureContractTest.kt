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
package net.ccbluex.liquidbounce.features.module.modules.world.autotool

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AutoToolArchitectureContractTest {

    @Test
    fun `autotool requirements do not depend on the module implementation`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/autotool/AutoToolRequirements.kt"
        ))

        assertFalse(source.contains(
            "import net.ccbluex.liquidbounce.features.module.modules.world.ModuleAutoTool"
        ))
    }
}
