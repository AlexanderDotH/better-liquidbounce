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

package net.ccbluex.liquidbounce.interfaces

import net.ccbluex.liquidbounce.injection.mixins.minecraft.blockentity.MixinVaultSharedDataAccessor
import net.ccbluex.liquidbounce.injection.mixins.minecraft.client.MinecraftAccessor
import net.ccbluex.liquidbounce.injection.mixins.minecraft.gui.MixinAbstractSignEditScreenAccessor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InjectionAccessPortContractTest {

    @Test
    fun `mixin accessors implement foundation-owned access ports`() {
        assertTrue(MinecraftUseItemAccess::class.java.isAssignableFrom(MinecraftAccessor::class.java))
        assertTrue(SignEditScreenAccess::class.java.isAssignableFrom(MixinAbstractSignEditScreenAccessor::class.java))
        assertTrue(VaultSharedDataAccess::class.java.isAssignableFrom(MixinVaultSharedDataAccessor::class.java))
    }

    @Test
    fun `feature consumers do not import concrete mixin accessors`() {
        listOf(NO_BLOCK_INTERACT, AUTO_TIMESTAMP, TRIAL_CHAMBER_RUNTIME).forEach { path ->
            assertFalse(source(path).contains("import net.ccbluex.liquidbounce.injection.mixins."), path)
        }
    }

    @Test
    fun `foundation interfaces do not import feature implementations`() {
        assertFalse(source(GUI_MESSAGE_ADDITION).contains("import net.ccbluex.liquidbounce.features."))
    }

    @Test
    fun `ViaVersion packet hook delegates inventory state through the foundation runtime port`() {
        val mixin = source(MIXIN_PACKET_WRAPPER)

        assertTrue(mixin.contains("InventoryRuntimeHooks.INSTANCE.isInventoryOpenServerSide()"))
        assertTrue(mixin.contains("InventoryRuntimeHooks.INSTANCE.onClickOccurs()"))
        assertTrue(mixin.contains("InventoryRuntimeHooks.INSTANCE.setInventoryOpenServerSide(true)"))
        assertFalse(mixin.contains("import net.ccbluex.liquidbounce.features.inventory.InventoryManager;"))
    }

    private fun source(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val NO_BLOCK_INTERACT =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/ModuleNoBlockInteract.kt"
        const val AUTO_TIMESTAMP =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/ModuleAutoTimestamp.kt"
        const val TRIAL_CHAMBER_RUNTIME =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/trialchamber/TrialChamberRuntime.kt"
        const val GUI_MESSAGE_ADDITION =
            "src/main/java/net/ccbluex/liquidbounce/interfaces/GuiMessageAddition.java"
        const val MIXIN_PACKET_WRAPPER =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/viaversion/MixinPacketWrapper.java"
    }
}
