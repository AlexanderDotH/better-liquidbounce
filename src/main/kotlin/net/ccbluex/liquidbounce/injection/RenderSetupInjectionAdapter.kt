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

import net.ccbluex.liquidbounce.injection.mixins.minecraft.render.MixinRenderSetupAccessor
import net.ccbluex.liquidbounce.render.RenderInjectionAccess
import net.ccbluex.liquidbounce.render.RenderSetupFactory

object RenderSetupInjectionAdapter {

    @JvmStatic
    fun install() {
        RenderInjectionAccess.install(RenderSetupFactory(::copyWithOutputTarget))
    }

    private fun copyWithOutputTarget(
        setup: net.minecraft.client.renderer.rendertype.RenderSetup,
        outputTarget: net.minecraft.client.renderer.rendertype.OutputTarget,
    ): net.minecraft.client.renderer.rendertype.RenderSetup {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val accessor = setup as MixinRenderSetupAccessor
        return MixinRenderSetupAccessor.`liquid_bounce$invokeInit`(
            accessor.pipeline,
            accessor.textures,
            accessor.useLightmap,
            accessor.useOverlay,
            accessor.layeringTransform,
            outputTarget,
            accessor.textureTransform,
            accessor.outlineProperty,
            accessor.affectsCrumbling,
            accessor.sortOnUpload,
        )
    }
}
