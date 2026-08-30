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
package net.ccbluex.liquidbounce.features.autoconfig

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.api.models.client.AutoSettings
import net.ccbluex.liquidbounce.api.services.client.ClientApi
import net.ccbluex.liquidbounce.api.types.enums.AutoSettingsStatusType
import net.ccbluex.liquidbounce.api.types.enums.AutoSettingsType
import net.ccbluex.liquidbounce.config.gson.util.obj
import net.ccbluex.liquidbounce.config.gson.util.string
import net.ccbluex.liquidbounce.config.ConfigSystem.deserializeValueGroup
import net.ccbluex.liquidbounce.config.autoconfig.AutoConfigContext
import net.ccbluex.liquidbounce.config.autoconfig.IncludeConfiguration
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.gson.util.parseTree
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigUiBridge
import net.ccbluex.liquidbounce.features.spoofer.SpooferManager
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style
import java.io.Reader
import java.io.Writer

object AutoConfig {

    var loadingNow: Boolean
        get() = AutoConfigContext.loadingNow
        set(value) {
            AutoConfigContext.loadingNow = value
        }

    var includeConfiguration: IncludeConfiguration
        get() = AutoConfigContext.includeConfiguration
        set(value) {
            AutoConfigContext.includeConfiguration = value
        }

    init {
        AutoConfigContext.onLoadingFinished(AutoConfigUiBridge::syncClickGui)
    }

    @Volatile
    var configs: Array<AutoSettings>? = null
        private set

    /**
     * Reloads auto settings list.
     *
     * @return successfully reloaded or not
     */
    suspend fun reloadConfigs(): Boolean = try {
        configs = ClientApi.requestSettingsList()
        true
    } catch (e: Exception) {
        logger.error("Failed to load auto configs", e)
        false
    }

    inline fun withLoading(block: () -> Unit) {
        loadingNow = true
        try {
            block()
        } finally {
            loadingNow = false
        }
    }

    suspend fun loadAutoConfig(autoConfig: AutoSettings) = withLoading {
        ClientApi.requestSettingsScript(autoConfig.settingId).use(::loadAutoConfig)
    }

    /**
     * Deserialize module configurable from a reader
     */
    fun loadAutoConfig(
        reader: Reader,
        modules: Collection<ValueGroup> = emptyList()
    ) {
        publicGson.newJsonReader(reader).use { reader ->
            loadAutoConfig(reader.parseTree().asJsonObject, modules)
        }
    }

    /**
     * Handles the data from a configurable, which might be an auto config and therefore has data which
     * should be displayed to the user.
     *
     * @param jsonObject The JSON object of the configurable
     * @see deserializeValueGroup
     */
    fun loadAutoConfig(
        jsonObject: JsonObject,
        modules: Collection<ValueGroup> = emptyList()
    ) {
        chat(metadata = MessageMetadata(prefix = false))
        chat("Auto Config".asPlainText(Style.EMPTY + ChatFormatting.LIGHT_PURPLE + ChatFormatting.BOLD))

        val name = jsonObject.string("name") ?: throw IllegalArgumentException("Auto Config has no name")
        when (name) {
            "autoconfig" -> {
                // Deserialize Module Configurable
                jsonObject.obj("modules")?.let { moduleObject ->
                    deserializeModuleValueGroup(moduleObject, modules)
                }

                // Deserialize Spoofer Configurable
                jsonObject.obj("spoofers")?.let { spooferObject ->
                    deserializeValueGroup(SpooferManager, spooferObject)
                }
            }
            "modules" -> deserializeModuleValueGroup(jsonObject, modules)
            else -> error("Unknown auto config type: $name")
        }

        // Auto Config
        printAutoConfigMetadata(jsonObject)
    }

    /**
     * Created an auto config, which stores the moduleConfigur
     */
    fun serializeAutoConfig(
        writer: Writer,
        includeConfiguration: IncludeConfiguration = IncludeConfiguration.DEFAULT,
        autoSettingsType: AutoSettingsType = AutoSettingsType.RAGE,
        statusType: AutoSettingsStatusType = AutoSettingsStatusType.BYPASSING
    ) = writeAutoConfig(writer, includeConfiguration, autoSettingsType, statusType)

    /**
     * Creates the JSON tree shared by public auto config serialization and local config extensions.
     */
    internal fun createAutoConfigJson(
        includeConfiguration: IncludeConfiguration = IncludeConfiguration.DEFAULT,
        autoSettingsType: AutoSettingsType = AutoSettingsType.RAGE,
        statusType: AutoSettingsStatusType = AutoSettingsStatusType.BYPASSING
    ): JsonObject = buildAutoConfigJson(includeConfiguration, autoSettingsType, statusType)

    /**
     * Deserialize module configurable from a JSON object
     */
    internal fun deserializeModuleValueGroup(
        jsonObject: JsonObject,
        modules: Collection<ValueGroup> = emptyList()
    ) = loadModuleValueGroups(jsonObject, modules)

}
