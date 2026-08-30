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

import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportPlan

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.once
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput

internal object AutoDodgeMovementExecutor : MinecraftShortcuts {

    fun execute(
        event: MovementInputEvent,
        action: AutoDodgeMovementAction,
        spearMovement: SpearMovementResult,
        performSpearTeleport: (SpearTeleportPlan) -> Boolean,
        performMaceTeleport: (SpearTeleportPlan) -> Boolean,
    ) {
        val dodgePlan = when (action) {
            is AutoDodgeMovementAction.Dodge -> action.plan
            is AutoDodgeMovementAction.Teleport -> executeTeleport(
                event,
                action,
                spearMovement,
                performSpearTeleport,
                performMaceTeleport,
            )
            AutoDodgeMovementAction.None -> null
        } ?: return

        applyDodgePlan(event, dodgePlan)
    }

    private fun executeTeleport(
        event: MovementInputEvent,
        action: AutoDodgeMovementAction.Teleport,
        spearMovement: SpearMovementResult,
        performSpearTeleport: (SpearTeleportPlan) -> Boolean,
        performMaceTeleport: (SpearTeleportPlan) -> Boolean,
    ): DodgePlan? {
        val executed = when (action.defense) {
            AutoDodgeTeleportDefense.SPEAR -> performSpearTeleport(action.plan)
            AutoDodgeTeleportDefense.MACE -> performMaceTeleport(action.plan)
        }
        if (!executed) return spearMovement.jukePlan?.asDodgePlan()

        event.directionalInput = DirectionalInput.NONE
        return null
    }

    private fun applyDodgePlan(event: MovementInputEvent, dodgePlan: DodgePlan) {
        event.directionalInput = dodgePlan.directionalInput
        dodgePlan.yawChange?.let { player.yRot = it }
        if (dodgePlan.shouldJump && AllowRotationChange.allowJump && player.onGround()) {
            ModuleAutoDodge.once<MovementInputEvent> { it.jump = true }
        }
        if (AllowTimer.enabled && dodgePlan.useTimer) {
            Timer.requestTimerSpeed(
                AllowTimer.timerSpeed,
                Priority.IMPORTANT_FOR_PLAYER_LIFE,
                ModuleAutoDodge,
            )
        }
    }
}
