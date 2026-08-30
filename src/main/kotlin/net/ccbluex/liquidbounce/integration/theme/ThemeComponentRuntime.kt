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

package net.ccbluex.liquidbounce.integration.theme

import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.group.json
import net.ccbluex.liquidbounce.integration.theme.component.HudComponent
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentFactory
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.text.capitalize

internal class ThemeComponentRuntime(
    private val loadFactory: suspend (String) -> HudComponentFactory,
    private val onColorChanged: (String, String, Color4b) -> Unit,
    private val unregisterComponent: (HudComponent) -> Unit,
    private val warn: (String, Throwable) -> Unit,
) {

    private var factories: Map<String, HudComponentFactory>? = null
    private var componentSettings: ValueGroup? = null

    private var loadedSettings: ValueGroup? = null
    val settings: ValueGroup
        get() = requireNotNull(loadedSettings) { "settings not loaded" }

    private var loadedColors: ValueGroup? = null
    val colors: ValueGroup
        get() = requireNotNull(loadedColors) { "colors not loaded" }

    val components: List<HudComponent>
        get() = requireNotNull(componentSettings) { "components not loaded" }
            .inner.filterIsInstance<HudComponent>()

    suspend fun load(metadata: ThemeMetadata) {
        factories = loadFactories(metadata.components)
        val initialComponents = requireNotNull(factories).values.mapNotNull(::createComponent)
        buildSettings(metadata, initialComponents)
    }

    fun addComponent(sourceId: String): HudComponent? {
        val source = components.find { it.id.toString() == sourceId } ?: return null
        val factory = requireNotNull(factories)[source.name] ?: return null

        if (factory.singleton && components.any { it.name == source.name && it.enabled }) {
            return null
        }

        val disabledComponent = components.find { it.name == source.name && !it.enabled }
        val component = disabledComponent
            ?: createComponent(factory)?.also(::registerComponent)
            ?: return null

        if (!factory.singleton && disabledComponent != null) {
            component.restore()
        }

        component.enabled = true
        return component
    }

    fun componentCatalog(): List<Theme.ComponentCatalogEntry> = requireNotNull(factories).values
        .mapNotNull { factory ->
            val source = components.firstOrNull { it.name == factory.name } ?: return@mapNotNull null
            Theme.ComponentCatalogEntry(
                source.name,
                source.componentDescription,
                source.id.toString(),
                factory.singleton,
                !factory.singleton || components.none { it.name == factory.name && it.enabled },
            )
        }

    fun close() {
        componentSettings?.inner
            ?.filterIsInstance<HudComponent>()
            ?.forEach(unregisterComponent)
    }

    private suspend fun loadFactories(names: List<String>): Map<String, HudComponentFactory> {
        val loadedFactories = names.mapNotNull { name ->
            runCatching { loadFactory(name) }
                .onFailure { warn("Failed to load component $name", it) }
                .getOrNull()
        }

        return buildMap {
            for (factory in loadedFactories) {
                check(put(factory.name, factory) == null) {
                    "Found duplicated component name '${factory.name}'"
                }
            }
        }
    }

    private fun buildSettings(metadata: ThemeMetadata, initialComponents: List<HudComponent>) {
        val colors = ValueGroup("Colors")
        metadata.colors?.forEach { (name, value) ->
            colors.color(name, Color4b.fromHex(value)).onChanged { color ->
                onColorChanged(metadata.id, name, color)
            }
        }

        val components = ComponentSettings().also { settings ->
            initialComponents.forEach(settings::tree)
        }

        loadedColors = colors
        componentSettings = components
        loadedSettings = ValueGroup(metadata.id.capitalize()).apply {
            tree(colors)
            metadata.values?.forEach { value -> json(value) }
            tree(components)
        }
    }

    private fun createComponent(factory: HudComponentFactory): HudComponent? = runCatching {
        factory.createComponent()
    }.onFailure {
        warn("Failed to create component ${factory.name}", it)
    }.getOrNull()

    private fun registerComponent(component: HudComponent) {
        val settings = requireNotNull(componentSettings)
        settings.tree(component)
        component.walkInit()
        settings.key?.let(component::walkKeyPath)
    }

    private inner class ComponentSettings : ValueGroup("Components") {
        override fun prepareDeserialize(jsonObject: JsonObject) {
            val existingCounts = Object2IntOpenHashMap<String>()
            components.forEach { existingCounts.addTo(it.name, 1) }

            for (storedComponent in jsonObject.getAsJsonArray("value")) {
                val name = storedComponent.asJsonObject["name"].asString
                val remaining = existingCounts.getOrDefault(name, 0)
                if (remaining > 0) {
                    existingCounts.put(name, remaining - 1)
                    continue
                }

                val factory = requireNotNull(factories)[name] ?: continue
                if (factory.singleton) {
                    continue
                }
                createComponent(factory)?.let(::registerComponent)
            }
        }
    }
}
