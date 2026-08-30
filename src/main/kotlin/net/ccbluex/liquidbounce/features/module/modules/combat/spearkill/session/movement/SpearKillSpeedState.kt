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



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
/** Immutable speed policy used both for route projection and one confirmed outbound step. */
internal data class SpearKillSpeedLimits(
    val targetSpeed: Double,
    val acceleration: Double,
    val deceleration: Double,
    val stepDistance: Double,
    val vanillaBudget: Double,
) {
    init {
        require(targetSpeed.isPositiveSpearKillSpeed()) { "Target speed must be finite and positive" }
        require(acceleration.isPositiveSpearKillSpeed()) { "Acceleration must be finite and positive" }
        require(deceleration.isPositiveSpearKillSpeed()) { "Deceleration must be finite and positive" }
        require(stepDistance.isPositiveSpearKillSpeed()) { "Step distance must be finite and positive" }
        require(vanillaBudget.isPositiveSpearKillSpeed()) { "Vanilla budget must be finite and positive" }
    }
}

/** Requested speed and the independently bounded route step for one outbound movement tick. */
internal data class SpearKillSpeedStep(
    val requestedSpeed: Double,
    val stepLimit: Double,
)

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
