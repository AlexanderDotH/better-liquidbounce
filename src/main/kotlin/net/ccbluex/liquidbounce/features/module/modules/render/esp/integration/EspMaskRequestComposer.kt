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
package net.ccbluex.liquidbounce.features.module.modules.render.esp.integration

import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.common.EspMaskRequest

private const val PROTECTED_SURFACE_COLOR = -1

internal fun selectEntityMaskLayer(
    chams: () -> Boolean,
    glow: () -> Boolean,
    outline: () -> Boolean,
): EspMaskLayer? = when {
    chams() -> EspMaskLayer.ENTITY_CHAMS
    glow() -> EspMaskLayer.PLAYER_GLOW
    outline() -> EspMaskLayer.PLAYER_OUTLINE
    else -> null
}

internal fun selectStorageMaskLayer(
    chams: () -> Boolean,
    glow: () -> Boolean,
    outline: () -> Boolean,
): EspMaskLayer? = when {
    chams() -> EspMaskLayer.STORAGE_CHAMS
    glow() -> EspMaskLayer.STORAGE_GLOW
    outline() -> EspMaskLayer.STORAGE_OUTLINE
    else -> null
}

internal fun appendProtectedMask(
    request: EspMaskRequest,
    layer: EspMaskLayer,
    color: Int,
): EspMaskRequest = request
    .with(EspMaskLayer.PROTECTED_SURFACE, PROTECTED_SURFACE_COLOR)
    .with(layer, color)
