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

import net.minecraft.server.level.ChunkResult
import net.minecraft.world.level.chunk.ChunkAccess
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class BaseFinderChunkGenerationRuntime {
    private val jobs = ConcurrentLinkedQueue<ChunkGenJob>()
    private var activeJob: ChunkGenJob? = null
    private var activeFuture: CompletableFuture<ChunkResult<ChunkAccess>>? = null

    fun pump(server: BaseFinderBackgroundServer) {
        completeActive()
        if (activeJob != null) return
        val job = jobs.poll() ?: return
        try {
            val levelKey = BaseFinderBackgroundServer.levelKeyFor(job.dimensionKey)
                ?: error("unsupported dimension ${job.dimensionKey}")
            val level = server.getLevel(levelKey) ?: error("level not loaded ${job.dimensionKey}")
            activeJob = job
            activeFuture = level.chunkSource.getChunkFuture(
                job.chunkX,
                job.chunkZ,
                BaseFinderBackgroundServer.GENERATION_STATUS,
                true,
            )
        } catch (error: Throwable) {
            job.future.completeExceptionally(error)
            clearActive()
        }
    }

    fun generate(
        server: BaseFinderBackgroundServer,
        dimensionKey: String,
        chunkX: Int,
        chunkZ: Int,
    ): ChunkAccess? {
        if (!server.isReady || server.isStopped) error("server not ready for chunk $dimensionKey $chunkX,$chunkZ")
        val job = ChunkGenJob(dimensionKey, chunkX, chunkZ, CompletableFuture())
        jobs.add(job)
        return try {
            job.future.get(BaseFinderBackgroundServer.CHUNK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            job.future.cancel(true)
            error("timed out generating chunk $dimensionKey $chunkX,$chunkZ")
        }
    }

    fun failPending(error: Throwable) {
        activeJob?.future?.completeExceptionally(error)
        clearActive()
        while (true) {
            val job = jobs.poll() ?: break
            job.future.completeExceptionally(error)
        }
    }

    private fun completeActive() {
        val job = activeJob ?: return
        val future = activeFuture ?: return
        if (!future.isDone) return
        try {
            val result = future.join()
            if (result.isSuccess) {
                job.future.complete(result.orElse(null))
            } else {
                job.future.completeExceptionally(
                    IllegalStateException(
                        "chunk gen failed ${job.dimensionKey} ${job.chunkX},${job.chunkZ}: ${result.error}",
                    ),
                )
            }
        } catch (error: Throwable) {
            job.future.completeExceptionally(error)
        } finally {
            clearActive()
        }
    }

    private fun clearActive() {
        activeJob = null
        activeFuture = null
    }

    private data class ChunkGenJob(
        val dimensionKey: String,
        val chunkX: Int,
        val chunkZ: Int,
        val future: CompletableFuture<ChunkAccess?>,
    )
}

internal fun BaseFinderBackgroundServer.pumpChunkWorkerMailboxes() {
    for (level in allLevels) {
        try {
            while (level.chunkSource.pollTask()) {
                // drain
            }
        } catch (_: Throwable) {
            // Best-effort; generation futures still complete via pollTask.
        }
    }
}
