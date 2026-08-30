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
package net.ccbluex.liquidbounce.render.placement

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.ccbluex.fastutil.fastIterator
import net.ccbluex.liquidbounce.render.EMPTY_BOX
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.math.high32
import net.ccbluex.liquidbounce.utils.math.low32
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB

internal class PlacementFrameRenderer(
    private val placementRenderer: PlacementRenderer,
    private val id: Int,
    private val inList: Long2ObjectOpenHashMap<InOutBlockData>,
    private val currentList: Long2ObjectOpenHashMap<CurrentBlockData>,
    private val outList: Long2ObjectOpenHashMap<InOutBlockData>,
    private val onExpired: (Long) -> Unit,
) {
    private val blockPosCache = BlockPos.MutableBlockPos()
    private val expiredPositions = LongArrayList()
    private lateinit var color: Color4b
    private lateinit var outlineColor: Color4b

    fun render(event: WorldRenderEvent, time: Long) {
        color = placementRenderer.getColor(id)
        outlineColor = placementRenderer.getOutlineColor(id)
        event.renderEnvironment {
            renderIncoming(time)
            renderCurrent()
            expiredPositions.clear()
            renderOutgoing(time)
            refreshExpiredNeighbors()
        }
    }

    context(env: WorldRenderEnvironment)
    private fun renderIncoming(time: Long) {
        with(placementRenderer) {
            inList.long2ObjectEntrySet().removeIf { entry ->
                val pos = entry.longKey
                val value = entry.value
                val sizeFactor = startSizeCurve.getFactor(value.startTime, time, inTime.toFloat())
                val expand = Mth.lerp(sizeFactor, startSize, 1f).let { if (it < 1f) 1f - it else it }
                val colorFactor = fadeInCurve.getFactor(value.startTime, time, inTime.toFloat())
                drawEntryBox(pos, value.cullData, animatedBox(expand, value.box), colorFactor)

                if (time - value.startTime >= inTime) {
                    if (keep) currentList.put(pos, value.toCurrent())
                    else outList.put(pos, value.copy(startTime = time))
                    true
                } else false
            }
        }
    }

    context(env: WorldRenderEnvironment)
    private fun renderCurrent() {
        currentList.fastIterator().forEach { entry ->
            drawEntryBox(entry.longKey, entry.value.cullData, entry.value.box, 1f)
        }
    }

    context(env: WorldRenderEnvironment)
    private fun renderOutgoing(time: Long) {
        with(placementRenderer) {
            outList.long2ObjectEntrySet().removeIf { entry ->
                val pos = entry.longKey
                val value = entry.value
                val sizeFactor = endSizeCurve.getFactor(value.startTime, time, outTime.toFloat())
                val expand = 1f - Mth.lerp(sizeFactor, 1f, endSize)
                val colorFactor = 1f - fadeOutCurve.getFactor(value.startTime, time, outTime.toFloat())
                drawEntryBox(pos, value.cullData, animatedBox(expand, value.box), colorFactor)

                if (time - value.startTime >= outTime) {
                    expiredPositions.add(pos)
                    true
                } else false
            }
        }
    }

    context(ctx: WorldRenderEnvironment)
    private fun drawEntryBox(blockPos: Long, cullData: Long, box: AABB, colorFactor: Float) {
        ctx.withPositionRelativeToCamera(blockPosCache.set(blockPos)) {
            drawBox(
                box,
                color.fade(colorFactor),
                outlineColor.fade(colorFactor),
                cullData.high32(),
                cullData.low32(),
            )
        }
    }

    private fun refreshExpiredNeighbors() {
        for (index in expiredPositions.indices) onExpired(expiredPositions.getLong(index))
    }

    private fun animatedBox(expand: Float, box: AABB): AABB = when (expand) {
        1f -> box
        0f -> EMPTY_BOX
        else -> {
            val factor = if (expand < 1) -0.5 * expand else (expand - 1) * 0.5
            box.inflate(box.xsize * factor, box.ysize * factor, box.zsize * factor)
        }
    }
}

@JvmRecord
internal data class InOutBlockData(val startTime: Long, val cullData: Long, val box: AABB) {
    fun toCurrent() = CurrentBlockData(cullData, box)
}

@JvmRecord
internal data class CurrentBlockData(val cullData: Long, val box: AABB) {
    fun toInOut(startTime: Long) = InOutBlockData(startTime, cullData, box)
}
