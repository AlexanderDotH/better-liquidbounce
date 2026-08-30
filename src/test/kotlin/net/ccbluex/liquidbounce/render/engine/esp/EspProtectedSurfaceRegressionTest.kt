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
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/esp/integration/" +
                "EspMaskFeatureAdapter.kt"
        )

        val surfaceCapture = source.indexOf("var request = protectPlayerSurface(entity)")
        val combatFilter = source.indexOf("request = appendLivingEntityMask(request, entity)")

        assertTrue(surfaceCapture >= 0)
        assertTrue(combatFilter > surfaceCapture)
    }

    @Test
    fun `protected model layer is rendered into the composite exclusion mask`() {
        val source = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspMaskCapture.kt"
        )

        assertTrue(source.contains("executeEspMask`(EspMaskLayer.PROTECTED_SURFACE)"))
    }

    @Test
    fun `deferred tracer glow cannot skip player surface capture`() {
        val source = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspMaskCapture.kt"
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
            "src/main/kotlin/net/ccbluex/liquidbounce/render/CompositePipelineDefinitions.kt"
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
        val capture = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspMaskCapture.kt"
        )

        assertFalse(module.contains("DrawOutlinesEvent"))
        assertTrue(module.contains("source = EspGlowSource.BLOCK_ESP"))
        assertTrue(module.contains("style = { activeShaderMode?.style }"))
        assertTrue(module.contains("activeShaderMode?.drawMask(target) == true"))
        assertTrue(capture.contains("EspFeatureRendererRegistry.glow(source)"))
        assertTrue(capture.contains("provider.drawMask(target)"))
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

    @Test
    fun `merged renderer keeps fork ESP and player-model hooks alongside upstream render hooks`() {
        val gameRenderer = readSource(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinGameRenderer.java"
        )
        val levelRenderer = readSource(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinLevelRenderer.java"
        )
        val livingRenderer = readSource(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/entity/" +
                "MixinLivingEntityRenderer.java"
        )
        val antiBlind = readSource(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleAntiBlind.kt"
        )

        val beginFrame = gameRenderer.indexOf("EspShaderRenderer.beginFrame()")
        val gameRenderEvent = gameRenderer.indexOf("EventManager.INSTANCE.callEvent(GameRenderEvent.INSTANCE)")
        assertTrue(beginFrame >= 0)
        assertTrue(gameRenderEvent > beginFrame)
        assertTrue(levelRenderer.contains("EspShaderRenderer.capture(preparedFrame)"))
        assertTrue(levelRenderer.contains("method = \"addAlwaysOnTopPass\""))
        assertTrue(livingRenderer.contains("PlayerModelDelayHook.applyDelayedTransform"))
        assertTrue(livingRenderer.contains("ModuleAntiBlind.canRenderInvisibleEntities()"))
        assertTrue(
            Regex(
                """fun canRenderInvisibleEntities\(\)\s*=\s*canRender\(DoRender\.INVISIBLE_ENTITIES\)"""
            ).containsMatchIn(antiBlind)
        )
    }

    private fun readSource(path: String): String = Files.readString(Path.of(path))
}
