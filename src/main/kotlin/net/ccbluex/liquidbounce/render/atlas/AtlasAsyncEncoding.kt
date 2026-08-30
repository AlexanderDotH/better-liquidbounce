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

import net.ccbluex.liquidbounce.utils.client.logger
import net.minecraft.client.renderer.Rect2i
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

internal fun <A : Any> encodeAtlasAsync(
    label: String,
    tileSize: Int,
    textureSize: Int,
    atlasPixels: ByteBuffer,
    tileRects: Map<Identifier, Rect2i>,
    result: CompletableFuture<A>,
    buildAtlas: (Map<Identifier, ByteArray>) -> A,
) {
    try {
        Util.backgroundExecutor().execute {
            encodeAtlasOnBackground(label, tileSize, textureSize, atlasPixels, tileRects, result, buildAtlas)
        }
    } catch (throwable: Throwable) {
        MemoryUtil.memFree(atlasPixels)
        result.completeExceptionally(label, throwable)
    }
}

private fun <A : Any> encodeAtlasOnBackground(
    label: String,
    tileSize: Int,
    textureSize: Int,
    atlasPixels: ByteBuffer,
    tileRects: Map<Identifier, Rect2i>,
    result: CompletableFuture<A>,
    buildAtlas: (Map<Identifier, ByteArray>) -> A,
) {
    try {
        if (result.isCancelled) return
        val images = encodePngTiles(label, tileSize, textureSize, atlasPixels, tileRects, result)
        val atlas = buildAtlas(images)
        if (!result.isCancelled && result.complete(atlas)) {
            logger.info("Loaded $textureSize x $textureSize $label atlas with ${images.size} PNGs")
        }
    } catch (throwable: Throwable) {
        result.completeExceptionally(label, throwable)
    } finally {
        MemoryUtil.memFree(atlasPixels)
    }
}
