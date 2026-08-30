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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillPrimedInstantPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchPacketType

internal fun SpearKillHighSpeedResearchPacketType.toPrimedPacketType() = when (this) {
    SpearKillHighSpeedResearchPacketType.POSITION -> SpearKillPrimedInstantPacketType.Position
    SpearKillHighSpeedResearchPacketType.POSITION_ROTATION ->
        SpearKillPrimedInstantPacketType.PositionRotation
    SpearKillHighSpeedResearchPacketType.ROTATION -> SpearKillPrimedInstantPacketType.Rotation
    SpearKillHighSpeedResearchPacketType.STATUS_ONLY -> SpearKillPrimedInstantPacketType.StatusOnly
}
