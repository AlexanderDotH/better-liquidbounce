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
package net.ccbluex.liquidbounce.features.module.modules.render.xray.contract

import net.minecraft.world.level.block.Block

internal class XRayCommandActions(
    val blocks: () -> Collection<Block>,
    val add: (Block) -> Boolean,
    val remove: (Block) -> Boolean,
    val clear: () -> Unit,
    val reset: () -> Unit,
)

internal object XRayCommandBridge {

    private fun unavailable(): Nothing = error("XRay command actions have not been installed")

    private val unavailableActions = XRayCommandActions(
        blocks = { unavailable() },
        add = { unavailable() },
        remove = { unavailable() },
        clear = { unavailable() },
        reset = { unavailable() },
    )

    private var actions: XRayCommandActions = unavailableActions

    fun install(actions: XRayCommandActions) {
        this.actions = actions
    }

    fun blocks(): Collection<Block> = actions.blocks()

    fun add(block: Block): Boolean = actions.add(block)

    fun remove(block: Block): Boolean = actions.remove(block)

    fun clear() = actions.clear()

    fun reset() = actions.reset()

    fun <T> withActionsForTest(actions: XRayCommandActions, block: () -> T): T {
        val previousActions = this.actions
        this.actions = actions
        return try {
            block()
        } finally {
            this.actions = previousActions
        }
    }
}
