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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

internal data class VClipFallSafetyContext(
    val initialFallDistance: Double,
    val safeFallDistance: Double,
)

/** Pure input for a reusable VClip transport plan. */
internal data class VClipTransportRequest(
    val origin: VClipPosition,
    val target: VClipPosition,
    val fallSafety: VClipFallSafetyContext,
)

/**
 * Immutable packet-planning configuration shared by VClip consumers.
 *
 * Local entity movement and motion resets deliberately stay outside this contract.
 */
internal sealed interface VClipTransportProfile {
    fun plan(request: VClipTransportRequest): VClipPacketPlanResult
}

internal data class VClipVanillaProfile(
    val paperBypass: Boolean = false,
    val fullPacket: Boolean = false,
) : VClipTransportProfile {

    override fun plan(request: VClipTransportRequest) = VClipPacketPlanner.vanilla(
        origin = request.origin,
        target = request.target,
        paperBypass = paperBypass,
        fullPacket = fullPacket,
        initialFallDistance = request.fallSafety.initialFallDistance,
        safeFallDistance = request.fallSafety.safeFallDistance,
    )
}

internal data class VClipFoliaProfile(
    val movementPackets: Int = DEFAULT_MOVEMENT_PACKETS,
    val fullPacket: Boolean = false,
) : VClipTransportProfile {

    init {
        require(movementPackets in MOVEMENT_PACKETS_RANGE) {
            "Folia movement packets must stay within the researched $MOVEMENT_PACKETS_RANGE window"
        }
    }

    override fun plan(request: VClipTransportRequest) = VClipPacketPlanner.folia(
        origin = request.origin,
        target = request.target,
        movementPackets = movementPackets,
        fullPacket = fullPacket,
        initialFallDistance = request.fallSafety.initialFallDistance,
        safeFallDistance = request.fallSafety.safeFallDistance,
    )

    internal companion object {
        const val DEFAULT_MOVEMENT_PACKETS = 5
        val MOVEMENT_PACKETS_RANGE: IntRange = 1..5
    }
}
