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
package net.ccbluex.liquidbounce.features.module.modules.world.surround.runtime

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.ccbluex.fastutil.fastIterator
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.block.placer.BlockPlacer
import net.ccbluex.liquidbounce.utils.block.isBlockedByEntitiesReturnCrystal
import net.minecraft.core.BlockPos

internal class SurroundProtection(
    parent: EventListener,
    private val placerProvider: () -> BlockPlacer,
    private val extraLayerForced: () -> Boolean,
) : ToggleableValueGroup(parent, "Protect", true) {

    @Suppress("MagicNumber")
    private val minDestroyProgress by int("MinDestroyProgress", 4, 0..9, "stage")

    val extraLayer = SurroundExtraLayer(this)

    init {
        tree(extraLayer)
    }

    val broken = LongOpenHashSet()

    /** Runs before the block placer's crystal destroyer. */
    @Suppress("unused", "LoopWithTooManyJumpStatements")
    private val tickHandler = handler<GameTickEvent>(priority = 10) {
        val placer = placerProvider()
        if (!placer.crystalDestroyer.enabled && (extraLayerForced() || !extraLayer.enabled)) {
            return@handler
        }

        broken.clear()
        for (entry in placer.blocks.fastIterator()) {
            if (entry.booleanValue) continue
            val posAsLong = entry.longKey
            val breakingProgressions = world.destructionProgress()[posAsLong] ?: continue
            val breakingInfo = breakingProgressions.lastOrNull { it.id != player.id } ?: continue
            val stage = breakingInfo.progress

            if (stage < minDestroyProgress) {
                continue
            }

            val pos = BlockPos.of(posAsLong)
            if (extraLayer.enabled && stage > 0) {
                broken.add(posAsLong)
            }

            if (!placer.crystalDestroyer.enabled) {
                continue
            }

            val crystal = pos.isBlockedByEntitiesReturnCrystal().value() ?: continue
            placer.crystalDestroyer.currentTarget = crystal
            if (placer.crystalDestroyer.currentTarget == crystal) {
                return@handler
            }
        }
    }
}

internal class SurroundExtraLayer(parent: EventListener) : ToggleableValueGroup(parent, "ExtraLayer", true) {
    val corners by boolean("Corners", false)
}
