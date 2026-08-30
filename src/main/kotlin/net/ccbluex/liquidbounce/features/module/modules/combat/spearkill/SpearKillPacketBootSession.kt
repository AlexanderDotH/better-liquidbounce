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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteSession
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import java.util.Collections
import java.util.IdentityHashMap

/** Keeps a fall-damage spoof bound to the exact SpearKill movement packet that carries it. */
internal class SpearKillFallDamagePacketTracker {

    private val protectedPackets = Collections.synchronizedMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Unit>(),
    )

    fun protect(packet: ServerboundMovePlayerPacket) {
        protectedPackets[packet] = Unit
        packet.onGround = true
    }

    /** Restores the owned ground bit after lower-priority packet objections changed it. */
    fun reassertGround(packet: ServerboundMovePlayerPacket): Boolean {
        if (!protectedPackets.containsKey(packet)) return false

        packet.onGround = true
        return true
    }

    fun confirmFinalState(packet: ServerboundMovePlayerPacket, cancelled: Boolean): Boolean {
        if (protectedPackets.remove(packet) == null) return false

        return !cancelled && packet.onGround
    }

    fun clear() {
        protectedPackets.clear()
    }
}

/**
 * Tracks SpearKill's packet displacement and confirmed physical return positions.
 * A movement is removed only after the corresponding packet passed the packet pipeline.
 */
internal class SpearKillPacketBootSession(
    internal val state: SpearKillPacketSessionPort,
) : RemoteKillRouteSession by state
