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
package net.ccbluex.liquidbounce.features.module.modules.misc.autoaccount.contract

internal class AutoAccountCommandActions(
    val register: () -> Unit,
    val login: () -> Unit,
)

internal object AutoAccountCommandBridge {
    private val unavailableActions = AutoAccountCommandActions(
        register = { error("AutoAccount command actions are not installed") },
        login = { error("AutoAccount command actions are not installed") },
    )

    private var actions = unavailableActions

    fun install(actions: AutoAccountCommandActions) {
        this.actions = actions
    }

    fun register() = actions.register()

    fun login() = actions.login()

    internal fun <T> withActionsForTest(actions: AutoAccountCommandActions, block: () -> T): T {
        val previous = this.actions
        this.actions = actions
        return try {
            block()
        } finally {
            this.actions = previous
        }
    }
}
