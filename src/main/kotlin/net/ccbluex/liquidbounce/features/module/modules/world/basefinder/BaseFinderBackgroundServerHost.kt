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

/** Owns at most one [BaseFinderBackgroundServer] for the active typed seed. */
internal object BaseFinderBackgroundServerHost {
    private val lock = Any()
    private var current: BaseFinderBackgroundServer? = null
    private var lastFailure: String? = null
    private var failureCooldownUntilMs: Long = 0L

    fun lastFailure(): String? = synchronized(lock) { lastFailure }

    fun clearFailure() = synchronized(lock) {
        lastFailure = null
        failureCooldownUntilMs = 0L
    }

    fun ifReady(): BaseFinderBackgroundServer? = synchronized(lock) {
        current?.takeIf { it.isReady && !it.isStopped }
    }

    fun currentViewDistance(): Int? = ifReady()?.viewDistance

    fun ensureRunning(seed: Long, viewDistance: Int): BaseFinderBackgroundServer = synchronized(lock) {
        val clampedView = BaseFinderBackgroundServer.clampViewDistance(viewDistance)
        val existing = current
        if (existing != null && existing.canReuseFor(seed, clampedView)) return existing
        val now = System.currentTimeMillis()
        if (now < failureCooldownUntilMs) {
            error(lastFailure ?: "background server cooling down after failure")
        }
        existing?.shutdownAndCleanup()
        current = null
        val started = try {
            BaseFinderBackgroundServer.spin(seed, clampedView)
        } catch (error: Throwable) {
            rememberFailure("server spin failed: ${error.message ?: error::class.java.simpleName}")
            throw error
        }
        if (!started.awaitReady()) {
            val reason = "server not ready for seed $seed view=$clampedView"
            rememberFailure(reason)
            started.shutdownAndCleanup()
            error(reason)
        }
        lastFailure = null
        failureCooldownUntilMs = 0L
        current = started
        started
    }

    fun shutdown() = synchronized(lock) {
        current?.shutdownAndCleanup()
        current = null
        MinecraftFullBaseFinderChunkExpector.invalidateGeneratedChunks()
    }

    private fun rememberFailure(reason: String) {
        lastFailure = reason
        failureCooldownUntilMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
    }

    private const val FAILURE_COOLDOWN_MS = 15_000L
}
