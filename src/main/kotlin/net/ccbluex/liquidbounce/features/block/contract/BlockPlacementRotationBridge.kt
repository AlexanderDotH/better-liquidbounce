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
package net.ccbluex.liquidbounce.features.block.contract

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.aiming.RotationTargetFactory

data class BlockPlacementRotationSettings(
    val valueGroup: ValueGroup,
    val targetFactory: RotationTargetFactory,
)

interface BlockPlacementRotationProvider {
    fun createSettings(owner: EventListener): BlockPlacementRotationSettings

    fun schedule(
        owner: EventListener,
        postMove: Boolean,
        priority: Boolean,
        task: Runnable,
    )
}

object BlockPlacementRotationBridge {

    @Volatile
    private var provider: BlockPlacementRotationProvider? = null

    @Synchronized
    fun install(provider: BlockPlacementRotationProvider) {
        check(this.provider == null) { "Block placement rotation provider is already installed" }
        this.provider = provider
    }

    fun createSettings(owner: EventListener): BlockPlacementRotationSettings =
        requireProvider().createSettings(owner)

    fun schedule(
        owner: EventListener,
        postMove: Boolean,
        priority: Boolean = false,
        task: Runnable,
    ) = requireProvider().schedule(owner, postMove, priority, task)

    private fun requireProvider(): BlockPlacementRotationProvider =
        checkNotNull(provider) { "Block placement rotation provider is not installed" }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: BlockPlacementRotationProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
