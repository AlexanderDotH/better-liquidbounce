/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.render.blockesp

import com.mojang.blaze3d.pipeline.RenderTarget
import kotlinx.atomicfu.atomic
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.CachedMeshStorage
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.addShapeFaces
import net.ccbluex.liquidbounce.render.buildMesh
import net.ccbluex.liquidbounce.render.drawGenericBlockESP
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.translate
import net.ccbluex.liquidbounce.render.withPush
import net.minecraft.world.level.block.Block

internal data class BlockMergeKey(val block: Block, val color: Color4b?)

interface BlockEspDirtyMode {
    fun markDirty()
}

abstract class BlockEspMode internal constructor(
    name: String,
) : Mode(name), BlockEspDirtyMode {
    protected var useColor = false
    protected val dirtyFlag = atomic(true)

    final override fun markDirty() {
        if (running) dirtyFlag.value = true
    }

    final override fun enable() {
        dirtyFlag.value = true
        super.enable()
    }
}

abstract class BlockEspCachedMaskMode internal constructor(
    name: String,
    private val runtimeProvider: () -> BlockEspRuntime,
) : BlockEspMode(name) {
    private val renderState by lazy { CachedMeshStorage("BlockESP $name") }
    private val runtime: BlockEspRuntime
        get() = runtimeProvider()

    abstract val style: EspGlowStyle

    override fun disable() {
        renderState.clearStates()
        renderState.clearBuffers()
        super.disable()
    }

    internal fun drawMask(renderTarget: RenderTarget): Boolean = renderTarget.drawGenericBlockESP(
        renderState,
        ClientRenderPipelines.outlineQuads(useColor),
        runtime.distanceFadeSettings,
    ) { runtime.modeTransforms(useColor, colorModulatorAlpha = 255) }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (runtime.trackerIsEmpty()) {
            renderState.clearStates()
            return@handler
        }
        if (!dirtyFlag.compareAndSet(expect = true, update = false)) return@handler

        val colorMode = runtime.activeBlockColorMode
        useColor = colorMode.isParamSensitive
        renderState.buildMesh(
            pipeline = ClientRenderPipelines.outlineQuads(useColor),
            origin = player.blockPosition(),
        ) { pose, meshOrigin ->
            runtime.collectBlockShapes(colorMode, useColor).forEach { mergedShape ->
                pose.withPush {
                    translate(mergedShape.blockPos, meshOrigin)
                    addShapeFaces(last().pose(), mergedShape.shape, mergedShape.key.color?.alpha(255))
                }
            }
        }
    }
}
