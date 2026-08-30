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
package net.ccbluex.liquidbounce.features.module.modules.render

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleLogoffSpotBlinkContractTest {

    @Test
    fun `entity removal keeps blink dummy filtering behind the neutral state contract`() {
        val source = Files.readString(Path.of(MODULE_LOGOFF_SPOT))

        assertTrue(source.contains("import net.ccbluex.liquidbounce.common.runtime.BlinkDummyState"))
        assertTrue(source.contains("|| BlinkDummyState.isDummyPlayer(entity.id)"))
        assertFalse(source.contains("features.module.modules.player.ModuleBlink"))
    }

    private companion object {
        const val MODULE_LOGOFF_SPOT =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleLogoffSpot.kt"
    }
}
