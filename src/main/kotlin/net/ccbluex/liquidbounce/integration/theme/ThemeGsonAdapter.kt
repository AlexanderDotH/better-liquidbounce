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
package net.ccbluex.liquidbounce.integration.theme

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.ccbluex.liquidbounce.config.gson.ConfigGsonAdapterRegistry
import net.ccbluex.liquidbounce.config.gson.ConfigGsonAdapterScope
import net.ccbluex.liquidbounce.config.gson.serializer.ValueGroupSerializer.Companion.serializeReadOnly
import net.ccbluex.liquidbounce.integration.theme.component.HudComponent
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import java.lang.reflect.Type

object ThemeGsonAdapter {
    private var installed = false

    @Synchronized
    fun install() {
        check(!installed) { "Theme Gson adapter is already installed" }
        ConfigGsonAdapterRegistry.install(ConfigGsonAdapterScope.ACCESSIBLE_INTEROP) {
            registerTypeHierarchyAdapter(Theme::class.javaObjectType, ReadOnlyThemeSerializer)
            registerTypeHierarchyAdapter(HudComponent::class.javaObjectType, ReadOnlyComponentSerializer)
        }
        installed = true
    }
}

internal object ReadOnlyThemeSerializer : JsonSerializer<Theme> {
    override fun serialize(source: Theme, type: Type, context: JsonSerializationContext) = JsonObject().apply {
        addProperty("name", source.metadata.name)
        addProperty("id", source.metadata.id)
        add("colors", serializeReadOnly(source.colors, context))
        add("settings", serializeReadOnly(source.settings, context))
    }
}

internal object ReadOnlyComponentSerializer : JsonSerializer<HudComponent> {
    override fun serialize(source: HudComponent, type: Type, context: JsonSerializationContext) =
        JsonObject().apply {
            addProperty("name", source.name)
            addProperty("description", source.componentDescription)
            addProperty("id", source.id.toString())
            add("settings", serializeReadOnly(source, context))
            if (source is NativeHudComponent) {
                addProperty("width", source.width)
                addProperty("height", source.height)
            }
        }
}
