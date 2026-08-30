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

package net.ccbluex.liquidbounce.integration.theme

interface ThemeRouteSupport {
    fun isSupported(name: String?): Boolean

    fun isScreenSupported(name: String?): Boolean

    fun isOverlaySupported(name: String?): Boolean
}

internal class MetadataThemeRouteSupport : ThemeRouteSupport {

    private var metadata: ThemeMetadata? = null

    fun load(metadata: ThemeMetadata) {
        this.metadata = metadata
    }

    override fun isSupported(name: String?) = isScreenSupported(name) || isOverlaySupported(name)

    override fun isScreenSupported(name: String?) = name != null && loadedMetadata.screens.contains(name)

    override fun isOverlaySupported(name: String?) = name != null && loadedMetadata.overlays.contains(name)

    private val loadedMetadata: ThemeMetadata
        get() = requireNotNull(metadata) { "metadata not loaded" }
}
