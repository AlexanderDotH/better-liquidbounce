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
import org.junit.jupiter.api.Test

class VClipFallProtectionPolicyTest {

    @Test
    fun `disabled NoFall preserves the configured packet ground state`() {
        assertEquals(
            VClipFallProtection(
                packetOnGround = false,
                forceTargetPacket = false,
                resetLocalFallDistance = false,
            ),
            VClipFallProtectionPolicy.resolve(noFallRunning = false, configuredOnGround = false),
        )
    }

    @Test
    fun `running NoFall grounds the target packet and resets local fall distance`() {
        assertEquals(
            VClipFallProtection(
                packetOnGround = true,
                forceTargetPacket = true,
                resetLocalFallDistance = true,
            ),
            VClipFallProtectionPolicy.resolve(noFallRunning = true, configuredOnGround = false),
        )
    }
}
