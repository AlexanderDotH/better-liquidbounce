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
package net.ccbluex.liquidbounce.render.target

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawCircle
import net.ccbluex.liquidbounce.render.drawCircleOutline
import net.ccbluex.liquidbounce.render.drawGradientCircle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import org.joml.Vector3f
import kotlin.math.min

internal class LegacyTargetAppearance(override val parent: ModeValueGroup<*>) : WorldTargetAppearance("Legacy") {
    private val size by float("Size", 0.5f, 0.1f..2f)
    private val height by float("Height", 0.1f, 0.02f..2f)
    private val color by color("Color", targetDefaultColor)
    private val extraYOffset by float("ExtraYOffset", 0.1f, 0f..1f)

    override fun WorldRenderEnvironment.render(entity: Entity, partialTicks: Float) {
        val box = AABB(-size.toDouble(), 0.0, -size.toDouble(), size.toDouble(), height.toDouble(), size.toDouble())
        val position = entity.interpolateCurrentPosition(partialTicks)
            .add(0.0, entity.bbHeight.toDouble() + extraYOffset, 0.0)
        withPositionRelativeToCamera(position) { drawBox(box, color) }
    }
}

internal class CircleTargetAppearance(
    owner: ToggleableValueGroup,
    override val parent: ModeValueGroup<*>,
) : WorldTargetAppearance("Circle") {
    private val radius by float("Radius", 0.85f, 0.1f..2f)
    private val innerRadius by float("InnerRadius", 0f, 0f..2f).onChange { min(radius, it) }
    private val heightMode = modes(owner, "HeightMode") {
        arrayOf(
            TargetHeightMode.Feet(it), TargetHeightMode.Top(it), TargetHeightMode.Relative(it),
            TargetHeightMode.Health(it), TargetHeightMode.Animated(it),
        )
    }
    private val outerColor by color("OuterColor", targetDefaultColor)
    private val innerColor by color("InnerColor", targetDefaultColor)
    private val outlineColor by color("Color", Color4b.fullAlpha(0x007CFF))

    override fun WorldRenderEnvironment.render(entity: Entity, partialTicks: Float) {
        val position = entity.targetPosition(heightMode.activeMode.getHeight(entity, partialTicks), partialTicks)
        withPositionRelativeToCamera(position) {
            drawGradientCircle(radius, innerRadius, outerColor, innerColor)
            drawCircleOutline(radius, outlineColor)
        }
    }
}

internal class GlowingCircleTargetAppearance(
    owner: ToggleableValueGroup,
    override val parent: ModeValueGroup<*>,
) : WorldTargetAppearance("GlowingCircle") {
    private val radius by float("Radius", 0.85f, 0.1f..2f)
    private val heightMode = modes(owner, "HeightMode") {
        arrayOf(
            TargetHeightMode.Feet(it), TargetHeightMode.Top(it), TargetHeightMode.Relative(it),
            TargetHeightMode.Health(it), TargetHeightMode.Animated(it),
        )
    }
    private val color by color("OuterColor", targetDefaultColor)
    private val glowColor by color("GlowColor", Color4b.LIQUID_BOUNCE.alpha(0))
    private val glowHeightSetting by float("GlowHeight", 0.3f, -1f..1f)
    private val outlineColor by color("Color", Color4b.fullAlpha(0x007CFF))

    override fun WorldRenderEnvironment.render(entity: Entity, partialTicks: Float) {
        val mode = heightMode.activeMode
        val height = mode.getHeight(entity, partialTicks)
        val glowHeight = if (mode is TargetHeightMode.WithGlow) {
            mode.getGlowHeight(entity, partialTicks) - height
        } else {
            glowHeightSetting.toDouble()
        }
        withPositionRelativeToCamera(entity.targetPosition(height, partialTicks)) {
            drawGradientCircle(radius, radius, color, glowColor, Vector3f(0f, glowHeight.toFloat(), 0f))
            drawCircle(radius, color)
            drawCircleOutline(radius, outlineColor)
        }
    }
}

internal class GlowTargetAppearance(override val parent: ModeValueGroup<*>) : WorldTargetAppearance("Glow") {
    private val settings = TargetGlowSettings(this, targetDefaultColor)
    val color: Color4b get() = settings.color
    val style: EspGlowStyle get() = settings.style
    override fun WorldRenderEnvironment.render(entity: Entity, partialTicks: Float) = Unit
}

internal fun Entity.targetPosition(height: Double, partialTicks: Float) =
    interpolateCurrentPosition(partialTicks).add(0.0, height, 0.0)
