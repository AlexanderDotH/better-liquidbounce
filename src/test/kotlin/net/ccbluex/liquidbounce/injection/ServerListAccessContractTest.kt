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
package net.ccbluex.liquidbounce.injection

import net.ccbluex.liquidbounce.injection.mixins.minecraft.client.option.MixinServerListAccessor
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ServerListAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerListAccessContractTest {

    @Test
    fun `server list mixin preserves its accessor behind the integration contract`() {
        val accessor = MixinServerListAccessor::class.java

        assertTrue(ServerListAccess::class.java.isAssignableFrom(accessor))
        assertEquals(List::class.java, accessor.getMethod("liquid_bounce\$getServerList").returnType)
    }
}
