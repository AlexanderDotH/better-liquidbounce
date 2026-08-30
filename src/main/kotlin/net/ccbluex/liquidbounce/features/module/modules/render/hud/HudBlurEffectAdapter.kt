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
package net.ccbluex.liquidbounce.features.module.modules.render.hud

import net.ccbluex.liquidbounce.render.engine.BlurEffectAdapter
import net.ccbluex.liquidbounce.render.engine.BlurEffectPolicy
import net.ccbluex.liquidbounce.render.engine.BlurEffectState

interface HudBlurEffectSettings {
    fun enabled(): Boolean
    fun sigma(): Float
    fun alphaBlendStart(): Float
    fun alphaBlendEnd(): Float
}

class HudBlurEffectAdapter internal constructor(
    private val settings: HudBlurEffectSettings,
    private val hiddenBySilentScreen: () -> Boolean,
) : BlurEffectAdapter {

    override fun state() = BlurEffectState(
        hudBlurEnabled = settings.enabled(),
        hiddenBySilentScreen = hiddenBySilentScreen(),
        sigma = settings.sigma(),
        alphaBlendStart = settings.alphaBlendStart(),
        alphaBlendEnd = settings.alphaBlendEnd(),
    )

    companion object {
        fun install(settings: HudBlurEffectSettings, hiddenBySilentScreen: () -> Boolean) {
            BlurEffectPolicy.install(HudBlurEffectAdapter(settings, hiddenBySilentScreen))
        }
    }
}
