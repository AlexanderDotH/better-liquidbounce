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
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.fastutil.fastIterator
import net.ccbluex.liquidbounce.render.FULL_BOX
import net.minecraft.core.BlockPos
import java.util.function.LongPredicate

internal fun BlockPlacer.updateQueue(positions: Collection<BlockPos>) {
    val iterator = blocks.fastIterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        val position = blockPosCache.set(entry.longKey)
        if (position !in positions) {
            targetRenderer.removeBlock(position)
            iterator.remove()
        } else {
            entry.setValue(false)
        }
    }

    positions.forEach { addToQueue(it, false) }
    targetRenderer.updateAll()
}

/**
 * Adds a block to be placed.
 *
 * @param pos The position, can be [BlockPos.MutableBlockPos].
 * @param update Whether the renderer should update the culling.
 */
internal fun BlockPlacer.addQueue(pos: BlockPos, update: Boolean = true, isSupport: Boolean = false) {
    blocks.computeIfAbsent(pos.asLong(), LongPredicate {
        targetRenderer.addBlock(blockPosCache.set(it), update, FULL_BOX)
        isSupport
    })
}

/**
 * Removes a block from the queue.
 *
 * @param pos The position, can be [BlockPos.MutableBlockPos].
 */
internal fun BlockPlacer.removeQueue(pos: BlockPos) {
    blocks.remove(pos.asLong())
    targetRenderer.removeBlock(pos)
}

/**
 * Discards all blocks.
 */
internal fun BlockPlacer.clearQueue() {
    blocks.fastIterator().forEach { targetRenderer.removeBlock(blockPosCache.set(it.longKey)) }
    blocks.clear()
}

/**
 * This should be called when the module using this placer is disabled.
 */
internal fun BlockPlacer.disablePlacer() {
    resetState()
    crystalDestroyer.onDisable()
    targetRenderer.clearSilently()
    placedRenderer.clearSilently()
}

internal fun BlockPlacer.isQueueDone(): Boolean {
    return blocks.isEmpty()
}

internal fun BlockPlacer.resetState() {
    sneakTimes = 0
    blocks.clear()
    inaccessible.clear()
}
