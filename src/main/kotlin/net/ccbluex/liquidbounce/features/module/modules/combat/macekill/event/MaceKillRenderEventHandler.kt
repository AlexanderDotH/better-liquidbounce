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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPathAppearance
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.renderSpearKillAStarPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.shouldRenderSpearKillAStarPath
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent

internal fun MaceKillModuleState.registerMaceKillRenderHandler() {
    handler<WorldRenderEvent> { event ->
        if (shouldRenderSpearKillAStarPath(
                previewEnabled = preview.enabled,
                packetAStarEnabled = routeRenderPath.isNotEmpty(),
                renderPathEnabled = preview.renderPath,
                renderPath = routeRenderPath,
            )
        ) {
            renderSpearKillAStarPath(
                event,
                routeRenderPath,
                SpearKillAStarPathAppearance(
                    preview.glow.glowColor,
                    preview.glow.glowStyle.style,
                ),
            )
        }
    }

}
