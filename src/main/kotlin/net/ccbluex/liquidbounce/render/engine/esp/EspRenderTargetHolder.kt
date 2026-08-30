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

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import net.ccbluex.liquidbounce.render.buffer.clearColor
import net.ccbluex.liquidbounce.render.buffer.clearColorAndDepth

internal class EspRenderTargetHolder(
    private val name: String,
    private val useDepth: Boolean,
    private val format: GpuFormat,
) : AutoCloseable {

    var raw: RenderTarget? = null
        private set

    fun initAndClear(width: Int, height: Int): RenderTarget {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val target = raw?.also {
            if (it.width != safeWidth || it.height != safeHeight) {
                it.resize(safeWidth, safeHeight)
            }
        } ?: TextureTarget(name, safeWidth, safeHeight, useDepth, format).also { raw = it }

        if (useDepth) {
            // Minecraft 26.2 uses reverse-Z: zero is the far clear value.
            target.clearColorAndDepth(depth = 0.0)
        } else {
            target.colorTexture!!.clearColor()
        }
        return target
    }

    override fun close() {
        raw?.destroyBuffers()
        raw = null
    }
}
