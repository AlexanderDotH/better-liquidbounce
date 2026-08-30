/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.module.modules.render.playermodel

import net.minecraft.network.protocol.Packet

object ServerPlayerModelStateHook {
    @JvmStatic fun onPacketSent(packet: Packet<*>) = ServerPlayerModelStateTracker.onPacketSent(packet)
}
