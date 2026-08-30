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

import net.ccbluex.liquidbounce.utils.client.mc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val MARKER_SIZE = 9
internal const val HEAD_SIZE = 7
internal const val HEAD_INSET = 1
internal const val MARKER_TOP_OVERHANG = 2
internal const val LOCATOR_BOTTOM_OFFSET = 29
internal const val LOCATOR_INNER_WIDTH = 173.0
internal const val VISIBLE_DEGREE_RANGE = 60.0
