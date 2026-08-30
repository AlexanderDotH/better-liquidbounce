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
package net.ccbluex.liquidbounce.render.engine

import com.mojang.blaze3d.pipeline.RenderTarget
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.mc

internal data class FogLifecycleKey(
    val worldIdentity: Int,
    val renderTargetWidth: Int,
    val renderTargetHeight: Int,
    val gpuDeviceIdentity: Int,
) {
    companion object {
        fun capture(target: RenderTarget) = FogLifecycleKey(
            worldIdentity = System.identityHashCode(mc.level),
            renderTargetWidth = target.width,
            renderTargetHeight = target.height,
            gpuDeviceIdentity = System.identityHashCode(gpuDevice),
        )
    }
}
