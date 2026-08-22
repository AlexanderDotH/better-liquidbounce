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

/** One remote launch at a time, with an explicit cooldown transition before a held retry. */
internal enum class MaceKillHoldAttackState {
    IDLE,
    ATTEMPTED,
    RETRY_ARMED,
}

internal data class MaceKillHoldAttackDecision(
    val state: MaceKillHoldAttackState,
    val launch: Boolean,
    val keepRouteAlive: Boolean,
)

internal fun advanceMaceKillHoldAttack(
    state: MaceKillHoldAttackState,
    attackHeld: Boolean,
    targetAvailable: Boolean,
    routeActive: Boolean,
    evidencePending: Boolean,
    cooldownReady: Boolean,
): MaceKillHoldAttackDecision {
    if (!attackHeld) {
        return MaceKillHoldAttackDecision(
            state = MaceKillHoldAttackState.IDLE,
            launch = false,
            keepRouteAlive = routeActive,
        )
    }

    val nextState = when {
        routeActive || evidencePending -> MaceKillHoldAttackState.ATTEMPTED
        state == MaceKillHoldAttackState.ATTEMPTED -> MaceKillHoldAttackState.RETRY_ARMED
        else -> state
    }
    val mayLaunch = targetAvailable && cooldownReady &&
        (nextState == MaceKillHoldAttackState.IDLE || nextState == MaceKillHoldAttackState.RETRY_ARMED)

    return MaceKillHoldAttackDecision(
        state = if (mayLaunch) MaceKillHoldAttackState.ATTEMPTED else nextState,
        launch = mayLaunch,
        keepRouteAlive = routeActive,
    )
}

internal fun armMaceKillHoldAttackRetry(
    state: MaceKillHoldAttackState,
): MaceKillHoldAttackState = if (state == MaceKillHoldAttackState.IDLE) {
    MaceKillHoldAttackState.IDLE
} else {
    MaceKillHoldAttackState.RETRY_ARMED
}
