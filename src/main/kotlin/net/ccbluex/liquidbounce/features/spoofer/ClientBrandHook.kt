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

package net.ccbluex.liquidbounce.features.spoofer

object ClientBrandHook {
    @JvmStatic
    fun clientBrand(original: String) = SpooferClient.clientBrand(original)

    @JvmStatic
    fun handshakeAddress(original: String) =
        if (SpooferBungeeCord.running) SpooferBungeeCord.modifyHandshakeAddress(original) else original

    @JvmStatic fun isFingerprintSpoofing() = SpooferFingerprint.running
}
