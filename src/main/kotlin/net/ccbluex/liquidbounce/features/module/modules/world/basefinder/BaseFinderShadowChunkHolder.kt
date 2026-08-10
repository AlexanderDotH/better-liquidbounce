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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.minecraft.server.level.GenerationChunkHolder
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ProtoChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import java.util.concurrent.CompletableFuture

/**
 * [GenerationChunkHolder] that always surfaces a disposable [ProtoChunk].
 *
 * Used to build a [net.minecraft.server.level.WorldGenRegion] that writes into shadow chunks instead of
 * loading edited world chunks from disk.
 */
internal class BaseFinderShadowChunkHolder(
    pos: ChunkPos,
    private val proto: ProtoChunk,
) : GenerationChunkHolder(pos) {

    override fun getTicketLevel(): Int = TICKET_LEVEL

    override fun getQueueLevel(): Int = TICKET_LEVEL

    override fun addSaveDependency(future: CompletableFuture<*>) = Unit

    override fun getChunkIfPresentUnchecked(status: ChunkStatus): ChunkAccess = proto

    override fun getChunkIfPresent(status: ChunkStatus): ChunkAccess = proto

    override fun getLatestChunk(): ChunkAccess = proto

    companion object {
        private const val TICKET_LEVEL = 33
    }
}
