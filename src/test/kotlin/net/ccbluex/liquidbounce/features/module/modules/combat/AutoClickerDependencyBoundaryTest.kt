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
package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoClickerDependencyBoundaryTest {

    @Test
    fun `use settings receive their parent through the local provider contract`() {
        val useButtonSource = Files.readString(USE_BUTTON_SOURCE)
        val providerSource = Files.readString(PARENT_PROVIDER_SOURCE)
        val moduleSource = Files.readString(MODULE_SOURCE)

        assertFalse("ModuleAutoClicker" in useButtonSource)
        assertFalse("ModuleAutoClicker" in providerSource)
        assertTrue("AutoClickerUseParentProvider" in useButtonSource)
        assertTrue("fun interface AutoClickerUseParentProvider" in providerSource)
        assertTrue("private val useButton = AutoClickerUseButton" in moduleSource)
        assertTrue("tree(useButton)" in moduleSource)
    }

    @Test
    fun `use settings retain their public configuration defaults`() {
        val source = Files.readString(USE_BUTTON_SOURCE)

        assertTrue("\"Use\", false" in source)
        assertTrue("\"DelayStart\", false" in source)
        assertTrue("\"OnlyBlock\", false" in source)
        assertTrue("\"RequiresNoInput\", false" in source)
        assertTrue("var needToWait = true" in source)
    }

    private companion object {
        val USE_BUTTON_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/autoclicker/" +
                "AutoClickerUseButton.kt"
        )
        val PARENT_PROVIDER_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/autoclicker/contract/" +
                "AutoClickerUseParentProvider.kt"
        )
        val MODULE_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleAutoClicker.kt"
        )
    }
}
