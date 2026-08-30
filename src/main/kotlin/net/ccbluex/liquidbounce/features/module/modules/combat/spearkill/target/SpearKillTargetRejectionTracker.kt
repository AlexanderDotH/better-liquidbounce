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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import java.util.IdentityHashMap

/** Circuit-breaks stale route loops while allowing a held request to recover automatically. */
internal class SpearKillTargetRejectionTracker<T : Any>(
    private val retryDelayTicks: Int,
) {

    private val rejectedUntilTick = IdentityHashMap<T, Long>()

    init {
        require(retryDelayTicks > 0) { "Target retry delay must be positive" }
    }

    fun reject(target: T, currentTick: Int) {
        rejectedUntilTick[target] = currentTick.toLong() + retryDelayTicks
    }

    fun allow(target: T) {
        rejectedUntilTick.remove(target)
    }

    fun isRejected(target: T, currentTick: Int): Boolean {
        val rejectedUntil = rejectedUntilTick[target] ?: return false
        if (currentTick.toLong() < rejectedUntil) return true
        rejectedUntilTick.remove(target)
        return false
    }

    /** Route teardown may discard stale identities, but must retain a live correction cooldown. */
    fun clearExpired(currentTick: Int) {
        val tick = currentTick.toLong()
        rejectedUntilTick.entries.removeIf { (_, rejectedUntil) -> tick >= rejectedUntil }
    }

    fun clear() {
        rejectedUntilTick.clear()
    }
}
