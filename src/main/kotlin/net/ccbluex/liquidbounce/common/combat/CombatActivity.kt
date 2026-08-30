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
package net.ccbluex.liquidbounce.common.combat

fun interface CombatActivityPort {
    fun isInCombat(): Boolean
}

object CombatActivity {

    private val INACTIVE = CombatActivityPort { false }

    @Volatile
    private var port: CombatActivityPort = INACTIVE

    val isInCombat: Boolean
        get() = port.isInCombat()

    @Synchronized
    fun install(port: CombatActivityPort) {
        check(this.port === INACTIVE) { "Combat activity port is already installed" }
        this.port = port
    }

    @Synchronized
    internal fun <T> withPortForTest(candidate: CombatActivityPort?, block: () -> T): T {
        val previous = port
        port = candidate ?: INACTIVE
        return try {
            block()
        } finally {
            port = previous
        }
    }
}
