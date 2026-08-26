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
package net.ccbluex.liquidbounce.features.trialchamber

internal data class TrialSpawnerBlockObservation(
    val phase: TrialSpawnerPhase,
    val completed: Boolean,
)

/** The loaded block state is newer than the block entity cache and therefore wins when both are available. */
internal fun resolveTrialSpawnerBlockObservation(
    liveBlockPhase: TrialSpawnerPhase?,
    blockEntityPhase: TrialSpawnerPhase?,
): TrialSpawnerBlockObservation {
    val phase = liveBlockPhase ?: blockEntityPhase ?: TrialSpawnerPhase.INACTIVE
    return TrialSpawnerBlockObservation(
        phase = phase,
        completed = phase == TrialSpawnerPhase.COOLDOWN,
    )
}

internal enum class TrialVaultBlockPhase(val permitsClaimInference: Boolean) {
    INACTIVE(true),
    ACTIVE(true),
    UNLOCKING(false),
    EJECTING(false),
}

/** Immutable client evidence used to reconcile a Vault's local-player status. */
internal data class TrialVaultBlockObservation(
    val phase: TrialVaultBlockPhase,
    val localPlayerConnected: Boolean,
    val localPlayerWithinRange: Boolean,
)
