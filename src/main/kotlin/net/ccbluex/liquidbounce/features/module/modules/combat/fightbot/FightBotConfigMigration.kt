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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Moves the former KillAura child group and applies safe defaults before value deserialization. */
internal fun migrateLegacyFightBotConfig(root: JsonObject) {
    val modules = root.getAsJsonArray("value") ?: return
    var fightBot = modules.findNamedObject("FightBot")
    val killAuraValues = modules.findNamedObject("KillAura")?.getAsJsonArray("value")
    val legacyFightBot = killAuraValues?.findNamedObject("FightBot")

    if (legacyFightBot != null) {
        killAuraValues.remove(legacyFightBot)
        if (fightBot == null) {
            migrateLegacyFightBotTarget(legacyFightBot)
            modules.add(legacyFightBot)
            fightBot = legacyFightBot
        }
    }

    fightBot?.addMissingMaceAutomationDefault()
}

private fun JsonObject.addMissingMaceAutomationDefault() {
    val values = getAsJsonArray("value") ?: return
    if (values.findNamedObject("MaceAutomation") != null) return
    values.add(configValue("MaceAutomation", FightBotMaceAutomation.Off.tag))
}

private fun migrateLegacyFightBotTarget(fightBot: JsonObject) {
    val values = fightBot.getAsJsonArray("value") ?: return
    if (values.findNamedObject("Target") != null) return

    val targetFilter = values.findNamedObject("TargetFilter")
    val sparringOpponent = values.findNamedObject("SparringOpponent")
    targetFilter?.let(values::remove)
    sparringOpponent?.let(values::remove)

    val sparringValues = sparringOpponent?.getAsJsonArray("value")
    val sparringEnabled = sparringValues?.findNamedObject("Enabled")?.get("value")?.asBoolean == true
    val sparringName = sparringValues?.findNamedObject("Username")?.get("value")?.asString.orEmpty().trim()
    val namedMode = sparringEnabled && sparringName.isNotEmpty()

    val targetValues = JsonArray().apply {
        add(configValue("Mode", if (namedMode) FightBotTargetMode.Named.tag else FightBotTargetMode.Nearest.tag))
        add(configValue("Name", sparringName))
        targetFilter?.getAsJsonArray("value")?.forEach { add(it.deepCopy()) }
    }
    values.add(JsonObject().apply {
        addProperty("name", "Target")
        add("value", targetValues)
    })
}

private fun configValue(name: String, value: String) = JsonObject().apply {
    addProperty("name", name)
    addProperty("value", value)
}

private fun JsonArray.findNamedObject(name: String): JsonObject? = asSequence()
    .mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }
    .firstOrNull { it.get("name")?.asString == name }
