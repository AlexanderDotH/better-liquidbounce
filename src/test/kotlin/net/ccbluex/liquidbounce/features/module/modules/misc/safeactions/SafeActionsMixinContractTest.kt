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

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeActionsMixinContractTest {

    @Test
    fun `manual world drop is the only handleKeybinds operation guarded`() {
        val source = source(WORLD_DROP_MIXIN)

        assertTrue(source.contains("@Mixin(Minecraft.class)"))
        assertTrue(source.contains("method = \"handleKeybinds\""))
        assertTrue(source.contains("Lnet/minecraft/client/player/LocalPlayer;drop(Z)Z"))
        assertTrue(source.contains("require = 1"))
        assertTrue(source.contains("allow = 1"))
        assertTrue(source.contains("ModuleSafeActions.shouldAllowWorldDrop(player, dropAll)"))
        assertTrue(source.contains("original.call(player, dropAll)"))
    }

    @Test
    fun `container keyboard throw is guarded while other slot clicks remain vanilla`() {
        val source = source(CONTAINER_DROP_MIXIN)

        assertTrue(source.contains("@Mixin(AbstractContainerScreen.class)"))
        assertTrue(source.contains("method = \"keyPressed\""))
        assertTrue(source.contains(SLOT_CLICKED_METHOD))
        assertTrue(source.contains(SLOT_CLICKED_DESCRIPTOR))
        assertTrue(source.contains("require = 2"))
        assertTrue(source.contains("allow = 2"))
        assertTrue(source.contains("containerInput != ContainerInput.THROW"))
        assertTrue(
            source.contains(
                "ModuleSafeActions.shouldAllowContainerDrop(screen, menu, slot, buttonNum == 1)",
            ),
        )
        assertTrue(source.contains("original.call(screen, slot, slotId, buttonNum, containerInput)"))
        assertFalse(source.contains("method = \"mouseClicked\""))
        assertFalse(source.contains("method = \"slotClicked"))
    }

    @Test
    fun `SafeActions stays isolated from the existing shared mixins`() {
        assertFalse(source(EXISTING_WORLD_MIXIN).contains("ModuleSafeActions"))
        assertFalse(source(EXISTING_CONTAINER_MIXIN).contains("ModuleSafeActions"))
    }

    @Test
    fun `render completion publishes the current container hover context`() {
        val source = source(CONTAINER_DROP_MIXIN)

        assertTrue(source.contains("method = \"extractRenderState\""))
        assertTrue(source.contains("at = @At(\"RETURN\")"))
        assertTrue(source.contains("ModuleSafeActions.observeContainerContext(screen, menu, hoveredSlot)"))
    }

    @Test
    fun `dedicated SafeActions mixins are registered`() {
        val mixins = JsonParser.parseString(source(MIXIN_CONFIG))
            .asJsonObject
            .getAsJsonArray("client")
            .map { it.asString }

        assertTrue("minecraft.client.MixinMinecraftSafeActions" in mixins)
        assertTrue("minecraft.gui.MixinAbstractContainerScreenSafeActions" in mixins)
    }

    private fun source(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val WORLD_DROP_MIXIN =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinMinecraftSafeActions.java"
        const val CONTAINER_DROP_MIXIN =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/gui/MixinAbstractContainerScreenSafeActions.java"
        const val EXISTING_WORLD_MIXIN =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinMinecraft.java"
        const val EXISTING_CONTAINER_MIXIN =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/gui/MixinAbstractContainerScreen.java"
        const val MIXIN_CONFIG = "src/main/resources/liquidbounce.mixins.json"
        const val SLOT_CLICKED_METHOD =
            "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked("
        const val SLOT_CLICKED_DESCRIPTOR =
            "Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V"
    }
}
