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
package net.ccbluex.liquidbounce.features.module.modules.render.clickgui

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.minecraft.client.gui.screens.Screen

interface CachedClickGuiScreenBridge {
    val screen: Screen
    var browserVisible: Boolean
    fun sync()
    fun close()
}

interface ClickGuiRuntimeHook {
    fun isClientInitialized(): Boolean
    fun isTyping(): Boolean
    fun isClickGuiScreen(screen: Screen?): Boolean
    fun browserSettings(): ValueGroup
    fun createSharedScreen(): Screen
    fun createStandaloneScreen(): CachedClickGuiScreenBridge
}

object ClickGuiRuntimeBridge {

    @Volatile
    private var provider: ClickGuiRuntimeHook? = null

    @JvmStatic
    @Synchronized
    fun install(provider: ClickGuiRuntimeHook) {
        check(this.provider == null) { "ClickGUI runtime provider is already installed" }
        this.provider = provider
    }

    fun isClientInitialized(): Boolean = provider?.isClientInitialized() == true
    fun isTyping(): Boolean = provider?.isTyping() == true
    fun isClickGuiScreen(screen: Screen?): Boolean = provider?.isClickGuiScreen(screen) == true
    fun browserSettings(): ValueGroup = requireProvider().browserSettings()
    fun createSharedScreen(): Screen = requireProvider().createSharedScreen()
    fun createStandaloneScreen(): CachedClickGuiScreenBridge = requireProvider().createStandaloneScreen()

    private fun requireProvider(): ClickGuiRuntimeHook =
        checkNotNull(provider) { "ClickGUI runtime provider is not installed" }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: ClickGuiRuntimeHook?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
