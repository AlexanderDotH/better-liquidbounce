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

internal interface RuntimeStateOwner {
    val runtimeState: RuntimeState
}

internal interface RuntimeLifecycleContract : RuntimeStateOwner {
    fun onEnabled(
        structuresEnabled: Boolean,
        netherBedrockEnabled: Boolean,
        autoAcceptStrongEvidence: Boolean,
        persistProgress: Boolean,
        workerLimit: Int,
    ) = runtimeState.onEnabled(
        structuresEnabled,
        netherBedrockEnabled,
        autoAcceptStrongEvidence,
        persistProgress,
        workerLimit,
    )

    fun updateSettings(
        structuresEnabled: Boolean,
        netherBedrockEnabled: Boolean,
        autoAcceptStrongEvidence: Boolean,
        persistProgress: Boolean,
        workerLimit: Int,
    ) = runtimeState.updateSettings(
        structuresEnabled,
        netherBedrockEnabled,
        autoAcceptStrongEvidence,
        persistProgress,
        workerLimit,
    )

    fun onDisabled(persistProgress: Boolean) = runtimeState.onDisabled(persistProgress)
    fun onWorldChanged() = runtimeState.onWorldChanged()
    fun onTick() = runtimeState.onTick()
    fun consumePresentation() = runtimeState.consumePresentation()
}

internal interface RuntimeQueryContract : RuntimeStateOwner {
    fun status() = runtimeState.status()
    fun hudStatus() = runtimeState.hudStatus()
    fun pendingEvidenceIds() = runtimeState.pendingEvidenceIds()
    fun evidenceIds() = runtimeState.evidenceIds()
}

internal interface RuntimeEvidenceContract : RuntimeStateOwner {
    fun confirm(id: String) = runtimeState.confirm(id)
    fun reject(id: String) = runtimeState.reject(id)
    fun confirmGuided() = runtimeState.confirmGuided()
    fun rejectGuided() = runtimeState.rejectGuided()
    fun undo(id: String) = runtimeState.undo(id)
}

internal interface RuntimeControlContract : RuntimeStateOwner {
    fun pause() = runtimeState.pause()
    fun resume() = runtimeState.resume()
    fun resetCurrent() = runtimeState.resetCurrent()
    fun resetAll() = runtimeState.resetAll()
}

internal interface SeedCrackerEvidenceCommands {
    fun status() = SeedCrackerRuntime.status()
    fun pendingEvidenceIds() = SeedCrackerRuntime.pendingEvidenceIds()
    fun evidenceIds() = SeedCrackerRuntime.evidenceIds()
    fun confirm(id: String) = SeedCrackerRuntime.confirm(id)
    fun confirmGuided() = SeedCrackerRuntime.confirmGuided()
}

internal interface SeedCrackerDecisionCommands {
    fun reject(id: String) = SeedCrackerRuntime.reject(id)
    fun rejectGuided() = SeedCrackerRuntime.rejectGuided()
    fun undo(id: String) = SeedCrackerRuntime.undo(id)
}

internal interface SeedCrackerControlCommands {
    fun pause() = SeedCrackerRuntime.pause()
    fun resume() = SeedCrackerRuntime.resume()
    fun resetCurrent() = SeedCrackerRuntime.resetCurrent()
    fun resetAll() = SeedCrackerRuntime.resetAll()
}

internal interface SeedCrackerCommandOperations :
    SeedCrackerEvidenceCommands,
    SeedCrackerDecisionCommands,
    SeedCrackerControlCommands
