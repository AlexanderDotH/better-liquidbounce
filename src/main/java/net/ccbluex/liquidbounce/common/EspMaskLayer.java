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

package net.ccbluex.liquidbounce.common;

/**
 * Independent prepared-model mask destinations.
 *
 * <p>Keeping ownership in the layer prevents one module's model color from replacing another
 * module's color before post-processing.</p>
 */
public enum EspMaskLayer {
    PROTECTED_SURFACE,
    PLAYER_GLOW,
    TARGET_GLOW,
    ITEM_GLOW,
    ORB_GLOW,
    STORAGE_GLOW,
    PLAYER_OUTLINE,
    STORAGE_OUTLINE
}
