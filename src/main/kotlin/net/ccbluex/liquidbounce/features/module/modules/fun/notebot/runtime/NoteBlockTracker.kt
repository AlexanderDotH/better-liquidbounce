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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotRuntimeBridge
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockRotation
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult

class NoteBlockTracker(val pos: BlockPos) : MinecraftShortcuts {
    var currentNote: Int? = null
        private set

    private val tuneTimeout = Chronometer()
    private val testTimeout = Chronometer()

    fun canTuneRightNow(): Boolean = currentNote != null || tuneTimeout.hasElapsed(2000)
    fun canTestRightNow(): Boolean = testTimeout.hasElapsed(2000)

    fun tuneOnce() {
        interact()
        tuneTimeout.reset()
    }

    fun testOnce() {
        click()
        testTimeout.reset()
    }

    private fun interact() {
        val blockState = pos.state!!
        val range = NotebotRuntimeBridge.range()
        val raytrace = raytraceBlockRotation(
            player.eyePosition,
            pos,
            blockState,
            range = range,
            wallsRange = range,
        ) ?: return

        val blockHitResult: BlockHitResult = raytraceBlock(
            range,
            raytrace.rotation,
            pos,
            blockState,
        ) ?: return

        network.send(
            ServerboundMovePlayerPacket.Rot(
                raytrace.rotation.yaw,
                raytrace.rotation.pitch,
                player.lastOnGround,
                player.horizontalCollision,
            )
        )

        interaction.startPrediction(world) { sequence ->
            ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, blockHitResult, sequence)
        }

        currentNote = null
    }

    fun click() {
        interaction.startPrediction(world) { sequence ->
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                Direction.UP,
                sequence,
            )
        }
        network.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
    }

    override fun equals(other: Any?) = other is NoteBlockTracker && pos == other.pos
    override fun hashCode() = pos.hashCode()

    fun setObservedNote(note: Int) {
        currentNote = note
    }
}
