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

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal fun startAutoDodgePacketHold(
    request: AutoDodgePacketRuntimeRequest,
    burst: AutoDodgePacketBurst,
    lease: AutoCloseable,
    preflight: (AutoDodgePacketBurst) -> AutoDodgePacketPreflightResult,
    sendPacket: (ServerboundMovePlayerPacket.Pos) -> Unit,
    updateState: (AutoDodgePacketRuntimeState) -> Unit,
): AutoDodgeActiveHold? {
    if (!acceptDistinctAutoDodgeEndpoint(burst, lease, updateState)) return null
    val preflightResult = try {
        preflight(burst)
    } catch (throwable: Throwable) {
        updateState(AutoDodgePacketRuntimeState.BURST_REJECTED)
        lease.close()
        throw throwable
    }
    if (preflightResult != AutoDodgePacketPreflightResult.READY) {
        updateState(preflightResult.runtimeState)
        lease.close()
        return null
    }

    updateState(AutoDodgePacketRuntimeState.SENDING_DESTINATION)
    try {
        sendPacket(burst.destinationPacket)
    } catch (throwable: Throwable) {
        updateState(AutoDodgePacketRuntimeState.SEND_FAILED)
        lease.close()
        throw throwable
    }

    return AutoDodgeActiveHold(
        burst = burst,
        threatKey = request.threatKey,
        predictedImpactTick = request.predictedImpactTick,
        holdUntilTick = request.returnNotBeforeTick,
        lease = lease,
    )
}

private fun acceptDistinctAutoDodgeEndpoint(
    burst: AutoDodgePacketBurst,
    lease: AutoCloseable,
    updateState: (AutoDodgePacketRuntimeState) -> Unit,
): Boolean {
    if (burst.origin.position != burst.destination.position) return true
    updateState(AutoDodgePacketRuntimeState.BURST_REJECTED)
    lease.close()
    return false
}
