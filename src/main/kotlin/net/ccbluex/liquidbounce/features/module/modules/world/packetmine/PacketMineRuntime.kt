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

import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.tool.MineToolMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockRotation
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.HitResult
import kotlin.math.max

internal class PacketMineRuntime : MinecraftShortcuts {

    private var rotation: Rotation? = null
    private val scheduler = PacketMineTickScheduler()

    fun resetStartDelay() {
        scheduler.resetStartDelay()
    }

    fun onRotationUpdate() {
        val mineTarget = ModulePacketMine._target ?: return
        mineTarget.updateBlockState()
        rotate(mineTarget)
    }

    fun onTick() {
        scheduler.advanceTick()
        val mineTarget = ModulePacketMine._target ?: return
        if (mineTarget.isInvalidOrOutOfRange()) {
            ModulePacketMine._target = null
            return
        }

        mineTarget.updateBlockState()
        handleBreaking(mineTarget)
    }

    private fun rotate(mineTarget: MineTarget) {
        val shouldRotate = ModulePacketMine.rotationMode.shouldRotate(mineTarget)
        val raytrace = raytraceBlockRotation(
            player.eyePosition,
            mineTarget.targetPos,
            mineTarget.blockState,
            range = ModulePacketMine.range.toDouble(),
            wallsRange = ModulePacketMine.wallsRange.toDouble(),
        ) ?: run {
            mineTarget.abort()
            return
        }

        if (shouldRotate) {
            RotationManager.setRotationTarget(
                raytrace.rotation,
                considerInventory = !ModulePacketMine.ignoreOpenInventory,
                valueGroup = ModulePacketMine.rotations,
                Priority.IMPORTANT_FOR_USAGE_2,
                ModulePacketMine,
            )
        }

        rotation = raytrace.rotation
    }

    private fun handleBreaking(mineTarget: MineTarget) {
        val hit = raytraceBlock(
            max(ModulePacketMine.range, ModulePacketMine.wallsRange).toDouble(),
            RotationManager.serverRotation,
            mineTarget.targetPos,
            mineTarget.blockState,
        )
        val invalidHit = hit == null || hit.type != HitResult.Type.BLOCK || hit.blockPos != mineTarget.targetPos
        if (invalidHit && ModulePacketMine.rotationMode.getFailProcedure(mineTarget).execute(mineTarget)) {
            return
        }
        if (!updateDirection(mineTarget)) {
            return
        }
        if (player.isCreative) {
            handleCreativeBreaking(mineTarget)
            return
        }
        handleSurvivalBreaking(mineTarget)
    }

    private fun updateDirection(mineTarget: MineTarget): Boolean {
        val currentRotation = rotation ?: return false
        mineTarget.direction = raytraceBlock(
            max(ModulePacketMine.range, ModulePacketMine.wallsRange).toDouble() + 1.0,
            rotation = currentRotation,
            pos = mineTarget.targetPos,
            state = mineTarget.blockState,
        )?.direction ?: run {
            FailProcedure.ABORT.execute(mineTarget)
            return false
        }
        return true
    }

    private fun handleCreativeBreaking(mineTarget: MineTarget) {
        interaction.startPrediction(world) { sequence: Int ->
            interaction.destroyBlock(mineTarget.targetPos)
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                mineTarget.targetPos,
                mineTarget.direction!!,
                sequence,
            )
        }
        ModulePacketMine.swingMode.swing(InteractionHand.MAIN_HAND)
    }

    private fun handleSurvivalBreaking(mineTarget: MineTarget) {
        val switchMode = ModulePacketMine.switchMode.activeMode
        val slot = switchMode.getSlot(mineTarget.blockState)
        if (!mineTarget.started) {
            if (!scheduler.canStart()) {
                return
            }
            startBreaking(slot, mineTarget)
        } else if (ModulePacketMine.mode.activeMode.shouldUpdate(mineTarget, slot)) {
            PacketMineProgress.update(mineTarget, slot)
            finishBreakingIfReady(mineTarget, switchMode)
        }
        switchMode.getSwitchingMethod().reset()
    }

    private fun startBreaking(slot: HotbarItemSlot?, mineTarget: MineTarget) {
        switch(slot, mineTarget)
        if (ModulePacketMine.switchMode.activeMode.syncOnStart) {
            interaction.ensureHasSentCarriedItem()
        }

        ModulePacketMine.mode.activeMode.start(mineTarget)
        mineTarget.started = true
        mineTarget.finishReadyTick = null
    }

    private fun finishBreakingIfReady(mineTarget: MineTarget, switchMode: MineToolMode) {
        if (mineTarget.progress < ModulePacketMine.breakDamage || mineTarget.finished) {
            return
        }
        val finishReadyTick = mineTarget.finishReadyTick
        if (finishReadyTick == null) {
            mineTarget.finishReadyTick = scheduler.tick
            return
        }
        if (!scheduler.shouldFinish(finishReadyTick, ModulePacketMine.postBreakDelay)) {
            return
        }

        ModulePacketMine.mode.activeMode.finish(mineTarget)
        switchMode.getSwitchingMethod().switchBack()
    }

    fun switch(slot: HotbarItemSlot?, mineTarget: MineTarget) {
        if (slot == null) {
            return
        }
        val switchMode = ModulePacketMine.switchMode.activeMode
        if (switchMode.shouldSwitch(mineTarget)) {
            switchMode.getSwitchingMethod().switch(slot, mineTarget)
        }
    }
}
