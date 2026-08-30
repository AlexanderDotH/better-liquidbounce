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
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.Entity

internal sealed class TargetAppearance<Context : Any>(name: String) : Mode(name) {
    abstract fun Context.render(entity: Entity, partialTicks: Float)
}

internal sealed class WorldTargetAppearance(name: String) :
    TargetAppearance<WorldRenderEnvironment>(name)

internal sealed class GuiTargetAppearance(name: String) :
    TargetAppearance<GuiGraphicsExtractor>(name)
