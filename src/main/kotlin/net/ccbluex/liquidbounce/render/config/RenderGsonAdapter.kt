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
package net.ccbluex.liquidbounce.render.config

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import net.ccbluex.liquidbounce.common.interop.PackedThemeColor
import net.ccbluex.liquidbounce.config.gson.ConfigGsonAdapterRegistry
import net.ccbluex.liquidbounce.render.engine.type.Color4b

object RenderGsonAdapter {
    private var installed = false

    @Synchronized
    fun install() {
        check(!installed) { "Render Gson adapter is already installed" }
        ConfigGsonAdapterRegistry.install {
            registerTypeHierarchyAdapter(Color4b::class.javaObjectType, ColorAdapter)
            registerTypeAdapter(PackedThemeColor::class.javaObjectType, PackedThemeColorAdapter)
        }
        installed = true
    }
}

internal object PackedThemeColorAdapter : TypeAdapter<PackedThemeColor>() {
    override fun write(writer: JsonWriter, value: PackedThemeColor?) {
        if (value == null) writer.nullValue() else writer.value(value.argb)
    }

    override fun read(reader: JsonReader): PackedThemeColor? = when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        JsonToken.NUMBER -> PackedThemeColor(reader.nextLong().toInt())
        else -> throw JsonParseException("Only a number can be parsed as a packed theme color")
    }
}

internal object ColorAdapter : TypeAdapter<Color4b>() {
    override fun write(writer: JsonWriter, value: Color4b?) {
        if (value == null) writer.nullValue() else writer.value(value.argb)
    }

    override fun read(reader: JsonReader): Color4b? = when (val token = reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        JsonToken.NUMBER -> Color4b(reader.nextLong().toInt())
        JsonToken.STRING -> Color4b.fromHex(reader.nextString())
        else -> {
            reader.skipValue()
            throw JsonParseException("Only number or hex format string can be parsed as color, found $token")
        }
    }
}
