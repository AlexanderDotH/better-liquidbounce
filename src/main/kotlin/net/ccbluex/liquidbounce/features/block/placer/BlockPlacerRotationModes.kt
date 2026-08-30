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
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationBridge
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.HitResult
import kotlin.math.max

sealed class BlockPlacerRotationMode(
    name: String,
    private val modeValueGroup: ModeValueGroup<BlockPlacerRotationMode>,
    val placer: BlockPlacer
) : Mode(name) {

    val postMove by boolean("PostMove", false)

    abstract operator fun invoke(isSupport: Boolean, pos: BlockPos, placementTarget: BlockPlacementTarget): Boolean

    open fun getVerificationRotation(targetedRotation: Rotation) = targetedRotation

    open fun onTickStart() {}

    override val parent: ModeValueGroup<*>
        get() = modeValueGroup

}

/**
 * Normal rotations.
 * Only one placement per tick is possible, possible less because rotating takes some time.
 */
class NormalRotationMode(modeValueGroup: ModeValueGroup<BlockPlacerRotationMode>, placer: BlockPlacer)
    : BlockPlacerRotationMode("Normal", modeValueGroup, placer) {

    private val rotations = BlockPlacementRotationBridge.createSettings(this).also {
        tree(it.valueGroup)
    }

    override fun invoke(isSupport: Boolean, pos: BlockPos, placementTarget: BlockPlacementTarget): Boolean {
        val interactedBlockPos = placementTarget.interactedBlockPos
        RotationManager.setRotationTarget(
            placementTarget.rotation,
            considerInventory = !placer.ignoreOpenInventory,
            valueGroup = rotations.targetFactory,
            provider = placer.module,
            priority = placer.priority,
            whenReached = RestrictedSingleUseAction({
                val raytraceResult = raytraceBlock(
                    max(placer.range, placer.wallRange).toDouble(),
                    RotationManager.currentRotation ?: return@RestrictedSingleUseAction false,
                    interactedBlockPos,
                    interactedBlockPos.stateOrEmpty
                ) ?: return@RestrictedSingleUseAction false

                raytraceResult.type == HitResult.Type.BLOCK && raytraceResult.blockPos == interactedBlockPos
            }, {
                BlockPlacementRotationBridge.schedule(placer.module, postMove, priority = true) {
                    if (placer.ticksToWait > 0) {
                        return@schedule
                    }

                    placer.doPlacement(isSupport, pos, placementTarget)
                    placer.ranAction = true
                }
            })
        )

        return true
    }

    override fun getVerificationRotation(targetedRotation: Rotation): Rotation = RotationManager.serverRotation

}

/**
 * No rotations, or just a packet containing the rotation target.
 */
class NoRotationMode(modeValueGroup: ModeValueGroup<BlockPlacerRotationMode>, placer: BlockPlacer)
    : BlockPlacerRotationMode("None", modeValueGroup, placer) {

    val send by boolean("SendRotationPacket", false)

    /**
     * Not rotating properly allows doing multiple placements. "b/o" stands for blocker per operation.
     */
    private val placements by int("Placements", 1, 1..10, "b/o")

    private var placementsDone = 0

    override fun invoke(isSupport: Boolean, pos: BlockPos, placementTarget: BlockPlacementTarget): Boolean {
        BlockPlacementRotationBridge.schedule(placer.module, postMove, task = {
            if (placer.ticksToWait > 0) {
                return@schedule
            }

            if (send) {
                val rotation = placementTarget.rotation.normalize()
                network.send(
                    ServerboundMovePlayerPacket.Rot(rotation.yaw, rotation.pitch, player.onGround(),
                        player.horizontalCollision)
                )
            }

            placer.doPlacement(isSupport, pos, placementTarget)
            placer.ranAction = true
        })

        placementsDone++
        return placementsDone == placements
    }

    override fun onTickStart() {
        placementsDone = 0
    }

}
