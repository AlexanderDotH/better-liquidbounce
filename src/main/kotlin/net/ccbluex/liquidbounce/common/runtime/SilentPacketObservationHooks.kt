/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package net.ccbluex.liquidbounce.common.runtime

import net.minecraft.network.protocol.Packet

fun interface SilentPacketObserver {
    fun observe(packet: Packet<*>)
}

object SilentPacketObservationHooks {
    private val DISABLED = SilentPacketObserver { }

    @Volatile
    private var observer: SilentPacketObserver = DISABLED

    @JvmStatic
    @Synchronized
    fun install(observer: SilentPacketObserver) {
        check(this.observer === DISABLED) { "Silent packet observer is already installed" }
        this.observer = observer
    }

    fun observe(packet: Packet<*>) {
        observer.observe(packet)
    }
}
