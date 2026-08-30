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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.exploit.disabler.disablers.RateLimitedPacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.PacketRateLimitDisposition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ReachInteractablePacketRateLimitBoundaryTest {

    @Test
    fun `runtime depends on the packet rate limit port instead of the disabler implementation`() {
        Files.walk(RUNTIME_SOURCE_ROOT).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { source ->
                    assertFalse(
                        Files.readString(source).contains(DISABLER_IMPLEMENTATION_PACKAGE),
                        "$source must obtain packet dispositions through PacketRateLimitDispositionPort",
                    )
                }
        }
    }

    @Test
    fun `adapter preserves queued and dropped packet dispositions`() {
        assertEquals(
            PacketRateLimitDisposition.QUEUED,
            RateLimitedPacketDisposition.QUEUED.toReachPacketDisposition(),
        )
        assertEquals(
            PacketRateLimitDisposition.DROPPED,
            RateLimitedPacketDisposition.DROPPED.toReachPacketDisposition(),
        )
    }

    private companion object {
        val RUNTIME_SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/reach/" +
                "interactable/runtime",
        )
        const val DISABLER_IMPLEMENTATION_PACKAGE =
            "net.ccbluex.liquidbounce.features.module.modules.exploit.disabler.disablers"
    }
}
