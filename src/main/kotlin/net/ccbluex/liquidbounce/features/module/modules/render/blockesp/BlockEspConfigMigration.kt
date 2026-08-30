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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.render.blockesp

import com.google.gson.JsonObject

internal fun migrateLegacyNetherPortalTarget(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val valuesByName = storedValues.associateBy { it.asJsonObject["name"].asString }
    val legacyToggle = valuesByName[LEGACY_NETHER_PORTALS_SETTING]?.asJsonObject ?: return
    if (!legacyToggle["value"].asBoolean) return

    val storedTargets = valuesByName["Targets"]?.asJsonObject?.get("value")
        ?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    if (storedTargets.none { it.asString == NETHER_PORTAL_ID }) storedTargets.add(NETHER_PORTAL_ID)
}

private const val LEGACY_NETHER_PORTALS_SETTING = "NetherPortals"
private const val NETHER_PORTAL_ID = "minecraft:nether_portal"
