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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.GenericDistanceHSBColorMode
import net.ccbluex.liquidbounce.render.GenericEntityHealthColorMode
import net.ccbluex.liquidbounce.render.GenericRainbowColorMode
import net.ccbluex.liquidbounce.render.GenericStaticColorMode
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.TracerRenderBatch
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.TracerSegment
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.drawTracerBatch
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspHaloStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.features.combat.runtime.EntityTaggingManager
import net.ccbluex.liquidbounce.features.render.RenderedEntities
import net.ccbluex.liquidbounce.utils.entity.cameraDistanceSq
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.render.engine.type.toVec3f

/**
 * Tracers module
 *
 * Draws a line to every entity a certain radius.
 */

object ModuleTracers : ClientModule("Tracers", ModuleCategories.RENDER) {

    private val renderModes = choices("Mode", 0) {
        arrayOf(LineMode, GlowMode)
    }

    private object LineMode : Mode("Line") {
        override val parent: ModeValueGroup<Mode>
            get() = renderModes
    }

    private object GlowMode : Mode("Glow") {
        override val parent: ModeValueGroup<Mode>
            get() = renderModes

        private val styleConfig = EspHaloStyleConfig(this)

        val style
            get() = styleConfig.style
    }

    private val colorModes = choices("ColorMode", 0) {
        arrayOf(
            GenericDistanceHSBColorMode.entity(it),
            GenericEntityHealthColorMode(it),
            GenericStaticColorMode(it, Color4b(0, 160, 255, 255)),
            GenericRainbowColorMode(it)
        )
    }

    private val lineWidth by float("LineWidth", 1f, 1f..16f)

    private val maximumDistance by float("MaximumDistance", 128F, 1F..512F)

    override fun onEnabled() {
        RenderedEntities.subscribe(this)
    }

    override fun onDisabled() {
        RenderedEntities.unsubscribe(this)
    }

    val renderHandler = handler<WorldRenderEvent> { event ->
        if (RenderedEntities.isEmpty()) {
            return@handler
        }

        val eyePosition = Vec3f.eyeVector(event.camera)
        val cameraPosition = event.camera.position()
        val maximumDistanceSq = maximumDistance.sq()
        val segments = buildList {
            for (entity in RenderedEntities) {
                if (entity.position().cameraDistanceSq().toFloat() > maximumDistanceSq) continue

                val color = if (FriendManager.isFriend(entity)) {
                    Color4b.BLUE
                } else {
                    EntityTaggingManager.getTag(entity).color ?: colorModes.activeMode.getColor(entity)
                }
                val position = entity.interpolateCurrentPosition(event.partialTicks)
                    .subtract(cameraPosition)
                    .toVec3f()
                add(
                    TracerSegment(
                        color = color,
                        eyePosition = eyePosition,
                        targetPosition = position,
                    )
                )
            }
        }
        val batch = TracerRenderBatch(segments, lineWidth)
        val glowMode = renderModes.activeMode === GlowMode

        event.renderEnvironment {
            drawTracerBatch(batch, glowMask = false, depthTested = glowMode)
        }

        if (!glowMode) return@handler

        batch.contributeGlowIfPresent {
            EspShaderRenderer.contributeGlow(event, EspGlowSource.TRACERS, GlowMode.style) {
                drawTracerBatch(it, glowMask = true)
            }
        }
    }
}
