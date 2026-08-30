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
@file:Suppress("WildcardImport")

package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldCommand
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldInventoryLayout
internal data class AutoDodgeRuntimeContext(
    val blinkActive: Boolean = false,
    val inventoryBlocked: Boolean = false,
    val scaffoldBlocked: Boolean = false,
    val usingItem: Boolean = false,
    val allowWhileUsingItem: Boolean = false,
    val murderMysteryDisallowsProjectile: Boolean = false,
    val cleanupPending: Boolean = false,
)

internal data class AutoDodgeBranchAvailability(
    val projectile: Boolean,
    val spear: Boolean,
    val cleanup: Boolean,
)

internal fun resolveAutoDodgeBranchAvailability(context: AutoDodgeRuntimeContext): AutoDodgeBranchAvailability {
    val commonAvailable = !context.blinkActive && !context.inventoryBlocked && !context.scaffoldBlocked
    return AutoDodgeBranchAvailability(
        projectile = commonAvailable && !context.murderMysteryDisallowsProjectile &&
            (!context.usingItem || context.allowWhileUsingItem),
        spear = commonAvailable,
        cleanup = context.cleanupPending,
    )
}

internal fun shouldRunAutoDodgeHandlers(
    moduleRunning: Boolean,
    shieldCleanupPending: Boolean,
): Boolean = moduleRunning || shieldCleanupPending

internal fun canScheduleSpearShieldInventoryCommand(
    command: SpearShieldCommand<*>,
    layout: SpearShieldInventoryLayout,
    reservedByModule: Boolean,
): Boolean {
    if (!reservedByModule) {
        return false
    }

    return when (command) {
        is SpearShieldCommand.SwapIntoOffhand -> layout == SpearShieldInventoryLayout.ORIGINAL
        is SpearShieldCommand.RestoreOffhand -> layout == SpearShieldInventoryLayout.EQUIPPED ||
            layout == SpearShieldInventoryLayout.SHIELD_BROKEN
        else -> false
    }
}

internal inline fun collectSpearMovementSimulation(
    tickCount: Int = SpearDodgePlanner.SIMULATION_TICKS,
    tick: () -> Unit,
    sample: () -> SpearMovementSample,
): SpearMovementSimulation {
    val samples = ArrayList<SpearMovementSample>(tickCount)
    repeat(tickCount) {
        tick()
        samples += sample()
    }
    return SpearMovementSimulation(samples)
}
