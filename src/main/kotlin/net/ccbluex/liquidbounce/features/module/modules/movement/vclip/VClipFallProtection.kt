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

internal data class VClipFallProtection(
    val packetOnGround: Boolean,
    val forceTargetPacket: Boolean,
    val resetLocalFallDistance: Boolean,
)

internal object VClipFallProtectionPolicy {

    fun resolve(noFallRunning: Boolean, configuredOnGround: Boolean) = if (noFallRunning) {
        VClipFallProtection(
            packetOnGround = true,
            forceTargetPacket = true,
            resetLocalFallDistance = true,
        )
    } else {
        VClipFallProtection(
            packetOnGround = configuredOnGround,
            forceTargetPacket = false,
            resetLocalFallDistance = false,
        )
    }
}
