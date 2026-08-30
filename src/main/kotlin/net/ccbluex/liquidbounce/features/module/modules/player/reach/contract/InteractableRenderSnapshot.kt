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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.contract

import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteRenderSnapshot
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSearchSnapshot

internal sealed interface InteractableRenderSnapshot {
    data class Planning(val search: InteractableRouteSearchSnapshot) : InteractableRenderSnapshot
    data class Route(val route: InteractableRouteRenderSnapshot) : InteractableRenderSnapshot
}
