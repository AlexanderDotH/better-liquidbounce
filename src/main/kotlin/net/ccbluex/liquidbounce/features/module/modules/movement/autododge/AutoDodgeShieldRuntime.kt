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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldAcquisition
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldCommand
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldController
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldObservation
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldState
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldTransition
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.shouldPreserveAutoDodgeShieldUse
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.shouldSuppressAutoDodgeVanillaUse
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.minecraft.world.item.ItemStack

internal class AutoDodgeShieldRuntime(warn: (String) -> Unit) : MinecraftShortcuts {

    private val equipment = AutoDodgeShieldEquipment()
    private val commands = AutoDodgeShieldCommandRuntime(equipment, warn)

    var state: SpearShieldState<ItemStack> = SpearShieldState.Idle
        private set

    val cleanupPending: Boolean
        get() = state !is SpearShieldState.Idle && state !is SpearShieldState.Aborted

    fun ownsShieldUse() = shouldPreserveAutoDodgeShieldUse(state)

    fun suppressesVanillaShieldUse() = shouldSuppressAutoDodgeVanillaUse(state)

    fun update(canStartDefense: Boolean, moduleEnabled: Boolean, primaryThreat: SpearThreat?) {
        val threat = primaryThreat.takeIf { canStartDefense && Spear.enabled && Spear.Shield.enabled }
        val selection = selectRoute(threat)
        val policy = state.sessionOrNull()?.policy ?: selection?.policy
        val aligned = threat != null && policy?.let { equipment.isAligned(it, threat) } == true
        val observation = equipment.observation(state, threat != null, aligned)
        val transition = selectTransition(moduleEnabled, threat, selection, aligned, observation)
            ?: return
        applyTransition(transition)
    }

    private fun selectRoute(threat: SpearThreat?) = if (
        state is SpearShieldState.Idle || state is SpearShieldState.Aborted
    ) {
        threat?.let { equipment.findRoute() }
    } else {
        null
    }

    private fun selectTransition(
        moduleEnabled: Boolean,
        threat: SpearThreat?,
        selection: AutoDodgeShieldRouteSelection?,
        aligned: Boolean,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<ItemStack>? {
        if (!moduleEnabled || !Spear.enabled || !Spear.Shield.enabled) {
            return SpearShieldController.disable(state, observation)
        }
        if (selection == null || threat == null || !aligned || equipment.isShieldUseActive()) {
            return SpearShieldController.update(state, observation)
        }
        if (selection.route.needsOffhandReservation() && !reserveOffhand()) return null
        return SpearShieldController.acquire(
            state,
            SpearShieldAcquisition(
                tick = player.tickCount.toLong(),
                aligned = true,
                route = selection.route,
                usingItem = player.isUsingItem,
                usingShield = false,
                useKeyDown = mc.options.keyUse.isPressedOnAny,
                policy = selection.policy,
            ),
        )
    }

    private fun reserveOffhand(): Boolean {
        val transition = SpearShieldTransition<ItemStack>(
            state,
            listOf(SpearShieldCommand.ReserveOffhand),
        )
        return transition.commands.all(commands::execute)
    }

    private fun applyTransition(transition: SpearShieldTransition<ItemStack>) {
        state = transition.state
        commands.clearPendingWhenTerminal(state)
        for (command in transition.commands) {
            if (!commands.execute(command)) break
        }
        commands.renewOrRelease(state, stateName())
    }

    fun schedulePending(event: ScheduleInventoryActionEvent) = commands.schedulePending(state, event)

    fun disable() {
        if (mc.player == null) {
            worldReset()
            return
        }
        applyTransition(
            SpearShieldController.disable(
                state,
                equipment.observation(state, threatPresent = false, aligned = false),
            )
        )
    }

    fun worldReset() {
        state = SpearShieldController.worldReset<ItemStack>().state
        commands.reset()
    }

    fun stateName(): String = when (val current = state) {
        SpearShieldState.Idle -> "Idle"
        is SpearShieldState.Interrupting -> "Interrupting"
        is SpearShieldState.Equipping -> "Equipping"
        is SpearShieldState.Blocking -> "Blocking"
        is SpearShieldState.LoweredAwaitingRestore -> "LoweredAwaitingRestore"
        is SpearShieldState.Restoring -> "Restoring"
        is SpearShieldState.Aborted -> "Aborted/${current.reason}"
    }

    fun blockReadyAtTick() = (state as? SpearShieldState.Blocking)?.blockReadyAtTick

    fun isReady(): Boolean {
        val blocking = state as? SpearShieldState.Blocking ?: return false
        return equipment.isShieldUseActive() && blocking.session.policy.isReady(player.ticksUsingItem)
    }

    fun reservationName() = commands.reservationName()
}
