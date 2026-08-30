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
package net.ccbluex.liquidbounce.integration.theme

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ThemeShaderBackgroundPipelineFactoryTest {

    @Test
    fun `shader pipeline keeps the existing resource identifiers and render state`() {
        val source = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/ThemeShaderBackgroundPipelineFactory.kt"
        ).readText()

        assertTrue(source.contains("shader/fsh/theme-bg-\$themeName-\$backgroundName"))
        assertTrue(source.contains("pipeline/theme-bg-\$themeName"))
        assertTrue(source.contains(".screenQuadSnippet()"))
        assertTrue(source.contains(".withUniformBuffer(ClientUniformDefine.THEME_BACKGROUND)"))
        assertTrue(source.contains(".withColorTargetState(ColorTargetState.DEFAULT)"))
        assertTrue(source.contains(".withDepthStencilState(optional())"))
    }
}
