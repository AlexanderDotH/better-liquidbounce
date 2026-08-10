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
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.util.StaticCache2D
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ProtoChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.chunk.status.ChunkStep

/**
 * [WorldGenRegion] that serves disposable shadow [ProtoChunk]s without vanilla status gating.
 *
 * Vanilla [WorldGenRegion.getChunk] refuses neighbors whose dependency status is not yet recorded on
 * [GenerationChunkHolder] futures. Our holders always expose the proto, but distance/status checks can
 * still throw "Requested chunk unavailable during world generation" for off-center region steps.
 */
internal class BaseFinderShadowWorldGenRegion(
    level: ServerLevel,
    cache: StaticCache2D<GenerationChunkHolder>,
    step: ChunkStep,
    center: ChunkAccess,
    private val protos: Map<Long, ProtoChunk>,
) : WorldGenRegion(level, cache, step, center) {

    override fun getChunk(chunkX: Int, chunkZ: Int): ChunkAccess = requireProto(chunkX, chunkZ)

    override fun getChunk(
        chunkX: Int,
        chunkZ: Int,
        status: ChunkStatus,
        load: Boolean,
    ): ChunkAccess = requireProto(chunkX, chunkZ)

    private fun requireProto(chunkX: Int, chunkZ: Int): ChunkAccess =
        protos[ChunkPos.pack(chunkX, chunkZ)]
            ?: error("Shadow region missing chunk $chunkX,$chunkZ")
}
