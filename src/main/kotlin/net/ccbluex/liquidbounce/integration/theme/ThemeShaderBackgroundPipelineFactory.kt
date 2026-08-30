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
package net.ccbluex.liquidbounce.integration.theme

import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.screenQuadSnippet
import net.ccbluex.liquidbounce.render.ClientRenderPipelines.withUniformBuffer
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.utils.client.clientIdentifier
import net.ccbluex.liquidbounce.utils.kotlin.optional
import net.minecraft.resources.Identifier
import java.util.Locale

internal data class ThemeShaderBackgroundPipeline(
    val pipeline: RenderPipeline,
    val fragmentShaderId: Identifier,
)

internal object ThemeShaderBackgroundPipelineFactory {

    fun build(metadata: ThemeMetadata, background: Background): ThemeShaderBackgroundPipeline {
        val backgroundName = background.name.lowercase(Locale.US)
        val themeName = metadata.name.lowercase(Locale.US)
        val fragmentShaderId = clientIdentifier("shader/fsh/theme-bg-$themeName-$backgroundName")
        val pipeline = RenderPipeline.Builder()
            .withLocation(clientIdentifier("pipeline/theme-bg-$themeName"))
            .screenQuadSnippet()
            .withFragmentShader(fragmentShaderId)
            .withUniformBuffer(ClientUniformDefine.THEME_BACKGROUND)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(optional())
            .build()

        return ThemeShaderBackgroundPipeline(pipeline, fragmentShaderId)
    }
}
