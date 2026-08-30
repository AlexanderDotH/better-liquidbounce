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
package net.ccbluex.liquidbounce.render.atlas

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AbstractAtlasRendererStructureTest {

    @Test
    fun `atlas readback closes GPU resources before background encoding`() {
        val source = readSource("AbstractAtlasRenderer.kt")
        val processReadback = source
            .substringAfter("private fun processReadback")
            .substringBefore("private fun closeReadbackResources")

        assertInOrder(processReadback, "readFully()", "closeReadbackResources", "encodeAtlasAsync")
        assertFalse(source.contains("features.module.MinecraftShortcuts"))
        assertTrue(source.contains("utils.client.MinecraftShortcuts"))
    }

    @Test
    fun `background encoding preserves encode build complete and free order`() {
        val source = readSource("AtlasAsyncEncoding.kt")
        val backgroundTask = source.substringAfter("private fun <A : Any> encodeAtlasOnBackground")
        val backgroundBody = backgroundTask.substringAfter("    try {")

        val scheduler = source.substringBefore("private fun <A : Any> encodeAtlasOnBackground")
        assertTrue(scheduler.contains("Util.backgroundExecutor().execute"))
        assertInOrder(backgroundBody, "encodePngTiles", "buildAtlas", "result.complete")
        assertTrue(backgroundTask.contains("finally"))
        assertTrue(backgroundTask.contains("MemoryUtil.memFree(atlasPixels)"))
    }

    @Test
    fun `renderer close order retains projection framebuffer nodes and features`() {
        val source = readSource("AbstractAtlasRenderer.kt")

        assertInOrder(
            source.substringAfter("private fun close()"),
            "projectionMatrixBuffer.close()",
            "framebuffer.destroyBuffers()",
            "submitNodeStorage.drainPhases",
            "featureRenderDispatcher.close()",
        )
    }

    private fun assertInOrder(source: String, vararg tokens: String) {
        var previous = -1
        for (token in tokens) {
            val index = source.indexOf(token)
            assertTrue(index > previous, "$token must follow the previous atlas lifecycle step")
            previous = index
        }
    }

    private fun readSource(name: String): String = Files.readString(
        Path.of("src/main/kotlin/net/ccbluex/liquidbounce/render/atlas", name)
    )
}
