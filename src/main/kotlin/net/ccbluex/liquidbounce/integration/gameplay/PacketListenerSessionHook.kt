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

package net.ccbluex.liquidbounce.integration.gameplay

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillSetbackHook
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleNoCapability
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleNoRotateSet
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.ReachInteractableFeature
import net.ccbluex.liquidbounce.features.module.modules.render.playermodel.ServerPlayerModelStateTracker
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player

object PacketListenerSessionHook {

    @JvmStatic
    fun resetPlayerModelState() {
        ServerPlayerModelStateTracker.reset()
    }

    @JvmStatic
    fun serverAbilitiesApplied(packet: ClientboundPlayerAbilitiesPacket, player: LocalPlayer) {
        ModuleNoCapability.onServerAbilitiesApplied(packet, player.abilities)
    }

    @JvmStatic
    fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        SpearKillSetbackHook.beforeCorrection(packet, player)
        ReachInteractableFeature.beforeCorrection(packet, player)
    }

    @JvmStatic
    fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        SpearKillSetbackHook.afterCorrection(packet, player)
        ReachInteractableFeature.afterCorrection(packet, player)
        ServerPlayerModelStateTracker.correct(player.position(), player.yRot, player.xRot)
    }

    @JvmStatic
    fun shouldRestoreRotation(): Boolean = ModuleNoRotateSet.running &&
        Minecraft.getInstance().gui.screen() !is LevelLoadingScreen

    @JvmStatic
    fun restoreRotation(player: Player, previousRotation: Rotation) {
        if (ModuleNoRotateSet.mode.activeMode == ModuleNoRotateSet.ResetRotation) {
            val target = ModuleNoRotateSet.ResetRotation.rotations.toRotationTarget(
                Rotation(player.yRot, player.xRot, true),
                null,
                true,
                null,
            )
            RotationManager.setRotationTarget(target, Priority.NOT_IMPORTANT, ModuleNoRotateSet)
        }

        player.yRot = previousRotation.yRot + 0.000001f
        player.xRot = previousRotation.xRot + 0.000001f
    }
}
