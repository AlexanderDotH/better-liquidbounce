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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import java.util.IdentityHashMap

internal class MaceKillFailureNotificationGate(private val cooldownTicks: Int) {
    private var nextNotificationTick: Int? = null

    init {
        require(cooldownTicks > 0)
    }

    fun shouldNotify(currentTick: Int): Boolean {
        val nextTick = nextNotificationTick
        if (nextTick != null && currentTick < nextTick) return false
        nextNotificationTick = currentTick + cooldownTicks
        return true
    }

    fun clear() {
        nextNotificationTick = null
    }
}

internal class MaceKillTargetRejectionTracker<T : Any>(private val retryDelayTicks: Int) {
    private val rejectedUntilTick = IdentityHashMap<T, Long>()

    init {
        require(retryDelayTicks > 0)
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

    fun clearExpired(currentTick: Int) {
        val tick = currentTick.toLong()
        rejectedUntilTick.entries.removeIf { (_, rejectedUntil) -> tick >= rejectedUntil }
    }

    fun clear() {
        rejectedUntilTick.clear()
    }
}
