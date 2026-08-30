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

package net.ccbluex.liquidbounce.render.engine.esp

import com.mojang.blaze3d.pipeline.RenderTarget
import net.ccbluex.liquidbounce.common.EspMaskLayer

/**
 * Render-core composition boundary for feature-owned ESP activation, style, and cached masks.
 */
object EspFeatureRendererRegistry {

    private val glowProviders = SingleOwnerProviderRegistry<EspGlowSource, EspGlowFeatureProvider>()
    private val outlineProviders = SingleOwnerProviderRegistry<EspMaskLayer, EspOutlineFeatureProvider>()
    private val chamsProviders = SingleOwnerProviderRegistry<EspMaskLayer, EspChamsFeatureProvider>()

    internal fun registerGlow(
        id: String,
        source: EspGlowSource,
        style: () -> EspGlowStyle?,
        drawMask: (RenderTarget) -> Boolean = { false },
    ) {
        glowProviders.register(id, source, EspGlowFeatureProvider(style, drawMask))
    }

    internal fun registerOutline(
        id: String,
        layer: EspMaskLayer,
        style: () -> EspOutlineStyle?,
        drawMask: (RenderTarget) -> Boolean = { false },
    ) {
        require(layer == EspMaskLayer.PLAYER_OUTLINE || layer == EspMaskLayer.STORAGE_OUTLINE)
        outlineProviders.register(id, layer, EspOutlineFeatureProvider(style, drawMask))
    }

    internal fun registerChams(
        id: String,
        layer: EspMaskLayer,
        style: () -> EspChamsStyle?,
        drawMask: (RenderTarget) -> Boolean = { false },
    ) {
        require(layer == EspMaskLayer.ENTITY_CHAMS || layer == EspMaskLayer.STORAGE_CHAMS)
        chamsProviders.register(id, layer, EspChamsFeatureProvider(style, drawMask))
    }

    internal fun glow(source: EspGlowSource) = glowProviders.provider(source)

    internal fun outline(layer: EspMaskLayer) = outlineProviders.provider(layer)

    internal fun chams(layer: EspMaskLayer) = chamsProviders.provider(layer)
}

internal class EspGlowFeatureProvider(
    val style: () -> EspGlowStyle?,
    val drawMask: (RenderTarget) -> Boolean,
)

internal class EspOutlineFeatureProvider(
    val style: () -> EspOutlineStyle?,
    val drawMask: (RenderTarget) -> Boolean,
)

internal class EspChamsFeatureProvider(
    val style: () -> EspChamsStyle?,
    val drawMask: (RenderTarget) -> Boolean,
)

internal class SingleOwnerProviderRegistry<K : Any, V : Any> {

    private val lock = Any()
    private val providers = mutableMapOf<K, OwnedProvider<V>>()

    fun register(id: String, key: K, provider: V) {
        require(id.isNotBlank()) { "Render provider id must not be blank" }
        synchronized(lock) {
            val current = providers[key]
            check(current == null) { "Render source '$key' is already owned by '${current?.id}'" }
            providers[key] = OwnedProvider(id, provider)
        }
    }

    fun provider(key: K): V? = synchronized(lock) { providers[key]?.provider }
}

private data class OwnedProvider<V : Any>(val id: String, val provider: V)
