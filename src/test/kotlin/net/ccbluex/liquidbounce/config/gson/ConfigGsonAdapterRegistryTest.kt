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
package net.ccbluex.liquidbounce.config.gson

import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConfigGsonAdapterRegistryTest {

    @Test
    fun `installed adapters are applied to every builder before sealing`() {
        val registry = MutableConfigGsonAdapterRegistry()
        registry.install {
            registerTypeAdapter(Marker::class.java, JsonSerializer<Marker> { source, _, _ ->
                JsonPrimitive(source.value)
            })
        }

        val first = registry.applyTo(com.google.gson.GsonBuilder()).create()
        val second = registry.applyTo(com.google.gson.GsonBuilder()).create()

        assertEquals("\"registered\"", first.toJson(Marker("registered")))
        assertEquals("\"registered\"", second.toJson(Marker("registered")))
    }

    @Test
    fun `installing after the first builder is rejected`() {
        val registry = MutableConfigGsonAdapterRegistry()
        registry.applyTo(com.google.gson.GsonBuilder())

        assertThrows(IllegalStateException::class.java) {
            registry.install { setPrettyPrinting() }
        }
    }

    @Test
    fun `accessible adapters are applied only to the requested builder scope`() {
        val registry = MutableConfigGsonAdapterRegistry()
        registry.install(ConfigGsonAdapterScope.ACCESSIBLE_INTEROP) {
            registerTypeAdapter(Detail::class.java, JsonSerializer<Detail> { source, _, _ ->
                JsonPrimitive(source.value)
            })
        }

        val common = registry.applyTo(com.google.gson.GsonBuilder()).create()
        val accessible = registry.applyTo(
            com.google.gson.GsonBuilder(),
            ConfigGsonAdapterScope.ACCESSIBLE_INTEROP,
        ).create()

        assertEquals("{\"value\":\"scoped\"}", common.toJson(Detail("scoped")))
        assertEquals("\"scoped\"", accessible.toJson(Detail("scoped")))
    }

    private data class Marker(val value: String)
    private data class Detail(val value: String)
}
