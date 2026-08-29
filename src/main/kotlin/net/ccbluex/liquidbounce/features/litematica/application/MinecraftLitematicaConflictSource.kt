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
package net.ccbluex.liquidbounce.features.litematica.application

import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleAutoTool
import net.ccbluex.liquidbounce.features.module.modules.world.autobuild.ModuleAutoBuild
import net.ccbluex.liquidbounce.features.module.modules.world.fucker.ModuleFucker
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.ModulePacketMine
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

class MinecraftLitematicaConflictSource(
    private val silentHotbarOwner: Any,
) {

    fun capture(
        rotationUnavailable: Boolean,
        allowOwnedMiningAutoTool: Boolean = false,
    ): LitematicaConflictSnapshot {
        val player = mc.player
        val silentHotbarOwnedByPrinter = SilentHotbar.isSlotModifiedBy(silentHotbarOwner)
        val silentHotbarOwnedByAutoTool = allowOwnedMiningAutoTool && SilentHotbar.isSlotModifiedBy(ModuleAutoTool)

        return LitematicaConflictSnapshot(
            packetMineRunning = ModulePacketMine.running,
            scaffoldRunning = ModuleScaffold.running,
            autoBuildRunning = ModuleAutoBuild.running,
            fuckerRunning = ModuleFucker.running,
            blinkRunning = ModuleBlink.running,
            foreignSilentHotbar = SilentHotbar.isSlotModified() &&
                !silentHotbarOwnedByPrinter && !silentHotbarOwnedByAutoTool,
            containerScreenOpen = mc.gui.screen() is AbstractContainerScreen<*>,
            usingItem = player?.isUsingItem == true,
            rotationUnavailable = rotationUnavailable,
        )
    }
}
