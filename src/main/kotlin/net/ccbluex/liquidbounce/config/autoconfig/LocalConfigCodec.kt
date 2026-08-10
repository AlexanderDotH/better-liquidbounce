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
package net.ccbluex.liquidbounce.config.autoconfig

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.gson.util.parseTree
import net.ccbluex.liquidbounce.config.types.VALUE_NAME_ORDER
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.utils.client.mc
import java.io.Reader
import java.io.Writer

data class LocalConfigLoadSelection(
    val modules: Set<ClientModule> = emptySet(),
    val includeRender: Boolean = false,
)

data class LocalConfigLoadResult(
    val hasRenderSnapshot: Boolean,
)

private const val NAME = "name"
private const val VALUE = "value"
private const val AUTO_CONFIG_NAME = "autoconfig"
private const val MODULES = "modules"
private const val SPOOFERS = "spoofers"
private const val SPOOFER_CONFIG_NAME = "Spoofer"
private const val RENDER_MODULES = "renderModules"

internal data class LocalConfigLoadPlan(
    val standardConfig: JsonObject,
    val standardModules: List<ClientModule>,
    val renderSnapshot: JsonObject?,
    val renderModules: List<ClientModule>,
    val hasRenderSnapshot: Boolean,
) {
    fun apply(
        loadStandard: (JsonObject, List<ClientModule>) -> Unit,
        loadRender: (JsonObject, List<ClientModule>) -> Unit,
    ) {
        loadStandard(standardConfig, standardModules)

        if (renderSnapshot != null && renderModules.isNotEmpty()) {
            loadRender(renderSnapshot, renderModules)
        }
    }
}

/**
 * Adds local-only render state to the public AutoConfig document without changing the shared format.
 */
object LocalConfigCodec {

    fun serialize(
        writer: Writer,
        includeConfiguration: IncludeConfiguration = IncludeConfiguration.DEFAULT,
    ) {
        val jsonObject = createLocalConfigJson(AutoConfig.createAutoConfigJson(includeConfiguration))

        publicGson.newJsonWriter(writer).use {
            publicGson.toJson(jsonObject, it)
        }
    }

    fun load(
        reader: Reader,
        selection: LocalConfigLoadSelection = LocalConfigLoadSelection(),
    ): LocalConfigLoadResult {
        val jsonObject = LocalConfigValidator.parse(reader)
        val loadPlan = createLoadPlan(jsonObject, selection)

        AutoConfig.withLoading {
            loadPlan.apply(
                loadStandard = AutoConfig::loadAutoConfig,
                loadRender = AutoConfig::deserializeModuleValueGroup,
            )
        }

        if (selection.includeRender && LiquidBounce.isInitialized) {
            mc.execute(ModuleHud::reopen)
        }

        return LocalConfigLoadResult(hasRenderSnapshot = loadPlan.hasRenderSnapshot)
    }

    internal fun createLocalConfigJson(
        base: JsonObject,
        modules: Iterable<ClientModule> = ModuleManager,
    ): JsonObject = base.deepCopy().apply {
        add(RENDER_MODULES, serializeRenderModules(modules))
    }

    private fun serializeRenderModules(modules: Iterable<ClientModule>): JsonObject {
        val renderModules = modules.asSequence()
            .filter { module -> module.category == ModuleCategories.RENDER }
            .sortedWith(VALUE_NAME_ORDER)
            .toList()
        val serializedModules = JsonArray(renderModules.size)
        renderModules.forEach { module ->
            serializedModules.add(
                fileGson.toJsonTree(module, ValueGroup::class.javaObjectType).asJsonObject
            )
        }

        return JsonObject().apply {
            addProperty(NAME, MODULES)
            add(VALUE, serializedModules)
        }
    }

    internal fun createLoadPlan(
        jsonObject: JsonObject,
        selection: LocalConfigLoadSelection,
        modules: Iterable<ClientModule> = ModuleManager,
    ): LocalConfigLoadPlan {
        val availableModules = modules.sortedWith(VALUE_NAME_ORDER)
        val renderModules = availableModules.filter { module ->
            module.category == ModuleCategories.RENDER
        }
        val standardModules = modulesToLoad(selection, availableModules)
        val standardConfig = if (standardModules.isEmpty()) {
            jsonObject.withoutModuleValues()
        } else {
            jsonObject
        }
        val hasRenderSnapshot = jsonObject.has(RENDER_MODULES)
        val renderSnapshot = jsonObject.get(RENDER_MODULES)
            ?.takeIf { selection.includeRender }
            ?.asJsonObject

        return LocalConfigLoadPlan(
            standardConfig,
            standardModules,
            renderSnapshot,
            renderModules,
            hasRenderSnapshot,
        )
    }

    private fun modulesToLoad(
        selection: LocalConfigLoadSelection,
        modules: List<ClientModule>,
    ): List<ClientModule> {
        val selectedModules = selection.modules
        val loadAllNonRender = selectedModules.isEmpty()

        return modules.filter { module ->
            when {
                module.category == ModuleCategories.RENDER -> selection.includeRender
                loadAllNonRender -> true
                else -> module in selectedModules
            }
        }
    }

    private fun JsonObject.withoutModuleValues(): JsonObject = deepCopy().apply {
        when (getAsJsonPrimitive(NAME).asString) {
            AUTO_CONFIG_NAME -> remove(MODULES)
            ModuleManager.modulesConfig.name -> add(VALUE, JsonArray())
        }
    }

}

private object LocalConfigValidator {

    fun parse(reader: Reader): JsonObject = publicGson.newJsonReader(reader).use { jsonReader ->
        val jsonElement = jsonReader.parseTree()
        require(jsonElement.isJsonObject) { "Local config root must be a JSON object" }
        jsonElement.asJsonObject.also(::validate)
    }

    private fun validate(jsonObject: JsonObject) {
        when (val configName = jsonObject.requiredString(NAME, "root")) {
            AUTO_CONFIG_NAME -> {
                jsonObject.validateOptionalValueGroup(MODULES, MODULES)
                jsonObject.validateOptionalValueGroup(SPOOFERS, SPOOFER_CONFIG_NAME)
            }
            MODULES -> validateValueGroup(
                jsonObject,
                "root",
                MODULES,
            )
            else -> error("Unknown local config type: $configName")
        }

        jsonObject.validateOptionalValueGroup(RENDER_MODULES, MODULES)
    }

    private fun JsonObject.validateOptionalValueGroup(key: String, expectedName: String) {
        if (!has(key)) {
            return
        }

        val element = get(key)
        require(element.isJsonObject) { "Local config '$key' must be a JSON object" }
        validateValueGroup(element.asJsonObject, key, expectedName)
    }

    private fun validateValueGroup(jsonObject: JsonObject, path: String, expectedName: String) {
        val actualName = jsonObject.requiredString(NAME, path)
        require(actualName == expectedName) {
            "Local config '$path' has name '$actualName', expected '$expectedName'"
        }

        val values = jsonObject.get(VALUE)
        require(values != null && values.isJsonArray) {
            "Local config '$path.$VALUE' must be a JSON array"
        }

        values.asJsonArray.forEachIndexed { index, element ->
            validateValueEntry(element, "$path.$VALUE[$index]")
        }
    }

    private fun validateValueEntry(element: JsonElement, path: String) {
        require(element.isJsonObject) { "Local config '$path' must be a JSON object" }
        element.asJsonObject.requiredString(NAME, path)
    }

    private fun JsonObject.requiredString(key: String, path: String): String {
        val element = get(key)
        require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "Local config '$path.$key' must be a string"
        }
        return element.asString
    }

}
