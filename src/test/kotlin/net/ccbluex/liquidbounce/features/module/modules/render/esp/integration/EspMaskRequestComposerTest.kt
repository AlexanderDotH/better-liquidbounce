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
package net.ccbluex.liquidbounce.features.module.modules.render.esp.integration

import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.common.EspMaskRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EspMaskRequestComposerTest {

    @Test
    fun `entity effects retain chams glow outline priority`() {
        assertEquals(
            EspMaskLayer.ENTITY_CHAMS,
            selectEntityMaskLayer(
                chams = { true },
                glow = { error("glow evaluated") },
                outline = { error("outline evaluated") },
            ),
        )
        assertEquals(
            EspMaskLayer.PLAYER_GLOW,
            selectEntityMaskLayer(
                chams = { false },
                glow = { true },
                outline = { error("outline evaluated") },
            ),
        )
        assertEquals(
            EspMaskLayer.PLAYER_OUTLINE,
            selectEntityMaskLayer(chams = { false }, glow = { false }, outline = { true }),
        )
        assertNull(selectEntityMaskLayer(chams = { false }, glow = { false }, outline = { false }))
    }

    @Test
    fun `storage effects retain chams glow outline priority`() {
        assertEquals(
            EspMaskLayer.STORAGE_CHAMS,
            selectStorageMaskLayer(
                chams = { true },
                glow = { error("glow evaluated") },
                outline = { error("outline evaluated") },
            ),
        )
        assertEquals(
            EspMaskLayer.STORAGE_GLOW,
            selectStorageMaskLayer(
                chams = { false },
                glow = { true },
                outline = { error("outline evaluated") },
            ),
        )
        assertEquals(
            EspMaskLayer.STORAGE_OUTLINE,
            selectStorageMaskLayer(chams = { false }, glow = { false }, outline = { true }),
        )
        assertNull(selectStorageMaskLayer(chams = { false }, glow = { false }, outline = { false }))
    }

    @Test
    fun `protected feature masks retain the first owner color`() {
        val firstColor = 0x00224466
        val secondColor = 0x00112233

        val request = appendProtectedMask(
            appendProtectedMask(EspMaskRequest.NONE, EspMaskLayer.ITEM_GLOW, firstColor),
            EspMaskLayer.ITEM_GLOW,
            secondColor,
        )

        assertEquals(0xFFFFFFFF.toInt(), request.color(EspMaskLayer.PROTECTED_SURFACE))
        assertEquals(0xFF224466.toInt(), request.color(EspMaskLayer.ITEM_GLOW))
    }
}
