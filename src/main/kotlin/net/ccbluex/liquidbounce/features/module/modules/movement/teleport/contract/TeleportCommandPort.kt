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
package net.ccbluex.liquidbounce.features.module.modules.movement.teleport.contract

internal object TeleportCommandPort {

    private lateinit var highTpProvider: () -> Boolean
    private lateinit var highTpAmountProvider: () -> Float
    private lateinit var teleportAction: (Double, Double, Double) -> Unit

    val highTp: Boolean
        get() = highTpProvider()
    val highTpAmount: Float
        get() = highTpAmountProvider()

    fun bind(
        highTpProvider: () -> Boolean,
        highTpAmountProvider: () -> Float,
        teleportAction: (Double, Double, Double) -> Unit,
    ) {
        this.highTpProvider = highTpProvider
        this.highTpAmountProvider = highTpAmountProvider
        this.teleportAction = teleportAction
    }

    fun indicateTeleport(x: Double, y: Double, z: Double) {
        teleportAction(x, y, z)
    }

}
