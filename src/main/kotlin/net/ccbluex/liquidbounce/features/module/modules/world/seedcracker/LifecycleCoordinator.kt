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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.mc
import net.fabricmc.loader.api.FabricLoader

internal fun RuntimeState.onEnabled(
    structuresEnabled: Boolean,
    netherBedrockEnabled: Boolean,
    autoAcceptStrongEvidence: Boolean,
    persistProgress: Boolean,
    workerLimit: Int,
) {
    enabled = true
    updateSettings(
        structuresEnabled,
        netherBedrockEnabled,
        autoAcceptStrongEvidence,
        persistProgress,
        workerLimit,
    )
    warnAboutParallelSeedCrackerX()
    activateCurrentScope()
    subscribe()
    refreshStatusProjection()
}

internal fun RuntimeState.updateSettings(
    structuresEnabled: Boolean,
    netherBedrockEnabled: Boolean,
    autoAcceptStrongEvidence: Boolean,
    persistProgress: Boolean,
    workerLimit: Int,
) {
    val previousSettings = settings
    settings = RuntimeSettings(
        structuresEnabled = structuresEnabled,
        netherBedrockEnabled = netherBedrockEnabled,
        autoAcceptStrongEvidence = autoAcceptStrongEvidence,
        persistProgress = persistProgress,
        workerLimit = workerLimit.coerceIn(MIN_WORKERS, MAX_WORKERS),
    )
    if (settings != previousSettings) invalidateCandidate()
    tracker.updateWorkerLimit(settings.workerLimit)
    activeScope.get()?.let(::offerCurrentSnapshot)
    refreshStatusProjection()
}

internal fun RuntimeState.onDisabled(persistProgress: Boolean) {
    val scope = activeScope.get()
    if (persistProgress) scope?.let(::persist)
    enabled = false
    unsubscribe()
    tracker.deactivate()
    clearVolatileEvidence()
    activeScope.set(null)
    candidate.set(null)
    latestSolveResult.set(null)
    latestStatus.set(null)
    lastGuidanceKey = null
    presentations.clear()
}

internal fun RuntimeState.onWorldChanged() {
    activeScope.get()?.takeIf { settings.persistProgress }?.let(::persist)
    clearVolatileEvidence()
    candidate.set(null)
    latestSolveResult.set(null)
    lastGuidanceKey = null
    activateCurrentScope()
    refreshStatusProjection()
}

internal fun RuntimeState.onTick() {
    refreshSolverResult()
    rescanDirtyChunks()
    refreshStatusProjection()
    publishGuidanceIfChanged()
}

internal fun RuntimeState.consumePresentation(): SeedCrackerPresentation? {
    refreshSolverResult()
    refreshStatusProjection()
    publishGuidanceIfChanged()
    return presentations.poll()
}

internal fun RuntimeState.status(): SeedCrackerPresentation {
    refreshSolverResult()
    val status = refreshStatusProjection() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
    latestSolveResult.get()?.conflictReport?.let { return conflictPresentation(it) }
    return statusPresentation(status)
}

internal fun RuntimeState.hudStatus(): SeedCrackerStatus? {
    refreshSolverResult()
    return refreshStatusProjection()
}

private fun RuntimeState.activateCurrentScope() {
    if (!enabled) return
    val scope = currentScope() ?: run {
        activeScope.set(null)
        tracker.deactivate()
        presentations += presentation("noWorld", NotificationEvent.Severity.INFO)
        return
    }
    clearVolatileEvidence()
    activeScope.set(scope)
    tracker.deactivate()
    tracker.activate(scope)
    candidate.set(null)
    latestSolveResult.set(null)
    load(scope)
    lastGuidanceKey = null
    offerCurrentSnapshot(scope)
    refreshStatusProjection(scope)
    presentations += presentation("enabled", NotificationEvent.Severity.INFO, scope.dimensionKey)
}

private fun RuntimeState.subscribe() {
    if (subscribed) return
    ChunkScanner.subscribe(subscriber)
    subscribed = true
}

private fun RuntimeState.unsubscribe() {
    if (!subscribed) return
    ChunkScanner.unsubscribe(subscriber)
    subscribed = false
}

private fun RuntimeState.warnAboutParallelSeedCrackerX() {
    if (FabricLoader.getInstance().isModLoaded("seedcrackerx")) {
        presentations += presentation("externalSeedCrackerX", NotificationEvent.Severity.ERROR)
    }
}

private fun currentScope(): CrackScope? {
    val level = mc.level ?: return null
    val localWorldName = mc.singleplayerServer?.worldData?.levelName ?: "unknown"
    val rawServerIdentity = mc.currentServer?.ip ?: "singleplayer:$localWorldName"
    return CrackScope(
        CrackScope.fingerprintServerIdentity(rawServerIdentity),
        level.dimension().identifier().toString(),
    )
}
