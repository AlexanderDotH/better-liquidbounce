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

@file:JvmName("ClientLocatorFallbackKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.theme.component

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.utils.client.mc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun resolveClientLocatorFallbackPolicy(
    hudRunning: Boolean,
    appearanceHidden: Boolean,
    hudTheme: HudTheme,
    bundledHud: Boolean,
): Boolean = hudRunning && !appearanceHidden && hudTheme == HudTheme.MODERN && bundledHud

internal fun shouldUseClientLocatorFallback(
    serverHasWaypoints: Boolean,
    fallbackEnabled: Boolean,
    hasEligiblePlayers: Boolean,
): Boolean = !serverHasWaypoints && fallbackEnabled && hasEligiblePlayers

internal fun isEligibleLocatorPlayer(
    isLocal: Boolean,
    isSpectator: Boolean,
    isRemoved: Boolean,
    isAlive: Boolean,
    isBot: Boolean,
    hasPlayerInfo: Boolean,
    isCrouching: Boolean,
    isInvisible: Boolean,
): Boolean = !isLocal && !isSpectator && !isRemoved && isAlive && !isBot && hasPlayerInfo &&
    !isCrouching && !isInvisible

internal fun resolveLocatorMarkerOffset(yawDegrees: Double): Double? {
    if (yawDegrees <= -VISIBLE_DEGREE_RANGE || yawDegrees > VISIBLE_DEGREE_RANGE) {
        return null
    }

    return yawDegrees / VISIBLE_DEGREE_RANGE
}

internal fun resolveLocatorMarkerX(guiWidth: Int, yawDegrees: Double): Int? {
    resolveLocatorMarkerOffset(yawDegrees) ?: return null

    val center = ceil((guiWidth - MARKER_SIZE) / 2f).toInt()
    val offset = floor(yawDegrees * LOCATOR_INNER_WIDTH / 2.0 / VISIBLE_DEGREE_RANGE).toInt()
    return center + offset
}
