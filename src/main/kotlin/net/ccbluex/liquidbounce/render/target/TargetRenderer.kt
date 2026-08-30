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

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.minecraft.world.entity.Entity

class TargetRenderer(
    owner: ToggleableValueGroup,
    val target: () -> Entity?,
) : ToggleableValueGroup(owner, "TargetRendering", true) {

    private val appearance = modes(owner, "Mode", TARGET_RENDERING_DEFAULT_MODE_INDEX) {
        arrayOf(
            LegacyTargetAppearance(it),
            CircleTargetAppearance(owner, it),
            ImageTargetAppearance(owner, it),
            GlowingCircleTargetAppearance(owner, it),
            GhostTargetAppearance(it),
            HeartTargetAppearance(it),
            TextTargetAppearance(owner, it),
            ArrowTargetAppearance(it),
            GlowTargetAppearance(it),
        ).also { appearances ->
            check(appearances.map(Mode::name) == TARGET_RENDERING_MODE_NAMES)
        }
    }

    init {
        doNotIncludeAlways()
        TargetGlowSourceRegistry.register(::currentGlowSelection)
    }

    private fun currentGlowSelection(): TargetGlowSelection? {
        if (!running) return null
        val glow = appearance.activeMode as? GlowTargetAppearance ?: return null
        return target()?.let { current -> TargetGlowSelection(current, glow.color, glow.style) }
    }

    @Suppress("unused")
    private val worldRenderHandler = handler<WorldRenderEvent> { event ->
        val mode = appearance.activeMode as? WorldTargetAppearance ?: return@handler
        val currentTarget = target() ?: return@handler
        with(mode) {
            event.renderEnvironment { render(currentTarget, event.partialTicks) }
        }
    }

    @Suppress("unused")
    private val guiRenderHandler = handler<OverlayRenderEvent> { event ->
        val mode = appearance.activeMode as? GuiTargetAppearance ?: return@handler
        val currentTarget = target() ?: return@handler
        with(mode) { event.context.render(currentTarget, event.tickDelta) }
    }
}

internal const val TARGET_RENDERING_DEFAULT_MODE_INDEX = 3

internal val TARGET_RENDERING_MODE_NAMES = listOf(
    "Legacy", "Circle", "Image", "GlowingCircle", "Ghost", "Hearts", "Text2D", "Arrow", "Glow",
)

internal class TargetGlowSettings(owner: ValueGroup, defaultColor: Color4b) {
    val color by owner.color("Color", defaultColor)
    private val styleConfig = EspGlowStyleConfig(owner)
    val style: EspGlowStyle
        get() = styleConfig.style
}

internal val targetDefaultColor = Color4b.LIQUID_BOUNCE.alpha(100)
