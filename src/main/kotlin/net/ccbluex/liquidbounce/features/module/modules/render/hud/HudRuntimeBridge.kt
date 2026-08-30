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

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.minecraft.client.gui.screens.Screen

interface HudOverlayHandle {
    val browserSettings: ValueGroup
    var visible: Boolean
    fun close()
}

interface HudRuntimeProvider {
    fun createOverlay(reopen: () -> Unit): HudOverlayHandle
    fun isClickGuiScreen(screen: Screen?): Boolean
    fun themeSettings(): List<ValueGroup>
    fun additionalComponents(): List<ValueGroup>
}

object HudRuntimeBridge {

    @Volatile
    private var provider: HudRuntimeProvider? = null

    @JvmStatic
    @Synchronized
    fun install(provider: HudRuntimeProvider) {
        check(this.provider == null) { "HUD runtime provider is already installed" }
        this.provider = provider
    }

    fun createOverlay(reopen: () -> Unit): HudOverlayHandle =
        provider?.createOverlay(reopen) ?: DeferredHudOverlay(reopen)

    fun isClickGuiScreen(screen: Screen?): Boolean = provider?.isClickGuiScreen(screen) == true
    fun themeSettings(): List<ValueGroup> = provider?.themeSettings().orEmpty()
    fun additionalComponents(): List<ValueGroup> = provider?.additionalComponents().orEmpty()

    private class DeferredHudOverlay(private val reopen: () -> Unit) : HudOverlayHandle {
        private val delegate: HudOverlayHandle by lazy {
            checkNotNull(provider) { "HUD runtime provider is not installed" }.createOverlay(reopen)
        }

        override val browserSettings: ValueGroup get() = delegate.browserSettings
        override var visible: Boolean
            get() = delegate.visible
            set(value) { delegate.visible = value }

        override fun close() = delegate.close()
    }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: HudRuntimeProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
