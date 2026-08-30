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
package net.ccbluex.liquidbounce.render

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RenderPassExtensionFacadeContractTest {

    @Test
    fun `split extensions retain the RenderPassExtensions JVM facade`() {
        sources.forEach { source ->
            assertTrue("@file:JvmName(\"RenderPassExtensionsKt\")" in source)
            assertTrue("@file:JvmMultifileClass" in source)
            assertFalse("TooManyFunctions" in source)
        }
    }

    @Test
    fun `all render pass extension entry points remain available exactly once`() {
        val combinedSource = sources.joinToString("\n")
        expectedFunctionCounts.forEach { (functionName, expectedCount) ->
            assertTrue(
                Regex("""fun\s+(?:[A-Za-z0-9_.<>?]+\.)?${Regex.escape(functionName)}\s*\(""")
                    .findAll(combinedSource)
                    .count() == expectedCount,
                "Expected $expectedCount $functionName declaration(s)",
            )
        }
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/render")
        val sources = listOf(
            "RenderPassExtensions.kt",
            "RenderPassResourceBindings.kt",
            "RenderPassStateBindings.kt",
        ).map { Files.readString(SOURCE_ROOT.resolve(it)) }
        val expectedFunctionCounts = mapOf(
            "bindTextures" to 1,
            "bindTexture" to 1,
            "unbindTexture" to 1,
            "setUniforms" to 1,
            "bindDefaultUniforms" to 1,
            "bindProjectionUniform" to 1,
            "bindFogUniform" to 1,
            "bindGlobalsUniform" to 1,
            "bindLightingUniform" to 1,
            "bindDynamicTransformsUniform" to 1,
            "setupRenderTypeScissor" to 1,
            "bindAndDraw" to 1,
            "getDynamicTransformsUniform" to 1,
            "createRenderPass" to 2,
        )
    }
}
