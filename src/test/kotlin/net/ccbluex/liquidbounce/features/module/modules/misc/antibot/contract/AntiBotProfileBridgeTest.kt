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
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntiBotProfileBridgeTest {

    @Test
    fun `profile checks preserve provider decisions`() {
        val duplicate = GameProfile(UUID.randomUUID(), "Duplicate")
        val unique = GameProfile(UUID.randomUUID(), "Unique")
        val provider = object : AntiBotProfileHook {
            override fun isDuplicate(profile: GameProfile) = profile === duplicate
            override fun isUnique(profile: GameProfile) = profile === unique
        }

        AntiBotProfileBridge.withProviderForTest(provider) {
            assertTrue(AntiBotProfileBridge.isDuplicate(duplicate))
            assertFalse(AntiBotProfileBridge.isUnique(duplicate))
            assertTrue(AntiBotProfileBridge.isUnique(unique))
        }
    }
}
