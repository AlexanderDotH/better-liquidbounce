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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.types.list.Tagged

/** Selects how SpearKill owns a temporary movement assist. */
internal enum class SpearKillMovementAssistMode(override val tag: String) : Tagged {
    NONE("None"),
    PACKET("Packet"),
    INPUT("Input"),
}

/** Immutable movement caps selected before a route emits its first movement. */
internal data class SpearKillMovementTransport(
    val maxSpeed: Double,
    val stepLimit: Double,
    val elytraActive: Boolean,
)

/** Input and server-only ownership for one preparation or route tick. */
internal data class SpearKillMovementAssistLease(
    val injectJump: Boolean,
    val injectSneak: Boolean,
    val serverSneak: Boolean,
    val requestPacketFallFlying: Boolean,
)

/** Physical input snapshot used to prove that automation only ORs its own temporary input. */
internal data class SpearKillMovementInput(
    val jump: Boolean,
    val sneak: Boolean,
)

internal fun resolveSpearKillMovementTransport(
    configuredSpeed: Double,
    configuredStepLimit: Double,
    elytraActive: Boolean,
): SpearKillMovementTransport {
    val configuredMaximum = configuredSpeed.coerceIn(
        SPEAR_KILL_MIN_SPEED.toDouble(),
        SPEAR_KILL_ELYTRA_MAX_SPEED_DOUBLE,
    )
    val transportMaximum = if (elytraActive) {
        SPEAR_KILL_ELYTRA_MAX_SPEED_DOUBLE
    } else {
        SPEAR_KILL_NORMAL_MAX_SPEED.toDouble()
    }
    val maxSpeed = minOf(configuredMaximum, transportMaximum)
    val stepLimit = minOf(
        maxSpeed,
        configuredStepLimit.coerceIn(
            SPEAR_KILL_MIN_SPEED.toDouble(),
            SPEAR_KILL_ELYTRA_MAX_SPEED_DOUBLE,
        ),
    )
    return SpearKillMovementTransport(maxSpeed, stepLimit, elytraActive)
}

internal fun resolveSpearKillMovementAssistLease(
    preparationActive: Boolean,
    routeActive: Boolean,
    sneakMode: SpearKillMovementAssistMode,
    elytraMode: SpearKillMovementAssistMode,
    elytraUsable: Boolean,
    elytraActive: Boolean,
): SpearKillMovementAssistLease {
    val assistActive = preparationActive || routeActive
    if (!assistActive) {
        return SpearKillMovementAssistLease(
            injectJump = false,
            injectSneak = false,
            serverSneak = false,
            requestPacketFallFlying = false,
        )
    }

    val elytraOwnsMovement = elytraActive ||
        elytraUsable && elytraMode != SpearKillMovementAssistMode.NONE
    return SpearKillMovementAssistLease(
        injectJump = elytraMode == SpearKillMovementAssistMode.INPUT && elytraUsable,
        injectSneak = routeActive && sneakMode == SpearKillMovementAssistMode.INPUT && !elytraOwnsMovement,
        serverSneak = routeActive && sneakMode == SpearKillMovementAssistMode.PACKET && !elytraOwnsMovement,
        requestPacketFallFlying = elytraMode == SpearKillMovementAssistMode.PACKET &&
            elytraUsable && !elytraActive,
    )
}

internal fun applySpearKillMovementInputLease(
    physical: SpearKillMovementInput,
    lease: SpearKillMovementAssistLease,
): SpearKillMovementInput = SpearKillMovementInput(
    jump = physical.jump || lease.injectJump,
    sneak = physical.sneak || lease.injectSneak,
)
