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

fun interface BlurEffectAdapter {
    fun state(): BlurEffectState
}

data class BlurEffectState(
    val hudBlurEnabled: Boolean,
    val hiddenBySilentScreen: Boolean,
    val sigma: Float,
    val alphaBlendStart: Float,
    val alphaBlendEnd: Float,
)

object BlurEffectPolicy {

    internal val disabledState = BlurEffectState(
        hudBlurEnabled = false,
        hiddenBySilentScreen = false,
        sigma = 1.0F,
        alphaBlendStart = 0.0F,
        alphaBlendEnd = 0.0F,
    )

    private val DISABLED = BlurEffectAdapter { disabledState }

    @Volatile
    private var adapter: BlurEffectAdapter = DISABLED

    @JvmStatic
    @Synchronized
    fun install(adapter: BlurEffectAdapter) {
        check(this.adapter === DISABLED) { "Blur effect adapter is already installed" }
        this.adapter = adapter
    }

    fun state(): BlurEffectState = adapter.state()

    fun shouldRenderHudBlur(): Boolean = state().hudBlurEnabled

    fun shouldHideScreen(): Boolean = state().hiddenBySilentScreen

    @Synchronized
    internal fun <T> withAdapterForTest(candidate: BlurEffectAdapter?, block: () -> T): T {
        val previous = adapter
        adapter = candidate ?: DISABLED
        return try {
            block()
        } finally {
            adapter = previous
        }
    }
}
