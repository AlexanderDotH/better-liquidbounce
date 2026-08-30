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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.Appearance
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class ModuleAmnesiaContractTest {

    @Test
    fun `Amnesia keeps its persisted setting order`() {
        assertEquals(
            listOf(
                "Enabled",
                "Bind",
                "Hidden",
                "Target",
                "Appearance",
                "DelayPlayerModel",
                "FakeKillAura",
                "FakeSpinbot",
                "FakeBhop",
                "FakeCriticals",
                "FakeJesus",
                "FakeScaffold",
                "FakeSneak",
                "FakeVelocity",
            ),
            ModuleAmnesia.inner.map { it.name },
        )
    }

    @Test
    fun `Amnesia keeps every Java mixin bridge static`() {
        assertStaticBridge("setTargetName", String::class.java)
        assertStaticBridge("findTarget")
        assertStaticBridge("isAmnesiaTarget", LivingEntity::class.java)
        assertStaticBridge("shouldFakeSneak", LivingEntity::class.java)
        assertStaticBridge("getActionState", LivingEntity::class.java)
        assertStaticBridge("getSpoofedName", Player::class.java)
        assertStaticBridge("getSpoofedDisplayName", Player::class.java, Component::class.java)
        assertStaticBridge("hasSpoofedAppearance", Player::class.java)
        assertStaticBridge("getSpoofedSkin", AbstractClientPlayer::class.java)
        assertStaticBridge("getVisualTransform", LivingEntity::class.java)
        assertStaticBridge("getAuxiliaryVisualPosition", LivingEntity::class.java, Float::class.javaPrimitiveType!!)
    }

    @Test
    fun `appearance accepts NameMC ids and image URLs without changing the cache key`() {
        assertEquals("abcdef1234567890", Appearance.parseNameMcSkinId("ABCDEF1234567890"))
        assertEquals(
            "abcdef1234567890",
            Appearance.parseNameMcSkinId("https://s.namemc.com/i/ABCDEF1234567890.png?download=1"),
        )
        assertNull(Appearance.parseNameMcSkinId("https://example.com/not-a-skin.png"))
    }

    private fun assertStaticBridge(name: String, vararg parameterTypes: Class<*>) {
        val method = ModuleAmnesia::class.java.getDeclaredMethod(name, *parameterTypes)
        assertTrue(Modifier.isStatic(method.modifiers), "$name must remain callable from Java mixins")
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
