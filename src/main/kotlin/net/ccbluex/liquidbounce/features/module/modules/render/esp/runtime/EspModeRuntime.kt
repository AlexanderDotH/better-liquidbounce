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
package net.ccbluex.liquidbounce.features.module.modules.render.esp.runtime

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.entity.LivingEntity

internal object EspModeRuntime {
    private var maximumDistance: () -> Float = { 0f }
    private var color: (LivingEntity) -> Color4b = { Color4b.WHITE }
    private var renderedEntities: () -> Iterable<LivingEntity> = { emptyList() }
    private var taggedColorProvider: (LivingEntity) -> Color4b? = { null }
    private var shouldBeShownProvider: (LivingEntity) -> Boolean = { true }

    fun install(
        maximumDistance: () -> Float,
        color: (LivingEntity) -> Color4b,
        renderedEntities: () -> Iterable<LivingEntity>,
    ) {
        this.maximumDistance = maximumDistance
        this.color = color
        this.renderedEntities = renderedEntities
    }

    fun installCombatPresentation(
        taggedColor: (LivingEntity) -> Color4b?,
        shouldBeShown: (LivingEntity) -> Boolean,
    ) {
        taggedColorProvider = taggedColor
        shouldBeShownProvider = shouldBeShown
    }

    fun maximumDistance(): Float = maximumDistance()
    fun color(entity: LivingEntity): Color4b = color(entity)
    fun renderedEntities(): Iterable<LivingEntity> = renderedEntities()
    fun taggedColor(entity: LivingEntity): Color4b? = taggedColorProvider(entity)
    fun shouldBeShown(entity: LivingEntity): Boolean = shouldBeShownProvider(entity)
}
