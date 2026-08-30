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
package net.ccbluex.liquidbounce.features.module.modules.misc.antibot.contract

import com.mojang.authlib.GameProfile

internal interface AntiBotProfileHook {
    fun isDuplicate(profile: GameProfile): Boolean
    fun isUnique(profile: GameProfile): Boolean
}

internal object AntiBotProfileBridge : AntiBotProfileHook {
    private object NoProfiles : AntiBotProfileHook {
        override fun isDuplicate(profile: GameProfile) = false
        override fun isUnique(profile: GameProfile) = false
    }

    private var provider: AntiBotProfileHook = NoProfiles

    fun install(provider: AntiBotProfileHook) {
        this.provider = provider
    }

    override fun isDuplicate(profile: GameProfile) = provider.isDuplicate(profile)
    override fun isUnique(profile: GameProfile) = provider.isUnique(profile)

    internal fun <T> withProviderForTest(provider: AntiBotProfileHook, block: () -> T): T {
        val previous = this.provider
        this.provider = provider
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
