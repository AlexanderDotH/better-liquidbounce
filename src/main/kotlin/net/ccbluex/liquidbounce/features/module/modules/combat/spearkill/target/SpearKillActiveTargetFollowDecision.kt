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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target


internal enum class SpearKillActiveTargetFollowDecision {
    CONTINUE,
    PAUSE,
    TERMINATE_UNREACHABLE,
}

internal fun resolveSpearKillActiveTargetFollow(
    isCombatSafe: Boolean,
    isRecovering: () -> Boolean,
    tracksPacketTarget: () -> Boolean,
): SpearKillActiveTargetFollowDecision = when {
    !isCombatSafe -> SpearKillActiveTargetFollowDecision.TERMINATE_UNREACHABLE
    isRecovering() -> SpearKillActiveTargetFollowDecision.PAUSE
    !tracksPacketTarget() -> SpearKillActiveTargetFollowDecision.PAUSE
    else -> SpearKillActiveTargetFollowDecision.CONTINUE
}
