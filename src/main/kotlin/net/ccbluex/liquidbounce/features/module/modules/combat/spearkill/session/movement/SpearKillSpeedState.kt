/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedLimits
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.isPositiveSpearKillSpeed

/**
 * SpearKill-owned transient speed. Previewing never mutates state; only a delivered outbound
 * movement may call [confirmOutbound].
 */
internal class SpearKillSpeedController {

    private var sessionStartSpeed: Double = 0.0

    var currentSpeed: Double = 0.0
        private set

    var active: Boolean = false
        private set

    fun begin(observedSpeed: Double, targetSpeed: Double) {
        require(targetSpeed.isPositiveSpearKillSpeed()) { "Target speed must be finite and positive" }
        if (active) return
        currentSpeed = observedSpeed.takeIf(Double::isFinite)?.coerceIn(0.0, targetSpeed) ?: 0.0
        sessionStartSpeed = currentSpeed
        active = true
    }

    fun preview(limits: SpearKillSpeedLimits): SpearKillSpeedStep =
        SpearKillSpeedProfile(currentSpeed, limits).stepAt(0)

    fun confirmOutbound(limits: SpearKillSpeedLimits): SpearKillSpeedStep = preview(limits).also {
        currentSpeed = it.requestedSpeed
    }

    fun profile(limits: SpearKillSpeedLimits): SpearKillSpeedProfile =
        SpearKillSpeedProfile(currentSpeed, limits)

    fun rejectOutboundProgress() {
        if (active) currentSpeed = sessionStartSpeed
    }

    fun reset() {
        currentSpeed = 0.0
        sessionStartSpeed = 0.0
        active = false
    }
}

/** Pure future projection; it never advances the owning [SpearKillSpeedController]. */
