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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*


/** Acquires stable ownership before charge/synchronous route calculation can span another tick. */
internal fun shouldAcquireSpearKillPreparationLock(
    packetMovementMode: Boolean,
    attackActive: Boolean,
    attackRequested: Boolean,
    hasTarget: Boolean,
    hasLockedTarget: Boolean,
): Boolean = packetMovementMode && !attackActive && attackRequested && hasTarget && !hasLockedTarget

/**
 * A committed/preparing target uses hysteresis and deliberately has no three-block lower bound.
 * The acquisition bound selects a target; it must not invalidate that same target during approach.
 */
internal fun isSpearKillLockedTargetEligible(
    isCombatSafe: Boolean,
    isAlive: Boolean,
    isInCurrentWorld: Boolean,
    distance: Double,
    maximumDistance: Double,
    hysteresis: Double,
    isRejected: Boolean,
): Boolean = distance.isFinite() && maximumDistance.isFinite() && hysteresis.isFinite() &&
    distance >= 0.0 && maximumDistance >= 0.0 && hysteresis >= 0.0 &&
    isSpearKillTargetCandidateEligible(
        isCombatSafe = isCombatSafe,
        isAlive = isAlive,
        isInCurrentWorld = isInCurrentWorld,
        isWithinRange = distance <= maximumDistance + hysteresis,
        isRejected = isRejected,
    )
