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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleSafeActions
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class SafeActionsModuleContractTest {

    @Test
    fun `SafeActions is disabled while Drop is enabled without a timeout`() {
        val drop = ModuleSafeActions.inner
            .filterIsInstance<ToggleableValueGroup>()
            .single { it.name == "Drop" }

        assertEquals("SafeActions", ModuleSafeActions.name)
        assertEquals(ModuleCategories.MISC, ModuleSafeActions.category)
        assertFalse(ModuleSafeActions.enabled)
        assertTrue(drop.enabled)
        assertEquals(listOf("Enabled"), drop.inner.map { it.name })
    }

    @Test
    fun `SafeActions exposes static world and container drop bridges for mixins`() {
        assertStaticBridge("shouldAllowWorldDrop", LocalPlayer::class.java, Boolean::class.javaPrimitiveType!!)
        assertStaticBridge(
            "shouldAllowContainerDrop",
            Any::class.java,
            AbstractContainerMenu::class.java,
            Slot::class.java,
            Boolean::class.javaPrimitiveType!!,
        )
        assertStaticBridge(
            "observeContainerContext",
            Any::class.java,
            AbstractContainerMenu::class.java,
            Slot::class.java,
        )
    }

    @Test
    fun `SafeActions is registered with both dedicated drop mixins`() {
        val manager = readSource(MODULE_MANAGER_PATH)
        val mixinConfiguration = readSource(MIXIN_CONFIGURATION_PATH)
        val worldMixin = readSource(WORLD_MIXIN_PATH)
        val containerMixin = readSource(CONTAINER_MIXIN_PATH)

        assertTrue(manager.contains("ModuleSafeActions"), "ModuleManager must register ModuleSafeActions")
        assertTrue(
            mixinConfiguration.contains("minecraft.client.MixinMinecraftSafeActions"),
            "the dedicated world-drop mixin must be registered",
        )
        assertTrue(
            mixinConfiguration.contains("minecraft.gui.MixinAbstractContainerScreenSafeActions"),
            "the dedicated container-drop mixin must be registered",
        )
        assertTrue(worldMixin.contains("ModuleSafeActions.shouldAllowWorldDrop"))
        assertTrue(containerMixin.contains("ModuleSafeActions.shouldAllowContainerDrop"))
        assertTrue(containerMixin.contains("ModuleSafeActions.observeContainerContext"))
    }

    private fun assertStaticBridge(name: String, vararg parameterTypes: Class<*>) {
        val method = ModuleSafeActions::class.java.getDeclaredMethod(name, *parameterTypes)
        assertTrue(Modifier.isStatic(method.modifiers), "$name must be callable from Java mixins")
    }

    private fun readSource(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val MODULE_MANAGER_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/bootstrap/module/MiscModuleRegistry.kt"
        const val MIXIN_CONFIGURATION_PATH = "src/main/resources/liquidbounce.mixins.json"
        const val WORLD_MIXIN_PATH =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/" +
                "MixinMinecraftSafeActions.java"
        const val CONTAINER_MIXIN_PATH =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/gui/" +
                "MixinAbstractContainerScreenSafeActions.java"

        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
