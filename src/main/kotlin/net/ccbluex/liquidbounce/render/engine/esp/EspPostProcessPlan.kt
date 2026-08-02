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

enum class EspPostProcessPass {
    DOWNSAMPLE,
    BLUR_HORIZONTAL,
    BLUR_VERTICAL,
    GLOW_COMPOSITE,
    OUTLINE_COMPOSITE,
}

object EspPostProcessPlan {

    fun create(hasGlow: Boolean, hasOutline: Boolean): List<EspPostProcessPass> = buildList(5) {
        if (hasGlow) {
            add(EspPostProcessPass.DOWNSAMPLE)
            add(EspPostProcessPass.BLUR_HORIZONTAL)
            add(EspPostProcessPass.BLUR_VERTICAL)
            add(EspPostProcessPass.GLOW_COMPOSITE)
        }

        if (hasOutline) {
            add(EspPostProcessPass.OUTLINE_COMPOSITE)
        }
    }
}

data class EspTargetSize(val width: Int, val height: Int) {

    companion object {
        fun halfOf(width: Int, height: Int) = EspTargetSize(
            width = ((width.coerceAtLeast(1) + 1) / 2).coerceAtLeast(1),
            height = ((height.coerceAtLeast(1) + 1) / 2).coerceAtLeast(1),
        )
    }
}
