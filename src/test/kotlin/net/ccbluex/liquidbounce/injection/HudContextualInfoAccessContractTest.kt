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

import com.mojang.datafixers.util.Pair
import net.ccbluex.liquidbounce.injection.mixins.minecraft.gui.MixinHudAccessor
import net.ccbluex.liquidbounce.integration.theme.component.HudContextualInfoAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HudContextualInfoAccessContractTest {

    @Test
    fun `hud mixin preserves contextual info behind the integration contract`() {
        val accessor = MixinHudAccessor::class.java

        assertTrue(HudContextualInfoAccess::class.java.isAssignableFrom(accessor))
        assertEquals(Pair::class.java, accessor.getMethod("getContextualInfoBar").returnType)
    }
}
