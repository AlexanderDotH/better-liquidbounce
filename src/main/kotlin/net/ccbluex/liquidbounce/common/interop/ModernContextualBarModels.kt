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
package net.ccbluex.liquidbounce.common.interop

@JvmRecord
data class ModernContextualBarSnapshot(
    val mode: String,
    val progress: Float,
    val level: Int,
    val cooldown: Boolean,
    val markers: List<ModernLocatorMarker>,
) {
    companion object {
        @JvmField
        val EMPTY = ModernContextualBarSnapshot("empty", 0f, 0, false, emptyList())
    }
}

@JvmRecord
data class ModernLocatorMarker(
    val id: String,
    val label: String,
    val offset: Double,
    val elevation: String,
    val distance: Int,
    val color: Int,
    val kind: String,
    val playerUuid: String?,
    val style: String,
)
