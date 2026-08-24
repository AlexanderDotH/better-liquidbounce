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
package net.ccbluex.liquidbounce.features.baritone.adapter

/** Enables LiquidBounce's owning module only after Baritone accepted an automation request. */
class BaritoneAutomationActivation(private val activate: () -> Unit) {

    fun <T> afterSuccess(operation: () -> T): T = operation().also { activate() }

    fun accepted(accepted: Boolean): Boolean = accepted.also { if (it) activate() }

    /** Covers upstream processes started outside LiquidBounce's task and command entry points. */
    fun observedPathStart() = activate()
}
