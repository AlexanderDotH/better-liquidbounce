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

package net.ccbluex.liquidbounce.features.module.modules.render.storageesp

import com.mojang.blaze3d.pipeline.RenderTarget
import kotlinx.atomicfu.atomic
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.CachedMeshStorage
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.addShapeFaces
import net.ccbluex.liquidbounce.render.buildMesh
import net.ccbluex.liquidbounce.render.drawGenericBlockESP
import net.ccbluex.liquidbounce.render.engine.esp.StorageShaderEffect
import net.ccbluex.liquidbounce.render.engine.esp.StorageShaderMaskPolicy
import net.ccbluex.liquidbounce.render.translate
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.math.PositionedVoxelShape
import net.minecraft.world.level.block.state.BlockState

abstract class StorageShaderMode internal constructor(
    name: String,
    moduleName: String,
    private val effect: StorageShaderEffect,
    private val parentProvider: () -> ModeValueGroup<Mode>,
    private val distanceFadeProvider: () -> DistanceFadeUniformValueGroup,
    private val hasTrackedBlocks: () -> Boolean,
    private val collectTrackedShapes: ((BlockState) -> Boolean) ->
        List<PositionedVoxelShape<StorageEspCategory>>,
) : Mode(name) {

    override val parent: ModeValueGroup<Mode>
        get() = parentProvider()

    private val dirtyFlag = atomic(true)
    private val renderState = CachedMeshStorage("$moduleName $name")

    internal fun markDirty() {
        if (running) dirtyFlag.value = true
    }

    override fun enable() {
        dirtyFlag.value = true
        super.enable()
    }

    override fun disable() {
        renderState.clearStates()
        renderState.clearBuffers()
        super.disable()
    }

    internal fun drawMask(renderTarget: RenderTarget): Boolean = renderTarget.drawGenericBlockESP(
        renderState = renderState,
        pipeline = ClientRenderPipelines.outlineQuads(useColor = true),
        distanceFade = distanceFadeProvider(),
    )

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (!hasTrackedBlocks()) {
            renderState.clearStates()
            return@handler
        }
        if (!dirtyFlag.compareAndSet(expect = true, update = false)) return@handler

        val shapes = collectTrackedShapes { state ->
            !StorageShaderMaskPolicy.requiresCachedGeometry(state.renderShape, effect)
        }
        renderState.buildMesh(
            pipeline = ClientRenderPipelines.outlineQuads(useColor = true),
            origin = player.blockPosition(),
        ) { pose, origin ->
            shapes.forEach { shape ->
                pose.withPush {
                    translate(shape.blockPos, origin)
                    addShapeFaces(last().pose(), shape.shape, shape.key.color)
                }
            }
        }
    }
}
