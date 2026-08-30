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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed


internal object SpearKillHighSpeedResearchBurstPolicy {

    fun admitsTarget(pendingOutbound: Boolean, sameWorld: Boolean): Boolean = pendingOutbound && sameWorld

    fun terminalRaytraceClear(
        pendingFinalOutbound: Boolean,
        targetAvailable: Boolean,
        visibleAttackRay: Boolean,
    ): Boolean = !pendingFinalOutbound || !targetAvailable || visibleAttackRay

    fun packetCountReset(finalPacketOrdinal: Int, maximum: Int): Boolean = finalPacketOrdinal > maximum
}
