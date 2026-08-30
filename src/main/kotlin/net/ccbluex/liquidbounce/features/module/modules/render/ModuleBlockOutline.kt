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

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.blockoutline.flattenBlockOutlineBox
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawBoxSide
import net.ccbluex.liquidbounce.render.drawShape
import net.ccbluex.liquidbounce.render.drawShapeSide
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.math.Easing
import net.ccbluex.liquidbounce.utils.math.minus
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Block Outline module
 *
 * Changes the way Minecraft highlights blocks.
 *
 * TODO: Implement GUI Information Panel
 *
 * The level-renderer injection delegates its block-outline cancellation decision to this facade.
 */
object ModuleBlockOutline : ClientModule("BlockOutline", ModuleCategories.RENDER, aliases = listOf("BlockOverlay")) {

    private val sideOnly by boolean("SideOnly", true)
    private val color by color("Color", Color4b(68, 117, 255, 70))
    private val outlineColor by color("Outline", Color4b(68, 117, 255, 150))

    internal object Glow : ToggleableValueGroup(this, "Glow", true) {
        private val styleConfig = EspGlowStyleConfig(this)

        internal val style: EspGlowStyle
            get() = styleConfig.style
    }

    private object Slide : ToggleableValueGroup(this, "Slide", true) {
        val time by int("Time", 150, 1..1000, "ms")
        val easing by easing("Easing", Easing.LINEAR)
    }

    init {
        tree(Glow)
        tree(Slide)
    }

    private var currentPosition: AABB? = null
    private var previousPosition: AABB? = null
    private var lastChange = 0L

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val target = mc.hitResult
        if (target !is BlockHitResult || target.type == HitResult.Type.MISS) {
            resetPositions()
            return@handler
        }

        val blockPos = target.blockPos
        val blockState = world.getBlockState(blockPos)
        if (blockState.isAir || !world.worldBorder.isWithinBounds(blockPos)) {
            resetPositions()
            return@handler
        }

        val side = target.direction
        val shape = blockState.getShape(this.world, blockPos, CollisionContext.of(mc.cameraEntity!!))
        if (shape.isEmpty) {
            resetPositions()
            return@handler
        }

        val singleBox = shape.toAabbs().singleOrNull()
        if (singleBox == null) {
            resetPositions()

            val localHitPos = target.location - blockPos

            event.renderEnvironment {
                withPositionRelativeToCamera(blockPos) {
                    drawHighlight(shape, side, localHitPos, color, outlineColor)
                }
            }
            event.renderShaderMask {
                withPositionRelativeToCamera(blockPos) {
                    drawHighlight(shape, side, localHitPos, it, null)
                }
            }
            return@handler
        }

        val finalPosition = (if (sideOnly) flattenBlockOutlineBox(singleBox, side) else singleBox).move(blockPos)
        if (currentPosition != finalPosition) {
            previousPosition = currentPosition
            currentPosition = finalPosition
            lastChange = System.currentTimeMillis()
        }

        val renderPosition = if (previousPosition != null && Slide.running) {
            val factor = Slide.easing.getFactor(lastChange, System.currentTimeMillis(), Slide.time.toFloat()).toDouble()

            val previousPosition = previousPosition!!
            AABB(
                Mth.lerp(factor, previousPosition.minX, finalPosition.minX),
                Mth.lerp(factor, previousPosition.minY, finalPosition.minY),
                Mth.lerp(factor, previousPosition.minZ, finalPosition.minZ),
                Mth.lerp(factor, previousPosition.maxX, finalPosition.maxX),
                Mth.lerp(factor, previousPosition.maxY, finalPosition.maxY),
                Mth.lerp(factor, previousPosition.maxZ, finalPosition.maxZ)
            )
        } else {
            finalPosition
        }

        val translatedPosition = renderPosition - event.camera.position()

        event.renderEnvironment {
            drawHighlight(translatedPosition, side, color, outlineColor)
        }
        event.renderShaderMask {
            drawHighlight(translatedPosition, side, it, null)
        }
    }

    private fun WorldRenderEnvironment.drawHighlight(
        shape: VoxelShape,
        side: Direction,
        hitPos: Vec3,
        faceColor: Color4b?,
        edgeColor: Color4b?,
    ) {
        if (sideOnly) {
            drawShapeSide(shape, side, hitPos, faceColor, edgeColor)
        } else {
            drawShape(shape, faceColor, edgeColor)
        }
    }

    private fun WorldRenderEnvironment.drawHighlight(
        box: AABB,
        side: Direction,
        faceColor: Color4b?,
        edgeColor: Color4b?,
    ) {
        if (sideOnly) {
            drawBoxSide(box, side, faceColor, edgeColor)
        } else {
            drawBox(box, faceColor, edgeColor)
        }
    }

    private fun WorldRenderEvent.renderShaderMask(draw: WorldRenderEnvironment.(Color4b) -> Unit) {
        val maskColor = when {
            !color.isTransparent -> color.with(a = 255)
            !outlineColor.isTransparent -> outlineColor.with(a = 255)
            else -> return
        }

        EspShaderRenderer.contributeGlow(
            this,
            EspGlowSource.BLOCK_OUTLINE,
            Glow.style.takeIf { Glow.running },
        ) {
            draw(maskColor)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        resetPositions()
        lastChange = System.currentTimeMillis()
    }

    private fun resetPositions() {
        currentPosition = null
        previousPosition = null
    }

}
