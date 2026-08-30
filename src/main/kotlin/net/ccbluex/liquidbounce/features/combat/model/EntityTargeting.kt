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
 */
package net.ccbluex.liquidbounce.features.combat.model

import net.ccbluex.liquidbounce.common.Tagged

typealias EntityTargetingInfo = net.ccbluex.liquidbounce.common.entity.EntityTargetingInfo
typealias EntityTargetClassification = net.ccbluex.liquidbounce.common.entity.EntityTargetClassification

/** User-configurable entity categories shared by combat and visual target consumers. */
enum class Targets(override val tag: String) : Tagged {
    SELF("Self"),
    PLAYERS("Players"),
    TRIAL("Trial"),
    HOSTILE("Hostile"),
    ANGERABLE("Angerable"),
    WATER_CREATURE("WaterCreature"),
    PASSIVE("Passive"),
    INVISIBLE("Invisible"),
    DEAD("Dead"),
    SLEEPING("Sleeping"),
    FRIENDS("Friends"),
}
