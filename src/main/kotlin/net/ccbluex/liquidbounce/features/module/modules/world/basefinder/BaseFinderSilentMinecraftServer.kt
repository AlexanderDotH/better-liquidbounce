/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

/**
 * Marker for unpublished BaseFinder worldgen servers.
 *
 * Fabric lifecycle events (and mods listening to them — Xaero, Distant Horizons, …) must not treat
 * these as the game's integrated/dedicated server. A second lifecycle start while singleplayer is
 * running previously crashed Xaero and tore down Distant Horizons' shared IO pool.
 *
 * Fabric tick events are also skipped: Spark's shared TPS statistics crash with
 * [ArrayIndexOutOfBoundsException] when a second server ticks beside the real one.
 */
interface BaseFinderSilentMinecraftServer
