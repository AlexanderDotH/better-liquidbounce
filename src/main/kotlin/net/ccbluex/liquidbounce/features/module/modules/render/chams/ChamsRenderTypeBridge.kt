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
package net.ccbluex.liquidbounce.features.module.modules.render.chams

import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderType

interface ChamsRenderTypeHook {
    fun name(renderType: RenderType): String
    fun withOutputTarget(renderType: RenderType, outputTarget: OutputTarget): RenderType
}

object ChamsRenderTypeBridge {

    @Volatile
    private var provider: ChamsRenderTypeHook? = null

    @JvmStatic
    @Synchronized
    fun install(provider: ChamsRenderTypeHook) {
        check(this.provider == null) { "Chams render-type provider is already installed" }
        this.provider = provider
    }

    fun name(renderType: RenderType): String? = provider?.name(renderType)

    fun withOutputTarget(renderType: RenderType, outputTarget: OutputTarget): RenderType =
        provider?.withOutputTarget(renderType, outputTarget) ?: renderType
}
