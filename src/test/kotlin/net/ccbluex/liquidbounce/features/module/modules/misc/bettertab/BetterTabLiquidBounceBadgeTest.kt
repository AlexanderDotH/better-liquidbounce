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
package net.ccbluex.liquidbounce.features.module.modules.misc.bettertab

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BetterTabLiquidBounceBadgeTest {

    @Test
    fun `visible LiquidBounce player gets the configured badge`() {
        val color = Color4b(70, 119, 255)

        val badge = BetterTabLiquidBounceBadge.create(visible = true, color = color)!!

        assertEquals(" [LB]", badge.string)
        assertEquals(color.toTextColor(), badge.style.color)
    }

    @Test
    fun `hidden LiquidBounce player has no badge`() {
        assertNull(BetterTabLiquidBounceBadge.create(visible = false, color = Color4b.LIQUID_BOUNCE))
    }
}
