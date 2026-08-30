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
package net.ccbluex.liquidbounce.render.playermodel

import net.minecraft.world.phys.Vec3
import java.lang.Math.fma
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class PlayerModelPositionInterpolationTest {

    @Test
    fun `first tick keeps the current position object unchanged`() {
        val current = Vec3(4.0, 5.0, 6.0)

        assertSame(current, interpolatePlayerModelPosition(null, current, 0.35f))
    }

    @Test
    fun `active entity interpolation keeps the established fused multiply add result`() {
        val previous = Vec3(-2.5, 7.25, 1.0)
        val current = Vec3(3.75, -4.0, 9.5)
        val partialTicks = 0.35f
        val delta = partialTicks.toDouble()

        assertEquals(
            Vec3(
                fma(delta, current.x - previous.x, previous.x),
                fma(delta, current.y - previous.y, previous.y),
                fma(delta, current.z - previous.z, previous.z),
            ),
            interpolatePlayerModelPosition(previous, current, partialTicks),
        )
    }

    @Test
    fun `nametag hook uses its render owned interpolation without the entity utility package`() {
        val source = Files.readString(Path.of(PLAYER_MODEL_NAMETAG_HOOK))

        assertFalse(source.contains("net.ccbluex.liquidbounce.utils.entity"))
        assertEquals(2, source.split(".interpolatePlayerModelPosition(partialTicks)").size - 1)
    }

    private companion object {
        const val PLAYER_MODEL_NAMETAG_HOOK =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/playermodel/PlayerModelNametagHook.kt"
    }
}
