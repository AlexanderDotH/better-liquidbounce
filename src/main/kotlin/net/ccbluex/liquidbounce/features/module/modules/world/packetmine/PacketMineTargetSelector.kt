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
package net.ccbluex.liquidbounce.features.module.modules.world.packetmine

import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.utils.block.immutable
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

internal class PacketMineTargetSelector : MinecraftShortcuts {

    private val chronometer = Chronometer()

    fun onMouseButton(event: MouseButtonEvent) {
        val openScreen = mc.gui.screen() != null
        val unchangeableActive = !ModulePacketMine.mode.activeMode.canManuallyChange && ModulePacketMine._target != null
        if (openScreen || unchangeableActive || !player.abilities.mayBuild) {
            return
        }

        val hasTimePassed = chronometer.hasElapsed(ModulePacketMine.selectDelay.toLong())
        val hitResult = mc.hitResult
        if (!event.isLeftButton || !hasTimePassed || hitResult !is BlockHitResult) {
            return
        }

        val blockPos = hitResult.blockPos
        val state = blockPos.stateOrEmpty
        val activeTarget = ModulePacketMine._target
        val shouldTargetBlock = ModulePacketMine.mode.activeMode.shouldTarget(blockPos, state)
        val isCancelledByUser = blockPos == activeTarget?.targetPos

        if (activeTarget != null && shouldBlockTargetChange(activeTarget)) {
            chronometer.reset()
            return
        }

        val selectedTarget = if (
            shouldTargetBlock && world.worldBorder.isWithinBounds(blockPos) && !isCancelledByUser
        ) {
            MineTarget(blockPos.immutable)
        } else {
            null
        }
        ModulePacketMine._target = selectedTarget
        chronometer.reset()
    }

    fun onPacket(packet: Packet<*>) {
        when (packet) {
            is ClientboundBlockUpdatePacket -> mc.execute {
                onBlockStateUpdate(packet.pos, packet.blockState)
            }
            is ClientboundSectionBlocksUpdatePacket -> mc.execute {
                packet.runUpdates(::onBlockStateUpdate)
            }
        }
    }

    fun onBlockStateUpdate(pos: BlockPos, state: BlockState) {
        val target = ModulePacketMine._target ?: return
        if (pos != target.targetPos) {
            return
        }
        if (state.isAir && ModulePacketMine.mode.activeMode.stopOnStateChange) {
            ModulePacketMine._target = null
        }
    }

    fun setTarget(blockPos: BlockPos) {
        val state = blockPos.state
        val shouldTargetBlock = state != null && ModulePacketMine.mode.activeMode.shouldTarget(blockPos, state)
        if (!shouldTargetBlock || !world.worldBorder.isWithinBounds(blockPos)) {
            return
        }

        val activeTarget = ModulePacketMine._target
        if (activeTarget != null && shouldBlockTargetChange(activeTarget)) {
            return
        }
        if (activeTarget?.finished != false && ModulePacketMine.mode.activeMode.canManuallyChange || activeTarget == null) {
            ModulePacketMine._target = MineTarget(blockPos.immutable)
        }
    }

    private fun shouldBlockTargetChange(mineTarget: MineTarget): Boolean {
        return ModulePacketMine.mode.activeMode.shouldPreventTargetChange(mineTarget)
    }
}
