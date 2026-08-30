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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.common.Tagged

internal enum class BaseFinderBoxMode(override val tag: String) : Tagged {
    FIXED("Fixed"),
    DYNAMIC("Dynamic box"),
}

/**
 * Which vanilla worldgen path SeedMismatch uses to rebuild expected terrain.
 *
 * [FEATURES] regenerates noise→carvers→biome decoration from the typed seed via a background MinecraftServer
 * host (singleplayer and multiplayer). [BASE_COLUMN] uses only the fast noise-column API.
 * Neither backend falls back to the other on failure.
 */
internal enum class BaseFinderWorldBackend(override val tag: String) : Tagged {
    FEATURES("Features"),
    BASE_COLUMN("Base column"),
}
