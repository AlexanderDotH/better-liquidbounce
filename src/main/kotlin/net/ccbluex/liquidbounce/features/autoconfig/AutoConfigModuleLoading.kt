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

package net.ccbluex.liquidbounce.features.autoconfig

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.ConfigMigrationRegistry
import net.ccbluex.liquidbounce.config.ConfigSystem.deserializeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigModuleBridge

internal fun loadModuleValueGroups(json: JsonObject, modules: Collection<ValueGroup>) {
    prepareModuleConfigForLoad(json)
    if (modules.isEmpty()) {
        deserializeValueGroup(AutoConfigModuleBridge.modulesConfig, json)
        return
    }
    modules.forEach { module -> loadSelectedModule(json, module) }
}

internal fun prepareModuleConfigForLoad(json: JsonObject) {
    ConfigMigrationRegistry.applyAll(json)
}

private fun loadSelectedModule(json: JsonObject, module: ValueGroup) {
    val target = AutoConfigModuleBridge.modulesConfig.inner.find { it.name == module.name } as? ValueGroup ?: return
    val element = json["value"].asJsonArray.find { it.asJsonObject["name"].asString == module.name } ?: return
    deserializeValueGroup(target, element)
}
