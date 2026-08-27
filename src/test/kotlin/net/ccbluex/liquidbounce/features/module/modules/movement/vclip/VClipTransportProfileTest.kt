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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VClipTransportProfileTest {

    private val request = VClipTransportRequest(
        origin = VClipPosition(2.0, 64.0, -3.0),
        target = VClipPosition(2.0, 57.63, -3.0),
        fallSafety = VClipFallSafetyContext(
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
        ),
    )

    @Test
    fun `Vanilla profile exposes only reusable packet settings with module defaults`() {
        val profile = VClipVanillaProfile()

        assertFalse(profile.paperBypass)
        assertFalse(profile.fullPacket)
        assertFalse(profile.javaClass.declaredFields.any { it.name == "resetMotion" })
        assertEquals(
            VClipPacketPlanner.vanilla(
                origin = request.origin,
                target = request.target,
                paperBypass = false,
                fullPacket = false,
                initialFallDistance = request.fallSafety.initialFallDistance,
                safeFallDistance = request.fallSafety.safeFallDistance,
            ),
            profile.plan(request),
        )
    }

    @Test
    fun `Folia profile exposes only reusable packet settings with module defaults`() {
        val profile = VClipFoliaProfile()

        assertEquals(5, profile.movementPackets)
        assertFalse(profile.fullPacket)
        assertFalse(profile.javaClass.declaredFields.any { it.name == "resetMotion" })
        assertEquals(
            VClipPacketPlanner.folia(
                origin = request.origin,
                target = request.target,
                movementPackets = 5,
                fullPacket = false,
                initialFallDistance = request.fallSafety.initialFallDistance,
                safeFallDistance = request.fallSafety.safeFallDistance,
            ),
            profile.plan(request),
        )
    }

    @Test
    fun `Folia profile rejects packet counts outside its researched window`() {
        assertThrows(IllegalArgumentException::class.java) {
            VClipFoliaProfile(movementPackets = 6)
        }
    }
}
