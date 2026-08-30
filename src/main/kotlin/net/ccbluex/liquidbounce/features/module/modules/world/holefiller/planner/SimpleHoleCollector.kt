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
package net.ccbluex.liquidbounce.features.module.modules.world.holefiller.planner

import net.ccbluex.liquidbounce.features.module.modules.world.holefiller.model.HoleFillerPlanContext

internal object SimpleHoleCollector {
    @Suppress("ComplexCondition")
    fun collect(context: HoleFillerPlanContext, preventSelfFill: Boolean, playerY: Double) {
        context.holes.forEach { hole ->
            val y = hole.pos.y + 1.0
            if (!preventSelfFill || y > playerY || context.selfInHole || !hole.positions.intersects(context.selfRegion)) {
                hole.asList().toCollection(context.blocks)
            }
        }
    }
}
