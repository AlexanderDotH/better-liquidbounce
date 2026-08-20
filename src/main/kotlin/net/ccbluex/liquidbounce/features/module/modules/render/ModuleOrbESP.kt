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

import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.GenericRainbowColorMode
import net.ccbluex.liquidbounce.render.GenericStaticColorMode
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.entity.cameraDistanceSq
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.KeyedAabb
import net.ccbluex.liquidbounce.utils.math.mergeIntersectingAabbsSweep
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.math.toVec3f
import net.ccbluex.liquidbounce.utils.math.worldToLocal
import net.ccbluex.liquidbounce.utils.render.drawLegacy2DMarker
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.phys.AABB

/**
 * Highlights experience orbs through walls.
 */
object ModuleOrbESP : ClientModule("OrbESP", ModuleCategories.RENDER) {

    override val baseKey: String
        get() = "${ConfigSystem.KEY_PREFIX}.module.orbEsp"

    private val maximumDistance by float("MaximumDistance", 128F, 1F..512F)
    val showTracers by boolean("Tracers", false)

    private val modes = choices("Mode", 0) {
        arrayOf(GlowMode, BoxMode, Legacy2DMode)
    }

    private val colorMode = choices("ColorMode", 0) {
        arrayOf(
            GenericStaticColorMode(it, Color4b(120, 230, 120, 255)),
            GenericRainbowColorMode(it),
        )
    }

    @Suppress("unused")
    private val tracerRenderHandler = handler<WorldRenderEvent> { event ->
        if (!showTracers) return@handler

        event.renderEnvironment {
            val eyeVector = Vec3f.eyeVector(camera)

            for (entity in world.entitiesForRendering()) {
                if (!shouldRender(entity)) continue

                val position = entity.interpolateCurrentPosition(event.partialTicks)
                    .subtract(camera.position())
                    .toVec3f()

                drawLine(
                    argb = getColor().argb,
                    p1 = eyeVector,
                    p2 = position,
                )
            }
        }
    }

    object GlowMode : Mode("Glow") {
        override val parent: ModeValueGroup<Mode>
            get() = modes

        private val styleConfig = EspGlowStyleConfig(this)

        internal val style: EspGlowStyle
            get() = styleConfig.style
    }

    private object BoxMode : Mode("Box") {
        override val parent: ModeValueGroup<Mode>
            get() = modes

        private val box = AABB(-0.125, 0.125, -0.125, 0.125, 0.375, 0.125)
        private val mergeIntersecting by boolean("MergeIntersecting", false)
        private val entities = mutableListOf<Entity>()

        override fun disable() {
            entities.clear()
            super.disable()
        }

        @Suppress("unused")
        private val tickHandler = handler<GameTickEvent> {
            entities.clear()
            world.entitiesForRendering().filterTo(entities, ::shouldRender)
        }

        @Suppress("unused")
        private val renderHandler = handler<WorldRenderEvent> { event ->
            if (entities.isEmpty()) return@handler

            val color = getColor()
            val faceColor = color.with(a = 50)
            val outlineColor = color.with(a = 100)

            event.renderEnvironment {
                if (!mergeIntersecting) {
                    renderBoxes(event, faceColor, outlineColor)
                    return@renderEnvironment
                }

                renderMergedBoxes(event, color, faceColor, outlineColor)
            }
        }

        private fun net.ccbluex.liquidbounce.render.WorldRenderEnvironment.renderBoxes(
            event: WorldRenderEvent,
            faceColor: Color4b,
            outlineColor: Color4b,
        ) {
            for (entity in entities) {
                val position = entity.interpolateCurrentPosition(event.partialTicks)

                withPositionRelativeToCamera(position) {
                    drawBox(box, faceColor, outlineColor)
                }
            }
        }

        private fun net.ccbluex.liquidbounce.render.WorldRenderEnvironment.renderMergedBoxes(
            event: WorldRenderEvent,
            color: Color4b,
            faceColor: Color4b,
            outlineColor: Color4b,
        ) {
            val mergedBoxes = mergeIntersectingAabbsSweep(
                entities.mapToArray { entity ->
                    val position = entity.interpolateCurrentPosition(event.partialTicks)
                    KeyedAabb(box.move(position), color)
                }.asList(),
            )

            for ((mergedBox, _) in mergedBoxes) {
                val (origin, localBox) = mergedBox.worldToLocal()
                withPositionRelativeToCamera(origin) {
                    drawBox(localBox, faceColor, outlineColor)
                }
            }
        }
    }

    private object Legacy2DMode : Mode("Legacy2D") {
        override val parent: ModeValueGroup<Mode>
            get() = modes

        private val scale by float("Scale", 0.1F, 0.02F..0.3F)
        private val yOffset by float("YOffset", 0F, -1F..1F)
        private val backgroundAlpha by int("BackgroundAlpha", 150, 0..255)
        private val entities = mutableListOf<Entity>()

        override fun disable() {
            entities.clear()
            super.disable()
        }

        @Suppress("unused")
        private val tickHandler = handler<GameTickEvent> {
            entities.clear()
            world.entitiesForRendering().filterTo(entities, ::shouldRender)
        }

        @Suppress("unused")
        private val renderHandler = handler<WorldRenderEvent> { event ->
            if (entities.isEmpty()) return@handler

            val foregroundColor = getColor().argb
            val backgroundColor = Color4b.BLACK.with(a = backgroundAlpha).argb

            event.renderEnvironment {
                for (entity in entities) {
                    val position = entity.interpolateCurrentPosition(event.partialTicks)
                        .add(0.0, yOffset.toDouble(), 0.0)

                    drawLegacy2DMarker(
                        pos = position,
                        entityHeight = entity.boundingBox.ysize,
                        scale = scale,
                        foregroundArgb = foregroundColor,
                        backgroundArgb = backgroundColor,
                    )
                }
            }
        }
    }

    fun shouldRender(entity: Entity?): Boolean {
        if (entity !is ExperienceOrb) return false

        return entity.eyePosition.cameraDistanceSq() <= maximumDistance.sq()
    }

    fun getColor(): Color4b = colorMode.activeMode.getColor(null)
}
