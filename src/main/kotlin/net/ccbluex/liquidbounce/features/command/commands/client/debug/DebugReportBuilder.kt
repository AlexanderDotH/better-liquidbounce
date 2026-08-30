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
package net.ccbluex.liquidbounce.features.command.commands.client.debug

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.features.account.accountType
import net.ccbluex.liquidbounce.features.global.GlobalSettingsTarget
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.language.LanguageManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.minecraft.SharedConstants
import java.util.EnumSet

internal fun createDebugReport(autoConfigPaste: String) = JsonObject().apply {
    add("client", clientDebugJson())
    add("minecraft", minecraftDebugJson())
    add("java", javaDebugJson())
    add("os", operatingSystemDebugJson())
    add("user", userDebugJson())
    add("profile", profileDebugJson())
    add("language", languageDebugJson())
    add("server", serverDebugJson())
    addProperty("config", autoConfigPaste)
    add("activeModules", activeModulesDebugJson())
    add("scripts", scriptsDebugJson())
    add("enemies", publicGson.toJsonTree(GlobalSettingsTarget.combat, EnumSet::class.javaObjectType))
}

internal fun clientDebugJson() = JsonObject().apply {
    addProperty("name", ClientBuildMetadata.NAME)
    addProperty("version", ClientBuildMetadata.version)
    addProperty("commit", ClientBuildMetadata.commit)
    addProperty("branch", ClientBuildMetadata.branch)
    addProperty("development", ClientBuildMetadata.IN_DEVELOPMENT)
    addProperty("usesViaFabricPlus", usesViaFabricPlus)
}

internal fun scriptsDebugJson() = JsonArray().apply {
    DebugScriptInventoryBridge.scripts().forEach { script ->
        add(JsonObject().apply {
            addProperty("name", script.name)
            addProperty("version", script.version)
            addProperty("author", script.authors)
            addProperty("path", script.path)
        })
    }
}

private fun minecraftDebugJson() = JsonObject().apply {
    addProperty("version", SharedConstants.getCurrentVersion().name())
    addProperty("protocol", SharedConstants.getProtocolVersion())
}

private fun javaDebugJson() = JsonObject().apply {
    addProperty("version", System.getProperty("java.version"))
    addProperty("vendor", System.getProperty("java.vendor"))
}

private fun operatingSystemDebugJson() = JsonObject().apply {
    addProperty("name", System.getProperty("os.name"))
    addProperty("version", System.getProperty("os.version"))
    addProperty("architecture", System.getProperty("os.arch"))
}

private fun userDebugJson() = JsonObject().apply {
    addProperty("language", System.getProperty("user.language"))
    addProperty("country", System.getProperty("user.country"))
    addProperty("timezone", System.getProperty("user.timezone"))
}

private fun profileDebugJson() = JsonObject().apply {
    addProperty("name", mc.user.name)
    addProperty("uuid", mc.user.profileId.toString())
    addProperty("type", mc.user.accountType)
}

private fun languageDebugJson() = JsonObject().apply {
    addProperty("language", mc.languageManager.selected)
    addProperty("clientLanguage", LanguageManager.clientLanguage.tag)
}

private fun serverDebugJson() = JsonObject().apply {
    mc.currentServer?.let { server ->
        addProperty("name", server.name)
        addProperty("address", server.ip)
        addProperty("protocol", server.protocol)
    }
}

private fun activeModulesDebugJson() = JsonArray().apply {
    ModuleManager.filter { module -> module.running }.forEach { module ->
        add(JsonPrimitive(module.name))
    }
}
