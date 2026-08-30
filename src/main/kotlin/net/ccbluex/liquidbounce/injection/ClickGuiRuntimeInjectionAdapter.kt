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
package net.ccbluex.liquidbounce.injection

import net.ccbluex.liquidbounce.common.ClientLifecycleState
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.clickgui.CachedClickGuiScreenBridge
import net.ccbluex.liquidbounce.features.module.modules.render.clickgui.ClickGuiRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.render.clickgui.ClickGuiRuntimeHook
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.isTyping as restIsTyping
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.screen.impl.CustomSharedMinecraftScreen
import net.ccbluex.liquidbounce.integration.screen.impl.CustomStandaloneMinecraftScreen
import net.minecraft.client.gui.screens.Screen

object ClickGuiRuntimeInjectionAdapter {

    @JvmStatic
    fun install() = ClickGuiRuntimeBridge.install(Provider)

    private object Provider : ClickGuiRuntimeHook {
        override fun isClientInitialized(): Boolean = ClientLifecycleState.isInitialized
        override fun isTyping(): Boolean = restIsTyping
        override fun isClickGuiScreen(screen: Screen?): Boolean = screen.isClickGuiScreen()
        override fun browserSettings(): ValueGroup = ScreenManager.browserSettings
        override fun createSharedScreen(): Screen = CustomSharedMinecraftScreen(CustomScreenType.CLICK_GUI)
        override fun createStandaloneScreen(): CachedClickGuiScreenBridge =
            CachedStandaloneClickGuiScreen(CustomStandaloneMinecraftScreen(CustomScreenType.CLICK_GUI))
    }
}

private class CachedStandaloneClickGuiScreen(
    override val screen: CustomStandaloneMinecraftScreen,
) : CachedClickGuiScreenBridge {
    override var browserVisible: Boolean
        get() = screen.browser.visible
        set(value) { screen.browser.visible = value }

    override fun sync() = screen.sync()
    override fun close() = screen.close()
}

private fun Screen?.isClickGuiScreen(): Boolean =
    this is CustomSharedMinecraftScreen && screenType == CustomScreenType.CLICK_GUI ||
        this is CustomStandaloneMinecraftScreen && screenType == CustomScreenType.CLICK_GUI
