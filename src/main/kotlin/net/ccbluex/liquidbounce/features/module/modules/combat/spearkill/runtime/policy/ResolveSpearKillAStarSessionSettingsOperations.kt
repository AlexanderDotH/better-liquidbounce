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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAStarSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode

internal fun SpearKillModuleState.resolveSpearKillAStarSessionSettings(
    routingMode: SpearKillRoutingMode,
): SpearKillAStarSessionSettings = if (routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED) {
    movementConfiguration.packet.networkOptimized.let { network ->
        SpearKillAStarSessionSettings(
            maxCost = network.maxCost,
            diagonal = network.diagonal,
            lineOfSightShortcuts = network.lineOfSightShortcuts,
        )
    }
} else {
    movementConfiguration.packet.aStar.let { aStar ->
        SpearKillAStarSessionSettings(
            maxCost = aStar.maxCost,
            diagonal = aStar.diagonal,
            lineOfSightShortcuts = aStar.lineOfSightShortcuts,
        )
    }
}
