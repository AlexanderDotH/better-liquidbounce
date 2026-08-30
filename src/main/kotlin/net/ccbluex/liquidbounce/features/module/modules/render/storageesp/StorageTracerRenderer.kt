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

import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.drawLines
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.render.engine.type.toVec3f
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity

internal class StorageTracerRenderer<C : StorageEspCategory>(
    private val types: Array<C>,
    private val blockPositions: (C) -> Sequence<BlockPos>,
    private val categorizeEntity: (Entity?) -> C?,
) : MinecraftShortcuts {

    fun render(event: WorldRenderEvent) {
        val activeTypes = types.filter { it.tracersEnabled() }
        if (activeTypes.isEmpty()) return

        event.renderEnvironment {
            val eyeVector = Vec3f.eyeVector(camera)
            renderBlocks(activeTypes, eyeVector)
            renderEntities(event, eyeVector)
        }
    }

    private fun C.tracersEnabled() = !color.isTransparent && this is TracedStorageEspCategory && tracers

    private fun WorldRenderEnvironment.renderBlocks(types: List<C>, eyeVector: Vec3f) {
        types.forEach { type ->
            blockPositions(type).forEach { blockPos ->
                if (!type.shouldRender(blockPos, ignoreDistance = false)) return@forEach
                val position = blockPos.center.subtract(camera.position()).toVec3f()
                drawLine(eyeVector, position, type.color.argb)
            }
        }
    }

    private fun WorldRenderEnvironment.renderEntities(event: WorldRenderEvent, eyeVector: Vec3f) {
        mc.level?.entitiesForRendering()?.forEach { entity ->
            val category = categorizeEntity(entity) ?: return@forEach
            if (!category.shouldRender(entity, ignoreDistance = false) || !category.tracersEnabled()) return@forEach

            val position = entity.interpolateCurrentPosition(event.partialTicks).subtract(camera.position()).toVec3f()
            val topPosition = position.add(0f, entity.bbHeight, 0f)
            drawLines(category.color.argb, eyeVector, position, position, topPosition)
        }
    }
}

interface TracedStorageEspCategory : StorageEspCategory {
    val tracers: Boolean
}
