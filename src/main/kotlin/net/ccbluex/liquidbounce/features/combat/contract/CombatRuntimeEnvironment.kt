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
package net.ccbluex.liquidbounce.features.combat.contract

internal object CombatRuntimeEnvironment {

    @Volatile
    private var criticalHitProvider: (Boolean) -> Boolean = { false }

    @Volatile
    private var killAuraTargetProvider: () -> Boolean = { false }

    @Volatile
    private var freeCamProvider: () -> Boolean = { false }

    @Volatile
    private var freeLookProvider: () -> Boolean = { false }

    @Volatile
    private var rotationPausedProvider: () -> Boolean = { false }

    fun bindCriticalHit(provider: (Boolean) -> Boolean) {
        criticalHitProvider = provider
    }

    fun bindKillAuraTarget(provider: () -> Boolean) {
        killAuraTargetProvider = provider
    }

    fun bindFreeCam(provider: () -> Boolean) {
        freeCamProvider = provider
    }

    fun bindFreeLook(provider: () -> Boolean) {
        freeLookProvider = provider
    }

    fun bindRotationPaused(provider: () -> Boolean) {
        rotationPausedProvider = provider
    }

    fun wouldDoCriticalHit(ignoreSprint: Boolean): Boolean = criticalHitProvider(ignoreSprint)

    fun hasActiveKillAuraTarget(): Boolean = killAuraTargetProvider()

    fun isDetachedViewEnabled(): Boolean = freeCamProvider() || freeLookProvider()

    fun shouldPauseRotation(): Boolean = rotationPausedProvider()

    @Synchronized
    internal fun <T> withProvidersForTest(
        criticalHit: (Boolean) -> Boolean = { false },
        killAuraTarget: () -> Boolean = { false },
        freeCam: () -> Boolean = { false },
        freeLook: () -> Boolean = { false },
        rotationPaused: () -> Boolean = { false },
        block: () -> T,
    ): T {
        val previousCriticalHit = criticalHitProvider
        val previousKillAuraTarget = killAuraTargetProvider
        val previousFreeCam = freeCamProvider
        val previousFreeLook = freeLookProvider
        val previousRotationPaused = rotationPausedProvider
        criticalHitProvider = criticalHit
        killAuraTargetProvider = killAuraTarget
        freeCamProvider = freeCam
        freeLookProvider = freeLook
        rotationPausedProvider = rotationPaused
        return try {
            block()
        } finally {
            criticalHitProvider = previousCriticalHit
            killAuraTargetProvider = previousKillAuraTarget
            freeCamProvider = previousFreeCam
            freeLookProvider = previousFreeLook
            rotationPausedProvider = previousRotationPaused
        }
    }
}
