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
package net.ccbluex.liquidbounce.render.engine.esp

import net.ccbluex.liquidbounce.common.EspMaskLayer

/** One independently captured and composited Glow owner. Declaration order is composite order. */
internal enum class EspGlowSource(
    val displayName: String,
    val useDepth: Boolean,
    val preparedLayer: EspMaskLayer? = null,
    val protectsSurface: Boolean = false,
) {
    BASE_FINDER("Base Finder", false),
    BLOCK_ESP("Block ESP", true),
    BLOCK_OUTLINE("Block Outline", false, protectsSurface = true),
    BLOCK_ESP_TRACERS("Block ESP Tracers", false),
    SPEAR_KILL_PATH("SpearKill Path", false),
    TRACERS("Tracers", false),
    ITEM_ESP("Item ESP", true, EspMaskLayer.ITEM_GLOW, protectsSurface = true),
    ORB_ESP("Orb ESP", true, EspMaskLayer.ORB_GLOW, protectsSurface = true),
    STORAGE_ESP("Storage ESP", true, EspMaskLayer.STORAGE_GLOW, protectsSurface = true),
    TARGET_GLOW("Target Glow", true, EspMaskLayer.TARGET_GLOW, protectsSurface = true),
    PLAYER_ESP("Player ESP", true, EspMaskLayer.PLAYER_GLOW, protectsSurface = true),
}

/** Selects model surfaces that auxiliary world glows must not recolor. */
internal object EspGlowProtectionPlan {

    /**
     * A source yields to later painter-order owners, while explicit surface owners protect their
     * visible color in both directions. Large early masks therefore cannot erase nested owners.
     */
    fun exclusionSources(
        source: EspGlowSource,
        availableSources: Collection<EspGlowSource>,
    ): List<EspGlowSource> = availableSources.filter { candidate ->
        candidate !== source && (candidate.protectsSurface || candidate.ordinal > source.ordinal)
    }
}
