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

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleStorageESP
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspChamsMode
import net.ccbluex.liquidbounce.interfaces.PreparedFrameAddition
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.utils.render.withOutputTextureOverride
import net.ccbluex.liquidbounce.utils.render.writeStd140

internal class EspPreparedChamsCapturer : AutoCloseable {
    private val mask = EspRenderTargetHolder("LiquidBounce ESP Chams Mask", true, GpuFormat.RGBA8_UNORM)
    private val styleData = ClientUniformDefine.ESP_CHAMS.createSingleBuffer()
    private val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    private var style = EspChamsStyle.DEFAULT

    var hasContribution = false
        private set

    fun beginFrame() {
        hasContribution = false
        style = EspChamsStyle.DEFAULT
    }

    fun capture(bridge: PreparedFrameAddition, mainTarget: RenderTarget) {
        val entityNodes = bridge.`liquid_bounce$hasEspMask`(EspMaskLayer.ENTITY_CHAMS)
        val storageNodes = bridge.`liquid_bounce$hasEspMask`(EspMaskLayer.STORAGE_CHAMS)
        if (!entityNodes && !storageNodes && !ModuleStorageESP.ChamsMode.running) return

        val target = mask.initAndClear(mainTarget.width, mainTarget.height)
        if (entityNodes) execute(bridge, EspMaskLayer.ENTITY_CHAMS, target)
        if (storageNodes) execute(bridge, EspMaskLayer.STORAGE_CHAMS, target)
        val cachedStorage = ModuleStorageESP.ChamsMode.running && ModuleStorageESP.ChamsMode.drawMask(target)
        hasContribution = entityNodes || storageNodes || cachedStorage
        if (!hasContribution) return

        style = EspShaderStyleResolver.resolveChams(
            EspChamsMode.style.takeIf { EspChamsMode.running && entityNodes },
            ModuleStorageESP.ChamsMode.style.takeIf {
                ModuleStorageESP.ChamsMode.running && (storageNodes || cachedStorage)
            },
        )
    }

    fun composite(target: RenderTarget) {
        if (!hasContribution) return
        styleData.writeStd140 { putVec4(style.opacity, 0f, 0f, 0f) }
        target.createRenderPass({ "LiquidBounce ESP Chams composite" }).use { pass ->
            pass.setPipeline(ClientRenderPipelines.EspChamsComposite)
            pass.bindTexture("MaskSampler", requireNotNull(mask.raw).colorTextureView, sampler)
            pass.setUniform(ClientUniformDefine.ESP_CHAMS.uboName, styleData)
            pass.draw(3, 1, 0, 0)
        }
    }

    private fun execute(bridge: PreparedFrameAddition, layer: EspMaskLayer, target: RenderTarget) {
        withOutputTextureOverride(target.colorTextureView, target.depthTextureView) {
            bridge.`liquid_bounce$executeEspMask`(layer)
        }
    }

    override fun close() {
        mask.close()
        styleData.buffer().close()
    }
}
