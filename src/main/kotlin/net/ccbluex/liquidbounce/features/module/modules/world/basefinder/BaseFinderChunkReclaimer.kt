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

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos

internal object BaseFinderChunkReclaimer {
    fun reclaim(server: BaseFinderBackgroundServer, dimensionKey: String, chunkX: Int, chunkZ: Int) {
        val levelKey = BaseFinderBackgroundServer.levelKeyFor(dimensionKey) ?: return
        val level = server.getLevel(levelKey) ?: return
        val source = level.chunkSource
        val center = ChunkPos(chunkX, chunkZ)
        try {
            source.removeTicketWithRadius(
                TicketType.UNKNOWN,
                center,
                BaseFinderBackgroundServer.GENERATION_TICKET_RADIUS,
            )
        } catch (_: Throwable) {
        }
        runUnloadPass(level)
        if (source.loadedChunksCount > BaseFinderBackgroundServer.MAX_RESIDENT_CHUNKS) {
            BaseFinderBackgroundServer.LOGGER.warn(
                "BaseFinder BG still holding {} chunks after reclaim — host will recycle",
                source.loadedChunksCount,
            )
            server.markForRestart()
        }
    }

    private fun runUnloadPass(level: ServerLevel) {
        val source = level.chunkSource
        try {
            source.tick({ true }, false)
        } catch (_: Throwable) {
        }
        try {
            while (source.pollTask()) {
                // drain unload/save tasks
            }
        } catch (_: Throwable) {
        }
    }
}
