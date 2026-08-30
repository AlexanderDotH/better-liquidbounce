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
package net.ccbluex.liquidbounce.features.module.modules.world.nuker.mode

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.CancelBlockBreakingEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleFastBreak
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker.areaMode
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker.ignoreOpenInventory
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker.mode
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker.wasTarget
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.shouldNukerBreakImmediately
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.ModulePacketMine
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockRotation
import net.ccbluex.liquidbounce.features.block.runtime.doBreak
import net.ccbluex.liquidbounce.utils.block.isNotBreakable
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.ccbluex.liquidbounce.render.progress.BreakingProgress
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.max

internal fun isServerRotationReadyForNukerBreak(
    expectedTarget: BlockPos,
    hitResult: BlockHitResult?
) = hitResult?.type == HitResult.Type.BLOCK && hitResult.blockPos == expectedTarget

internal fun executeServerRotatedNukerBreak(
    expectedTarget: BlockPos,
    hitResult: BlockHitResult,
    sendRotation: () -> Unit,
    breakBlock: (BlockHitResult) -> Unit,
): Boolean {
    if (!isServerRotationReadyForNukerBreak(expectedTarget, hitResult)) {
        return false
    }

    sendRotation()
    breakBlock(hitResult)
    return true
}

/**
 * An active Legit Nuker break must win over ordinary combat aiming. Otherwise KillAura can keep
 * the server rotation on a nearby entity while Nuker waits forever for its block raytrace.
 */
internal val LEGIT_NUKER_ROTATION_PRIORITY = Priority.IMPORTANT_FOR_USAGE_3

object LegitNukerMode : Mode("Legit") {

    private var currentTarget: BlockPos? = null

    override val parent: ModeValueGroup<Mode>
        get() = mode

    private val range by float("Range", 5F, 1F..6F)
    private val wallRange by float("WallRange", 0f, 0F..6F).onChange {
        minOf(it, range)
    }

    internal val forceImmediateBreak by boolean("ForceImmediateBreak", false)
    private val rotations = tree(RotationsValueGroup(this))
    private val switchDelay by int("SwitchDelay", 0, 0..20, "ticks")

    internal fun breakingProgress(): BreakingProgress? {
        if (ModulePacketMine.running) {
            return null
        }

        val target = currentTarget ?: return null
        if (breaksImmediately()) {
            return wasTarget?.takeIf { it == target }?.let { BreakingProgress(it, 1f) }
        }

        return BreakingProgress.Provider.Default.breakingProgress(target)
    }

    @Suppress("unused")
    private val simulatedTickHandler = handler<RotationUpdateEvent> {
        if (!ignoreOpenInventory && mc.gui.screen() is AbstractContainerScreen<*>) {
            this.currentTarget = null
            return@handler
        }

        if (ModuleBlink.running) {
            this.currentTarget = null
            return@handler
        }

        this.currentTarget = lookupTarget()

        val currentTarget = currentTarget
        if (currentTarget == null) {
            wasTarget = null
            return@handler
        }

        if (ModulePacketMine.running) {
            ModulePacketMine.setTarget(currentTarget)
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val currentTarget = currentTarget ?: return@tickHandler
        val state = currentTarget.state ?: return@tickHandler

        if (ModulePacketMine.running) {
            return@tickHandler
        }

        // Wait for the switch delay to pass
        if (wasTarget != null && currentTarget != wasTarget) {
            waitTicks(switchDelay)
        }

        val rotation = RotationManager.currentRotation ?: return@tickHandler
        val rayTraceResult = raytraceBlock(
            max(range, wallRange).toDouble() + 1.0,
            rotation = rotation,
            pos = currentTarget,
            state = state
        ) ?: return@tickHandler

        val brokeBlock = executeServerRotatedNukerBreak(
            expectedTarget = currentTarget,
            hitResult = rayTraceResult,
            sendRotation = { sendServerRotation(rotation) },
            breakBlock = { doBreak(it, breaksImmediately()) },
        )
        if (!brokeBlock) {
            return@tickHandler
        }

        wasTarget = currentTarget
    }

    @Suppress("unused")
    private val cancelBlockBreakingHandler = handler<CancelBlockBreakingEvent> { event ->
        if (currentTarget != null && !ModulePacketMine.running) {
            event.cancelEvent()
        }
    }

    /**
     * Chooses the best block to break next and aims at it.
     */
    private fun lookupTarget(): BlockPos? {
        val eyes = player.eyePosition
        val packetMine = ModulePacketMine.running

        // Check if the current target is still valid
        currentTarget?.let { pos ->
            val blockState = pos.state ?: return@let

            if (blockState.isNotBreakable(pos) || !ModuleNuker.isValid(blockState)) {
                return@let
            }

            val raytraceResult = raytraceBlockRotation(
                eyes = eyes,
                pos = pos,
                state = blockState,
                range = range.toDouble(),
                wallsRange = wallRange.toDouble(),
            ) ?: return@let

            if (!packetMine) {
                aimAt(raytraceResult.rotation)
            }

            // We don't need to update the target if it's still valid
            return pos
        }

        for ((pos, blockState) in areaMode.activeMode.lookupTargets(range)) {
            val raytraceResult = raytraceBlockRotation(
                eyes = eyes,
                pos = pos,
                state = blockState,
                range = range.toDouble(),
                wallsRange = wallRange.toDouble(),
            ) ?: continue

            if (!packetMine) {
                aimAt(raytraceResult.rotation)
            }

            return pos
        }

        return null
    }

    private fun aimAt(rotation: Rotation) {
        RotationManager.setRotationTarget(
            rotations.toRotationTarget(rotation, considerInventory = !ignoreOpenInventory),
            priority = LEGIT_NUKER_ROTATION_PRIORITY,
            provider = ModuleNuker
        )
    }

    /**
     * Keep the normal RotationManager smoothing, but guarantee packet ordering for the block action.
     * This mirrors KillAura's on-tick rotation path: rotation first, action second.
     */
    private fun sendServerRotation(rotation: Rotation) {
        network.send(
            ServerboundMovePlayerPacket.PosRot(
                player.x,
                player.y,
                player.z,
                rotation.yaw,
                rotation.pitch,
                player.onGround(),
                player.horizontalCollision,
            )
        )
    }

    private fun breaksImmediately(): Boolean {
        return shouldNukerBreakImmediately(forceImmediateBreak, ModuleFastBreak.running)
    }

}
