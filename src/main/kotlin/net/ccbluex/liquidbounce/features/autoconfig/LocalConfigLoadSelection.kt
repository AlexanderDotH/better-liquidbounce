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
@file:JvmName("LocalConfigCodecKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.autoconfig

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.OptionalInclusion
import net.ccbluex.liquidbounce.config.autoconfig.IncludeConfiguration
import net.ccbluex.liquidbounce.common.ClientLifecycleState
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.gson.util.parseTree
import net.ccbluex.liquidbounce.config.types.VALUE_NAME_ORDER
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigModuleBridge
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigUiBridge
import net.ccbluex.liquidbounce.utils.client.mc
import java.io.Reader
import java.io.Writer

data class LocalConfigLoadSelection(
    val modules: Set<ValueGroup> = emptySet(),
    val includeRender: Boolean = false,
)

data class LocalConfigLoadResult(
    val hasRenderSnapshot: Boolean,
)

internal const val NAME = "name"
internal const val VALUE = "value"
internal const val AUTO_CONFIG_NAME = "autoconfig"
internal const val MODULES = "modules"
internal const val SPOOFERS = "spoofers"
internal const val SPOOFER_CONFIG_NAME = "Spoofer"
internal const val RENDER_MODULES = "renderModules"

internal data class LocalConfigLoadPlan(
    val standardConfig: JsonObject,
    val standardModules: List<ValueGroup>,
    val renderSnapshot: JsonObject?,
    val renderModules: List<ValueGroup>,
    val hasRenderSnapshot: Boolean,
) {
    fun apply(
        loadStandard: (JsonObject, List<ValueGroup>) -> Unit,
        loadRender: (JsonObject, List<ValueGroup>) -> Unit,
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

        if (selection.includeRender && ClientLifecycleState.isInitialized) {
            mc.execute(AutoConfigUiBridge::reopenHud)
        }

        return LocalConfigLoadResult(hasRenderSnapshot = loadPlan.hasRenderSnapshot)
    }

    internal fun createLocalConfigJson(
        base: JsonObject,
        modules: Iterable<ValueGroup> = AutoConfigModuleBridge.modules,
    ): JsonObject = base.deepCopy().apply {
        add(RENDER_MODULES, serializeRenderModules(modules))
    }

    private fun serializeRenderModules(modules: Iterable<ValueGroup>): JsonObject {
        val renderModules = modules.asSequence()
            .filter { module -> module.isRenderModule() }
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
        modules: Iterable<ValueGroup> = AutoConfigModuleBridge.modules,
    ): LocalConfigLoadPlan {
        val availableModules = modules.sortedWith(VALUE_NAME_ORDER)
        val renderModules = availableModules.filter { module -> module.isRenderModule() }
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
        modules: List<ValueGroup>,
    ): List<ValueGroup> {
        val selectedModules = selection.modules
        val loadAllNonRender = selectedModules.isEmpty()

        return modules.filter { module ->
            when {
                module.isRenderModule() -> selection.includeRender
                loadAllNonRender -> true
                else -> module in selectedModules
            }
        }
    }

    private fun JsonObject.withoutModuleValues(): JsonObject = deepCopy().apply {
        when (getAsJsonPrimitive(NAME).asString) {
            AUTO_CONFIG_NAME -> remove(MODULES)
            AutoConfigModuleBridge.modulesConfig.name -> add(VALUE, JsonArray())
        }
    }

    private fun ValueGroup.isRenderModule() = inclusionGroup == OptionalInclusion.RENDER

}
