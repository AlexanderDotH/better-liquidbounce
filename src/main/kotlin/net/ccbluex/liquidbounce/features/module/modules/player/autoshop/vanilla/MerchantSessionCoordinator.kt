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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

internal sealed interface MerchantSessionState {
    data object Idle : MerchantSessionState
    data class Rotating(val targetId: Int, val sinceTick: Int) : MerchantSessionState
    data class Opening(
        val targetId: Int,
        val sinceTick: Int,
        val expectedContainerId: Int? = null,
    ) : MerchantSessionState
    data class AwaitingOffers(val targetId: Int, val containerId: Int, val sinceTick: Int) : MerchantSessionState
    data class Trading(val targetId: Int, val containerId: Int, val sinceTick: Int) : MerchantSessionState
}

internal class MerchantSessionCoordinator(
    private val openTimeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
    private val offersTimeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
    private val retryTicks: Int = DEFAULT_RETRY_TICKS,
) {

    private val retryAt = mutableMapOf<Int, Int>()

    var state: MerchantSessionState = MerchantSessionState.Idle
        private set

    val targetId: Int?
        get() = when (val current = state) {
            MerchantSessionState.Idle -> null
            is MerchantSessionState.Rotating -> current.targetId
            is MerchantSessionState.Opening -> current.targetId
            is MerchantSessionState.AwaitingOffers -> current.targetId
            is MerchantSessionState.Trading -> current.targetId
        }

    fun tryLock(targetId: Int, tick: Int): Boolean {
        if (state !== MerchantSessionState.Idle || !canRetry(targetId, tick)) {
            return false
        }

        state = MerchantSessionState.Rotating(targetId, tick)
        return true
    }

    fun markInteractionSent(targetId: Int, tick: Int): Boolean {
        val rotating = state as? MerchantSessionState.Rotating ?: return false
        if (rotating.targetId != targetId) {
            return false
        }

        state = MerchantSessionState.Opening(targetId, tick)
        return true
    }

    fun claimMerchantScreen(containerId: Int, tick: Int): Boolean {
        val opening = state as? MerchantSessionState.Opening ?: return false
        if (opening.expectedContainerId != containerId || hasExpired(opening.sinceTick, tick, openTimeoutTicks)) {
            return false
        }

        state = MerchantSessionState.AwaitingOffers(opening.targetId, containerId, tick)
        return true
    }

    fun expectMerchantContainer(containerId: Int, tick: Int): Boolean {
        val opening = state as? MerchantSessionState.Opening ?: return false
        if (opening.expectedContainerId != null || hasExpired(opening.sinceTick, tick, openTimeoutTicks)) {
            return false
        }

        state = opening.copy(expectedContainerId = containerId)
        return true
    }

    fun markOffersReady(containerId: Int, tick: Int): Boolean {
        val awaiting = state as? MerchantSessionState.AwaitingOffers ?: return false
        if (awaiting.containerId != containerId) {
            return false
        }

        state = MerchantSessionState.Trading(awaiting.targetId, containerId, tick)
        return true
    }

    fun canRetry(targetId: Int, tick: Int): Boolean = tick >= retryAt.getOrDefault(targetId, Int.MIN_VALUE)

    fun isOwnedContainer(containerId: Int): Boolean = when (val current = state) {
        is MerchantSessionState.AwaitingOffers -> current.containerId == containerId
        is MerchantSessionState.Trading -> current.containerId == containerId
        else -> false
    }

    fun hasTimedOut(tick: Int): Boolean = when (val current = state) {
        is MerchantSessionState.Opening -> hasExpired(current.sinceTick, tick, openTimeoutTicks)
        is MerchantSessionState.AwaitingOffers -> hasExpired(current.sinceTick, tick, offersTimeoutTicks)
        else -> false
    }

    fun finish(tick: Int): Int? {
        val finishedTargetId = targetId ?: return null
        retryAt[finishedTargetId] = tick + retryTicks
        state = MerchantSessionState.Idle
        return finishedTargetId
    }

    fun resetAll() {
        state = MerchantSessionState.Idle
        retryAt.clear()
    }

    private fun hasExpired(sinceTick: Int, tick: Int, timeoutTicks: Int) = tick - sinceTick >= timeoutTicks

    companion object {
        const val DEFAULT_RETRY_TICKS = 20
        const val DEFAULT_TIMEOUT_TICKS = 20
    }
}
