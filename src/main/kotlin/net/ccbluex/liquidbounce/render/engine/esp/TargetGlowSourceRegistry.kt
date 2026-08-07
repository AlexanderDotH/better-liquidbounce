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

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.entity.Entity

/** A full-model Glow request for one target that has already been selected by its owning feature. */
@JvmRecord
data class TargetGlowSelection(
    val entity: Entity,
    val color: Color4b,
    val style: EspGlowStyle,
)

/**
 * Connects target-owning features to the shared ESP entity-mask pass.
 *
 * Sources expose only their current selection; the registry never scans the world or changes target eligibility.
 * Registration order is the deterministic color precedence when several sources select the same entity.
 */
object TargetGlowSourceRegistry {

    private val sources = mutableListOf<() -> TargetGlowSelection?>()
    private val contributedStyles = linkedSetOf<EspGlowStyle>()

    @JvmStatic
    @Synchronized
    fun register(source: () -> TargetGlowSelection?): AutoCloseable {
        sources += source
        return AutoCloseable { unregister(source) }
    }

    @JvmStatic
    @Synchronized
    fun selectionFor(entity: Entity?): TargetGlowSelection? {
        if (entity == null) {
            return null
        }

        for (source in sources) {
            val selection = source() ?: continue
            if (selection.entity !== entity) continue

            contributedStyles += selection.style
            return selection
        }

        return null
    }

    /** Clears target styles before entity-mask requests for a new rendered frame are collected. */
    @Synchronized
    internal fun beginFrame() {
        contributedStyles.clear()
    }

    /** Returns this mask capture's styles and starts a fresh contribution set for the next frame. */
    @Synchronized
    internal fun consumeContributedStyles(): List<EspGlowStyle> = contributedStyles.toList().also {
        contributedStyles.clear()
    }

    @Synchronized
    private fun unregister(source: () -> TargetGlowSelection?) {
        sources.remove(source)
    }
}
