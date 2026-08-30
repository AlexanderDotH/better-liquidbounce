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

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import net.ccbluex.liquidbounce.common.interop.ThemeColorPayload
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ColorAdapterTest {
    private val gson = GsonBuilder().registerTypeAdapter(Color4b::class.java, ColorAdapter).create()

    @Test
    fun `adapter preserves signed numeric color encoding`() {
        val color = Color4b(0x80112233.toInt())
        val payload: ThemeColorPayload = color

        assertSame(color, payload)
        assertEquals(color.argb.toString(), gson.toJson(payload))
        assertEquals(color, gson.fromJson(color.argb.toString(), Color4b::class.java))
    }

    @Test
    fun `adapter accepts unsigned numeric and hexadecimal color encodings`() {
        assertEquals(Color4b(0xFF112233.toInt()), gson.fromJson("4279312947", Color4b::class.java))
        assertEquals(Color4b.fromHex("#80112233"), gson.fromJson("\"#80112233\"", Color4b::class.java))
        assertEquals(Color4b.fromHex("FF0000"), gson.fromJson("\"FF0000\"", Color4b::class.java))
    }

    @Test
    fun `adapter preserves null`() {
        assertEquals("null", gson.toJson(null, Color4b::class.java))
        assertNull(gson.fromJson("null", Color4b::class.java))
    }

    @Test
    fun `adapter rejects non color tokens`() {
        assertThrows(JsonParseException::class.java) {
            gson.fromJson("true", Color4b::class.java)
        }
    }
}
