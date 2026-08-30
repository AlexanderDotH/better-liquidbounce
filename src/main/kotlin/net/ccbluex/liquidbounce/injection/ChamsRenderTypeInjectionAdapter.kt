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

import net.ccbluex.liquidbounce.features.module.modules.render.chams.ChamsRenderTypeBridge
import net.ccbluex.liquidbounce.features.module.modules.render.chams.ChamsRenderTypeHook
import net.ccbluex.liquidbounce.render.RenderTypeAccess
import net.ccbluex.liquidbounce.render.withOutputTarget
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderType

object ChamsRenderTypeInjectionAdapter {

    @JvmStatic
    fun install() = ChamsRenderTypeBridge.install(Provider)

    private object Provider : ChamsRenderTypeHook {
        override fun name(renderType: RenderType): String = renderType.accessor().name

        override fun withOutputTarget(renderType: RenderType, outputTarget: OutputTarget): RenderType {
            val accessor = renderType.accessor()
            return RenderType.create(
                "liquidbounce_chams/${accessor.name}",
                accessor.state.withOutputTarget(outputTarget),
            )
        }
    }
}

@Suppress("CAST_NEVER_SUCCEEDS")
private fun RenderType.accessor() = this as RenderTypeAccess
