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
package net.ccbluex.liquidbounce.event.events

import net.ccbluex.liquidbounce.common.interop.HudComponentPayload
import net.ccbluex.liquidbounce.config.gson.accessibleInteropGson
import net.ccbluex.liquidbounce.integration.theme.component.HudComponent
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

class ComponentsUpdateEventContractTest {

    @Test
    fun `event keeps the neutral payload instance and established native wire data`() {
        MinecraftBootstrap.ensureInitialized()
        val component = object : HudComponent("Fixture", true) {}
        val event = ComponentsUpdateEvent(
            source = ComponentsUpdateEvent.Source.NATIVE,
            components = listOf(component),
        )

        assertSame(component, event.components.single())

        val serialized = event.serializer
            .toJsonTree(event, ComponentsUpdateEvent::class.java)
            .asJsonObject
        assertEquals("native", serialized["source"].asString)
        assertEquals(
            accessibleInteropGson.toJsonTree(component),
            serialized.getAsJsonArray("components").single(),
        )
    }

    @Test
    fun `event field exposes only the neutral payload contract`() {
        val componentType = ComponentsUpdateEvent::class.java
            .getDeclaredField("components")
            .genericType
            as ParameterizedType
        val elementType = componentType.actualTypeArguments.single()
        val elementClass = when (elementType) {
            is Class<*> -> elementType
            is WildcardType -> elementType.upperBounds.single()
            else -> error("Unexpected components element type: $elementType")
        }

        assertSame(HudComponentPayload::class.java, elementClass)
    }
}
