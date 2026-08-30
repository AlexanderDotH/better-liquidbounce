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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.events.BlockShapeEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.movement.avoidhazards.AvoidHazardInputPlanner
import net.ccbluex.liquidbounce.features.module.modules.movement.avoidhazards.AvoidHazardCollision
import net.ccbluex.liquidbounce.features.module.modules.movement.avoidhazards.Avoid
import net.ccbluex.liquidbounce.features.module.modules.movement.avoidhazards.AvoidMode
import net.ccbluex.liquidbounce.features.module.modules.movement.avoidhazards.isLadderClimbStateAt
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.math.toBlockPos
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes

/**
 * Anti hazards module
 *
 * Prevents you walking into blocks that might be malicious for you.
 */
object ModuleAvoidHazards : ClientModule("AvoidHazards", ModuleCategories.MOVEMENT) {
    private var mode by enumChoice("Mode", AvoidMode.SHAPE)
    private val avoid by multiEnumChoice("Avoid", Avoid.entries)

    // Conflicts with AvoidHazards
    val cobWebs get() = Avoid.COBWEB in avoid

    private const val MOVEMENT_PREDICTION_TICKS = 2

    @Suppress("MagicNumber")
    private val UNSAFE_BLOCK_CAP = Block.box(
        0.0,
        0.0,
        0.0,
        16.0,
        4.0,
        16.0
    )

    @Suppress("unused")
    val shapeHandler = handler<BlockShapeEvent> { event ->
        if (mode != AvoidMode.SHAPE) {
            return@handler
        }

        avoid.find { it.test(event.state.block, event.state.fluidState, event.pos) }?.let {
            event.shape = if (it.fullCube) Shapes.block() else UNSAFE_BLOCK_CAP
        }
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(priority = SAFETY_FEATURE) { event ->
        if (mode != AvoidMode.INPUT || !event.directionalInput.isMoving) {
            return@handler
        }

        val activeAvoidModes = avoid
        if (activeAvoidModes.isEmpty()) {
            return@handler
        }

        event.directionalInput = AvoidHazardInputPlanner.chooseSafeInput(event.directionalInput) { candidate ->
            isSafeInput(
                directionalInput = candidate,
                jump = event.jump,
                sneak = event.sneak,
                avoidModes = activeAvoidModes
            )
        }
    }

    private fun isSafeInput(
        directionalInput: DirectionalInput,
        jump: Boolean,
        sneak: Boolean,
        avoidModes: Collection<Avoid>
    ): Boolean {
        val level = mc.level ?: return true

        val simulatedInput = SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(
            directionalInput = directionalInput,
            jump = jump,
            sprinting = player.isSprinting,
            sneaking = sneak
        )

        val simulatedPlayer = SimulatedPlayer.fromClientPlayer(simulatedInput)
        simulatedPlayer.pos = player.position()
        var previousBoundingBox = simulatedPlayer.boundingBox
        // Do not reject every candidate while already on a ladder. We only block
        // transitions that newly enter climb-state.
        val startedOnLadder = Avoid.LADDERS in avoidModes && wouldEnterLadderClimbState(simulatedPlayer)

        repeat(MOVEMENT_PREDICTION_TICKS) {
            simulatedPlayer.tick()
            val currentBoundingBox = simulatedPlayer.boundingBox
            val sweptBoundingBox = previousBoundingBox.minmax(currentBoundingBox)
            val enteredLadder =
                Avoid.LADDERS in avoidModes &&
                    !startedOnLadder &&
                    wouldEnterLadderClimbState(simulatedPlayer)

            if (enteredLadder ||
                AvoidHazardCollision.isUnsafe(currentBoundingBox, level, avoidModes) ||
                AvoidHazardCollision.isUnsafe(sweptBoundingBox, level, avoidModes)
            ) {
                return false
            }

            previousBoundingBox = currentBoundingBox
        }

        return true
    }

    /**
     * Predict whether the simulated player would be in a vanilla climb-state
     * after this movement step.
     *
     * @see net.minecraft.world.entity.LivingEntity.onClimbable
     * @see isLadderClimbState
     */
    private fun wouldEnterLadderClimbState(simulatedPlayer: SimulatedPlayer): Boolean {
        return isLadderClimbStateAt(simulatedPlayer.pos.toBlockPos())
    }

}
