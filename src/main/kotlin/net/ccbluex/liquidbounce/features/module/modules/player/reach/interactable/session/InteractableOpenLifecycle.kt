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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session

internal sealed interface InteractableOpenLifecycleAction {
    data class Confirm(val containerId: Int) : InteractableOpenLifecycleAction
    data class CloseUnexpected(val containerId: Int) : InteractableOpenLifecycleAction
    data class CloseAndAbort(val containerId: Int) : InteractableOpenLifecycleAction
}

/** Tracks a server menu fact until vanilla has installed the matching client menu and screen. */
internal class InteractableOpenLifecycle(
    private val confirmationGraceTicks: Int,
) {
    private data class PendingOpen(val containerId: Int, val observedTick: Int)

    private var pending: PendingOpen? = null

    val awaitingConfirmation: Boolean
        get() = pending != null

    init {
        require(confirmationGraceTicks >= 0) { "Open confirmation grace must not be negative" }
    }

    fun observe(
        containerId: Int,
        tick: Int,
        disposition: InteractablePacketDisposition,
        opening: Boolean,
    ): InteractableOpenLifecycleAction? {
        if (!opening || pending != null) return InteractableOpenLifecycleAction.CloseUnexpected(containerId)
        if (disposition != InteractablePacketDisposition.DELIVERED) {
            return InteractableOpenLifecycleAction.CloseAndAbort(containerId)
        }
        pending = PendingOpen(containerId, tick)
        return null
    }

    fun evaluate(
        tick: Int,
        screenContainerId: Int?,
        playerMenuId: Int?,
    ): InteractableOpenLifecycleAction? {
        val current = pending ?: return null
        if (screenContainerId == current.containerId && playerMenuId == current.containerId) {
            pending = null
            return InteractableOpenLifecycleAction.Confirm(current.containerId)
        }
        if (tick.toLong() - current.observedTick.toLong() < confirmationGraceTicks) return null
        pending = null
        return InteractableOpenLifecycleAction.CloseAndAbort(current.containerId)
    }

    fun clear() {
        pending = null
    }
}
