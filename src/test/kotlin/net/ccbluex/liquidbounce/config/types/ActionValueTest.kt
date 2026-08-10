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
package net.ccbluex.liquidbounce.config.types

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionValueTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `action runs only for an explicit true payload and never retains state`() {
        var invocations = 0
        val action = ValueGroup("Actions").action("Reset") { invocations++ }

        assertEquals(ValueType.ACTION, action.valueType)
        assertEquals(false, action.get())

        action.deserializeFrom(Gson(), JsonPrimitive(false))
        assertEquals(0, invocations)

        action.deserializeFrom(Gson(), JsonPrimitive(true))
        assertEquals(1, invocations)
        assertEquals(false, action.get())

        action.deserializeFrom(Gson(), JsonPrimitive(true))
        assertEquals(2, invocations)
        assertEquals(false, action.get())
    }

    @Test
    fun `action serializes as false while interop exposes its action type`() {
        val group = ValueGroup("Actions")
        val action = group.action("Reset") {}

        action.set(true)

        val fileValue = fileGson.toJsonTree(group)
            .asJsonObject["value"].asJsonArray.single().asJsonObject
        assertEquals(false, fileValue["value"].asBoolean)

        val interopValue = interopGson.toJsonTree(group)
            .asJsonObject["value"].asJsonArray.single().asJsonObject
        assertEquals("ACTION", interopValue["valueType"].asString)
        assertEquals(false, interopValue["value"].asBoolean)
    }
}
