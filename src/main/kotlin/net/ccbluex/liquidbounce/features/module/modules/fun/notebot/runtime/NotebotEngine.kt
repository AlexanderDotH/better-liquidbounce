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

import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotStage
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.SongData
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundSource
import kotlin.math.log2
import kotlin.math.roundToInt

internal interface NotebotStageHandler {
    val handledStage: NotebotStage
    fun onTick(engine: NotebotEngine)
}

class NotebotEngine internal constructor(
    val songData: SongData,
    internal val blocksAndRequirements: NotebotBlocksAndRequirements,
    initialStage: (NotebotEngine) -> NotebotStageHandler,
) {
    private var currentStageHandler: NotebotStageHandler = initialStage(this)
    private var ticksToWait: Int? = null

    private val notebotTrackerMap: Map<BlockPos, NoteBlockTracker> = blocksAndRequirements.availableBlocks
        .flatMap { it.value }
        .associateBy { it.pos }

    fun handleSoundPacket(packet: ClientboundSoundPacket) {
        if (currentStageHandler.handledStage == NotebotStage.PLAY) return

        val soundLocation = packet.sound.value().location
        if (packet.source != SoundSource.RECORDS || !soundLocation.path.contains("note_block")) return

        val pos = BlockPos((packet.x - 0.5).toInt(), (packet.y - 0.5).toInt(), (packet.z - 0.5).toInt())
        val causingNoteBlock = notebotTrackerMap[pos] ?: return
        causingNoteBlock.setObservedNote((12f + 12f * log2(packet.pitch)).roundToInt())
    }

    suspend fun onTick() {
        ticksToWait?.let {
            waitTicks(it)
            ticksToWait = null
        }
        currentStageHandler.onTick(this)
    }

    internal fun changeStage(handler: NotebotStageHandler) {
        ticksToWait = NotebotRuntimeBridge.stageDelay(handler.handledStage)
        NotebotRuntimeBridge.onStageChanged(handler.handledStage)
        currentStageHandler = handler
    }
}
