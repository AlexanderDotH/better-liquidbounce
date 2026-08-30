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
package net.ccbluex.liquidbounce.features.inventory.runtime

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.ccbluex.liquidbounce.common.debug.DebugParameterSink
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryConstraintPolicy
import net.ccbluex.liquidbounce.utils.inventory.canCloseMainInventory
import net.ccbluex.liquidbounce.utils.inventory.isInInventoryScreen
import net.ccbluex.liquidbounce.utils.network.sendLegacyOpenInventory
import net.ccbluex.liquidbounce.utils.network.sendCloseInventory
import net.minecraft.world.inventory.ContainerInput
import kotlin.math.max
import kotlin.random.Random

internal class ActionScheduleExecutor(
    private val owner: EventListener,
    private val state: InventorySessionLedger,
) {

    private val isInventoryOpen
        get() = isInInventoryScreen || state.isServerSideOpen

    @Suppress("unused")
    private val repeatingSchedulerExecutor = owner.tickHandler {
        executeTick()
    }

    private suspend fun executeTick() {
        if (!inGame) {
            return
        }

        publishInventoryDebugState()
        val run = ScheduleRun()
        while (run.beginPass()) {
            state.beginSchedulingPass()
            val schedule = orderedRunnableSchedule(
                EventManager.callEvent(ScheduleInventoryActionEvent()).schedule,
            )
            if (schedule.isEmpty) {
                break
            }
            debugParameter("Schedule Size") { schedule.size }
            executeSchedule(schedule, run)
        }
        reportWatchdogFailure(run)
        closeInventoryAfterSchedule(run.maximumCloseDelay)
        state.finishScheduling()
    }

    private fun publishInventoryDebugState() {
        debugParameter("Inventory Open") { isInventoryOpen }
        debugParameter("Inventory Open Server Side") { state.isServerSideOpen }
        debugParameter("Cursor Stack") { player.containerMenu.carried }
    }

    private suspend fun executeSchedule(
        schedule: List<InventoryAction.Chain>,
        run: ScheduleRun,
    ) {
        for ((scheduleIndex, chain) in schedule.withIndex()) {
            if (state.requiresScheduleRefresh) {
                break
            }
            debugParameter("Schedule Index") { scheduleIndex }
            debugParameter("Action Size") { chain.actions.size }
            executeChain(chain, run)
        }
    }

    private suspend fun executeChain(chain: InventoryAction.Chain, run: ScheduleRun) {
        for ((index, action) in chain.actions.withIndex()) {
            debugParameter("Action Index") { index }
            if (!executeAction(chain, index, action, run)) {
                break
            }
        }
    }

    private suspend fun executeAction(
        chain: InventoryAction.Chain,
        index: Int,
        action: InventoryAction,
        run: ScheduleRun,
    ): Boolean {
        val constraints = chain.inventoryConstraints
        run.recordCloseDelay(constraints.closeDelay.random())
        consumeOpeningDelay(constraints, run)
        prepareInventoryFor(action, constraints, run)
        if (!chain.canPerformAction()) {
            logger.warn("Cannot perform action $action because it is not possible")
            return false
        }
        if (action is InventoryAction.Click &&
            shouldPerformMissClick(index, action, constraints) && action.performMissClick()
        ) {
            waitAndReset(constraints.clickDelay.random(), run)
        }
        if (action is InventoryAction.CloseScreen) {
            waitAndReset(constraints.closeDelay.random(), run)
        }
        if (action.performAction() && action !is InventoryAction.CloseScreen) {
            waitAndReset(constraints.clickDelay.random(), run)
        }
        return true
    }

    private suspend fun consumeOpeningDelay(
        constraints: InventoryConstraintPolicy,
        run: ScheduleRun,
    ) {
        if (state.consumeRecentOpening()) {
            waitAndReset(constraints.startDelay.random(), run)
        }
    }

    private suspend fun prepareInventoryFor(
        action: InventoryAction,
        constraints: InventoryConstraintPolicy,
        run: ScheduleRun,
    ) {
        if (action.requiresPlayerInventoryOpen()) {
            if (!isInventoryOpen) {
                network.sendLegacyOpenInventory()
                waitAndReset(constraints.startDelay.random(), run)
            }
            return
        }
        if (canCloseMainInventory && isInventoryOpen) {
            waitAndReset(constraints.closeDelay.random(), run)
            network.sendCloseInventory()
        }
    }

    private fun shouldPerformMissClick(
        index: Int,
        action: InventoryAction.Click,
        constraints: InventoryConstraintPolicy,
    ): Boolean {
        if (index != 0) {
            return false
        }
        return constraints.missChance.random() > Random.nextInt(100) &&
            action.actionType != ContainerInput.THROW
    }

    private suspend fun waitAndReset(delay: Int, run: ScheduleRun) {
        waitTicks(delay)
        run.resetWatchdog()
    }

    private suspend fun closeInventoryAfterSchedule(maximumCloseDelay: Int) {
        if (!isInventoryOpen || !canCloseMainInventory) {
            return
        }
        waitTicks(maximumCloseDelay)
        network.sendCloseInventory()
    }

    private fun reportWatchdogFailure(run: ScheduleRun) {
        if (!run.watchdogExceeded) {
            return
        }
        chat(
            "InventoryManager has been running for too long (${run.cycles} cycles) on tick, stopping now. " +
                "Please report this issue.",
        )
    }

    private fun debugParameter(name: String, value: () -> Any?) {
        DebugParameterSink.publish(owner, name, value)
    }
}

internal fun orderedRunnableSchedule(
    schedule: List<InventoryAction.Chain>,
    canPerform: (InventoryAction.Chain) -> Boolean = InventoryAction.Chain::canPerformAction,
): ObjectArrayList<InventoryAction.Chain> = schedule
    .filterTo(ObjectArrayList()) { chain -> canPerform(chain) && chain.actions.isNotEmpty() }
    .also { it.sortWith(ACTION_CHAIN_ORDER) }

private val ACTION_CHAIN_ORDER: Comparator<InventoryAction.Chain> =
    compareBy<InventoryAction.Chain> { it.requiresInventoryOpen() }
        .thenByDescending { it.priority }

private class ScheduleRun {
    var cycles = 0
        private set
    var maximumCloseDelay = 0
        private set
    var watchdogExceeded = false
        private set

    fun beginPass(): Boolean {
        cycles++
        watchdogExceeded = cycles > MAXIMUM_CYCLES
        return !watchdogExceeded
    }

    fun recordCloseDelay(delay: Int) {
        maximumCloseDelay = max(maximumCloseDelay, delay)
    }

    fun resetWatchdog() {
        cycles = 0
    }

    private companion object {
        const val MAXIMUM_CYCLES = 100
    }
}
