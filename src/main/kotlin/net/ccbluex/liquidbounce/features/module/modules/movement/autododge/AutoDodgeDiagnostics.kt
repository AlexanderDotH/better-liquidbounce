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

import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput

internal object AutoDodgeDiagnostics {

    fun update(defense: AutoDodgeDefenseRuntime, shield: AutoDodgeShieldRuntime) {
        updateSpear(defense)
        updateShield(shield)
        updateMace(defense)
        updatePacket(defense)
    }

    private fun updateSpear(defense: AutoDodgeDefenseRuntime) {
        ModuleAutoDodge.debugParameter("Spear/Threat") {
            defense.primarySpearThreat?.let { "${it.candidate.name}/${it.kind}/${it.response}" } ?: "-"
        }
        ModuleAutoDodge.debugParameter("Spear/CommittedInput") {
            defense.spearJukeDecision?.plan?.input ?: DirectionalInput.NONE
        }
        ModuleAutoDodge.debugParameter("Spear/CommittedTicks") { defense.spearJukeDecision?.ticksRemaining ?: 0 }
        ModuleAutoDodge.debugParameter("Spear/TeleportState") { defense.spearTeleportState.debugName }
        ModuleAutoDodge.debugParameter("Spear/TeleportDestination") { defense.spearTeleportPlan?.destination ?: "-" }
    }

    private fun updateShield(shield: AutoDodgeShieldRuntime) {
        ModuleAutoDodge.debugParameter("Spear/ShieldState") { shield.stateName() }
        ModuleAutoDodge.debugParameter("Spear/BlockReadyTick") { shield.blockReadyAtTick() ?: "-" }
        ModuleAutoDodge.debugParameter("Spear/ShieldReady") { shield.isReady() }
        ModuleAutoDodge.debugParameter("Spear/OffhandReservation") { shield.reservationName() }
    }

    private fun updateMace(defense: AutoDodgeDefenseRuntime) {
        ModuleAutoDodge.debugParameter("Mace/Threat") {
            defense.primaryMaceThreat?.let { "${it.candidate.name}/${it.kind}" } ?: "-"
        }
        ModuleAutoDodge.debugParameter("Mace/TeleportState") { defense.maceTeleportState.debugName }
        ModuleAutoDodge.debugParameter("Mace/TeleportDestination") { defense.maceTeleportPlan?.destination ?: "-" }
    }

    private fun updatePacket(defense: AutoDodgeDefenseRuntime) {
        ModuleAutoDodge.debugParameter("Packet/State") { defense.packetDebug.state.debugName }
        ModuleAutoDodge.debugParameter("Packet/Threat") { defense.packetDebug.selectedThreat ?: "-" }
        ModuleAutoDodge.debugParameter("Packet/Destination") { defense.packetDebug.destination ?: "-" }
        ModuleAutoDodge.debugParameter("Packet/PredictedImpactTick") { defense.packetDebug.predictedImpactTick ?: "-" }
        ModuleAutoDodge.debugParameter("Packet/DodgeAtTick") { defense.packetDebug.dodgeAtTick ?: "-" }
        ModuleAutoDodge.debugParameter("Packet/HoldUntilTick") { defense.packetDebug.holdUntilTick ?: "-" }
        ModuleAutoDodge.debugParameter("Packet/LastSuccessTick") { defense.packetDebug.lastSuccessfulBurstTick ?: "-" }
        ModuleAutoDodge.debugParameter("Packet/LastSuccessDestination") {
            defense.packetDebug.lastSuccessfulDestination ?: "-"
        }
    }
}
