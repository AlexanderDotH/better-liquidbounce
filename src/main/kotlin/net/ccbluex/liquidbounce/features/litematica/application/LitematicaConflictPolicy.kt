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
package net.ccbluex.liquidbounce.features.litematica.application

enum class LitematicaConflict {
    PACKET_MINE,
    SCAFFOLD,
    AUTO_BUILD,
    FUCKER,
    BLINK,
    FOREIGN_SILENT_HOTBAR,
    CONTAINER_SCREEN,
    ITEM_USE,
    ROTATION_UNAVAILABLE,
}

data class LitematicaConflictSnapshot(
    val packetMineRunning: Boolean = false,
    val scaffoldRunning: Boolean = false,
    val autoBuildRunning: Boolean = false,
    val fuckerRunning: Boolean = false,
    val blinkRunning: Boolean = false,
    val foreignSilentHotbar: Boolean = false,
    val containerScreenOpen: Boolean = false,
    val usingItem: Boolean = false,
    val rotationUnavailable: Boolean = false,
)

object LitematicaConflictPolicy {

    fun firstPause(snapshot: LitematicaConflictSnapshot): LitematicaConflict? = when {
        snapshot.packetMineRunning -> LitematicaConflict.PACKET_MINE
        snapshot.scaffoldRunning -> LitematicaConflict.SCAFFOLD
        snapshot.autoBuildRunning -> LitematicaConflict.AUTO_BUILD
        snapshot.fuckerRunning -> LitematicaConflict.FUCKER
        snapshot.blinkRunning -> LitematicaConflict.BLINK
        snapshot.foreignSilentHotbar -> LitematicaConflict.FOREIGN_SILENT_HOTBAR
        snapshot.containerScreenOpen -> LitematicaConflict.CONTAINER_SCREEN
        snapshot.usingItem -> LitematicaConflict.ITEM_USE
        snapshot.rotationUnavailable -> LitematicaConflict.ROTATION_UNAVAILABLE
        else -> null
    }
}
