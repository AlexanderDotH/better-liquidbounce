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
import net.ccbluex.liquidbounce.api.types.enums.AutoSettingsStatusType
import net.ccbluex.liquidbounce.api.types.enums.AutoSettingsType
import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.autoconfig.IncludeConfiguration
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigModuleBridge
import net.ccbluex.liquidbounce.features.spoofer.SpooferManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.protocolVersion
import net.ccbluex.liquidbounce.utils.text.dropPort
import net.ccbluex.liquidbounce.utils.text.rootDomain
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date

internal fun writeAutoConfig(
    writer: Writer,
    includeConfiguration: IncludeConfiguration,
    type: AutoSettingsType,
    status: AutoSettingsStatusType,
) {
    val json = buildAutoConfigJson(includeConfiguration, type, status)
    publicGson.newJsonWriter(writer).use { publicGson.toJson(json, it) }
}

internal fun buildAutoConfigJson(
    includeConfiguration: IncludeConfiguration,
    type: AutoSettingsType,
    status: AutoSettingsStatusType,
): JsonObject = withAutoConfigInclusion(includeConfiguration) {
    val modules = ConfigSystem.serializeValueGroup(AutoConfigModuleBridge.modulesConfig, publicGson)
    val spoofers = ConfigSystem.serializeValueGroup(SpooferManager, publicGson)
    check(modules.isJsonObject && spoofers.isJsonObject) { "Root element is not a json object" }
    JsonObject().apply {
        addProperty("name", "autoconfig")
        add("modules", modules.asJsonObject)
        add("spoofers", spoofers.asJsonObject)
        addAutoConfigMetadata()
        add("type", publicGson.toJsonTree(type))
        add("status", publicGson.toJsonTree(status))
    }
}

private fun JsonObject.addAutoConfigMetadata() {
    val now = Date()
    val (protocolName, protocolNumber) = protocolVersion
    addProperty("author", mc.user.name)
    addProperty("date", SimpleDateFormat("dd/MM/yyyy").format(now))
    addProperty("time", SimpleDateFormat("HH:mm:ss").format(now))
    addProperty("clientVersion", ClientBuildMetadata.version)
    addProperty("clientCommit", ClientBuildMetadata.commit)
    mc.currentServer?.let { addProperty("serverAddress", it.ip.dropPort().rootDomain()) }
    addProperty("protocolName", protocolName)
    addProperty("protocolVersion", protocolNumber)
}

internal fun <T> withAutoConfigInclusion(configuration: IncludeConfiguration, block: () -> T): T {
    AutoConfig.includeConfiguration = configuration
    return try {
        block()
    } finally {
        AutoConfig.includeConfiguration = IncludeConfiguration.DEFAULT
    }
}
