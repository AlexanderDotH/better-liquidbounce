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
package net.ccbluex.liquidbounce.render.engine.esp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EspProtectedSurfaceRegressionTest {

    @Test
    fun `local player surface is captured before combat target filtering`() {
        val source = readSource(
            "src/main/java/net/ccbluex/liquidbounce/render/engine/esp/EspMaskTargetSelector.java"
        )

        val surfaceCapture = source.indexOf("request.with(EspMaskLayer.PROTECTED_SURFACE")
        val combatFilter = source.indexOf("CombatExtensionsKt.shouldBeShown")

        assertTrue(surfaceCapture >= 0)
        assertTrue(combatFilter > surfaceCapture)
    }

    @Test
    fun `protected model layer is rendered into the composite exclusion mask`() {
        val source = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspShaderRenderer.kt"
        )

        assertTrue(source.contains("executeEspMask`(EspMaskLayer.PROTECTED_SURFACE)"))
    }

    @Test
    fun `deferred tracer glow cannot skip player surface capture`() {
        val source = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspShaderRenderer.kt"
        )
        val capture = source
            .substringAfter("private class EspProtectedMaskRenderer")
            .substringAfter("fun capture(")
            .substringBefore("fun prepare(")

        assertFalse(capture.contains("activeSources"))
        assertFalse(capture.contains("needsDedicatedSurfaceMask"))
    }

    @Test
    fun `protected pixels reject both Block ESP halo and core color`() {
        val shader = readSource(
            "src/main/resources/resources/liquidbounce/shaders/esp/glow_composite.frag"
        )
        val coreAlpha = shader.lineSequence().single { it.contains("float coreAlpha") }

        assertTrue(coreAlpha.contains("* outside"))
    }

    @Test
    fun `protected mask union preserves alpha for nested ESP owners`() {
        val pipelines = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/ClientRenderPipelines.kt"
        )
        val unionPipeline = pipelines
            .substringAfter("val EspMaskUnion")
            .substringBefore("val EspOutlineComposite")

        assertTrue(unionPipeline.contains("ColorTargetState.WRITE_ALL"))
    }

    @Test
    fun `Block ESP Glow no longer writes into the shared vanilla outline framebuffer`() {
        val module = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleBlockESP.kt"
        )
        val renderer = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspShaderRenderer.kt"
        )

        assertFalse(module.contains("DrawOutlinesEvent"))
        assertTrue(renderer.contains("ModuleBlockESP.activeShaderMode"))
    }

    @Test
    fun `world ESP composites before Minecraft draws the first person hand`() {
        val source = readSource(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinGameRenderer.java"
        )
        val compositeHook = source
            .substringBefore("private void compositeEspShaders")
            .substringAfterLast("@Inject(")

        assertTrue(compositeHook.contains("method = \"renderLevel\""))
        assertTrue(compositeHook.contains("GameRenderer;renderItemInHand"))
        assertFalse(compositeHook.contains("doEntityOutline"))
    }

    private fun readSource(path: String): String = Files.readString(Path.of(path))
}
