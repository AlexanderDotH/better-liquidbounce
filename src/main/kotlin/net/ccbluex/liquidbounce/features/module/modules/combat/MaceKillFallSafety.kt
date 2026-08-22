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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

internal data class MaceKillFallSafetyStep(
    val movement: Vec3,
    val grounded: Boolean,
    val groundSpoofed: Boolean = false,
)

internal sealed interface MaceKillFallSafetyPreflight {
    data object Safe : MaceKillFallSafetyPreflight

    data class UnsafeLanding(
        val stepIndex: Int,
        val fallDistance: Double,
    ) : MaceKillFallSafetyPreflight

    data object Invalid : MaceKillFallSafetyPreflight
}

internal enum class MaceKillFallSafetyFinishDecision {
    COMPLETE,
    WAIT_FOR_ROUTE_DELIVERY,
    SEND_GROUNDING,
    RESET_LOCAL_FALL_DISTANCE,
}

/**
 * An airborne origin is safe when the immutable packet contract returns to that exact origin.
 * No synthetic ground is needed: the route retains the incoming fall state and finalization leaves
 * an airborne origin airborne so NoFall or the player's movement mode can resume normally.
 */
internal fun canBeginMaceKillFallSafetyAtOrigin(
    originNearGround: Boolean,
    routeReturnsExactly: Boolean,
): Boolean = originNearGround || routeReturnsExactly

internal fun decideMaceKillFallSafetyFinish(
    lifecycle: SpearKillFallSafetyLifecycle,
    finalPositionKnown: Boolean,
    connectionOpen: Boolean,
    nearGround: Boolean,
): MaceKillFallSafetyFinishDecision {
    val action = lifecycle.finish(finalPositionKnown, connectionOpen, nearGround)
    return when {
        action.resetLocalFallDistance -> MaceKillFallSafetyFinishDecision.RESET_LOCAL_FALL_DISTANCE
        action.sendGroundedPacket -> MaceKillFallSafetyFinishDecision.SEND_GROUNDING
        lifecycle.active -> MaceKillFallSafetyFinishDecision.WAIT_FOR_ROUTE_DELIVERY
        else -> MaceKillFallSafetyFinishDecision.COMPLETE
    }
}

internal enum class MaceKillGroundingPacketResolution {
    UNRELATED,
    DELIVERED,
    REJECTED,
}

/** Identity boundary for the one final ground packet requested by MaceKill fall safety. */
internal class MaceKillGroundingPacketTracker {
    private var pendingPacket: ServerboundMovePlayerPacket? = null

    val pendingCount: Int
        get() = if (pendingPacket == null) 0 else 1

    fun protect(packet: ServerboundMovePlayerPacket) {
        check(pendingPacket == null) { "A MaceKill grounding packet is already pending" }
        pendingPacket = packet
        packet.onGround = true
    }

    fun reassertGround(packet: ServerboundMovePlayerPacket): Boolean {
        if (pendingPacket !== packet) return false
        packet.onGround = true
        return true
    }

    fun resolve(
        packet: ServerboundMovePlayerPacket,
        cancelled: Boolean,
        queued: Boolean,
    ): MaceKillGroundingPacketResolution {
        if (pendingPacket !== packet) return MaceKillGroundingPacketResolution.UNRELATED
        pendingPacket = null
        return if (!cancelled && !queued && packet.onGround) {
            MaceKillGroundingPacketResolution.DELIVERED
        } else {
            MaceKillGroundingPacketResolution.REJECTED
        }
    }

    fun discard(packet: ServerboundMovePlayerPacket): Boolean {
        if (pendingPacket !== packet) return false
        pendingPacket = null
        return true
    }

    fun clear() {
        pendingPacket = null
    }
}

/**
 * Rejects collision-derived landings that would commit a damaging server-side fall state.
 *
 * Collision-derived routes never manufacture intermediate ground packets. The explicit
 * experimental ClipReach policy marks only its identity-owned anchor packets as spoofed ground;
 * those packets reset the modeled server fall state without leaking that exception to other routes.
 */
internal fun preflightMaceKillFallSafety(
    initialFallDistance: Double,
    safeFallDistance: Double,
    steps: List<MaceKillFallSafetyStep>,
): MaceKillFallSafetyPreflight {
    if (!initialFallDistance.isFinite() || initialFallDistance < 0.0 ||
        !safeFallDistance.isFinite() || safeFallDistance < 0.0 ||
        steps.any { !it.movement.isFinite() }
    ) {
        return MaceKillFallSafetyPreflight.Invalid
    }

    var fallDistance = initialFallDistance
    for ((index, step) in steps.withIndex()) {
        if (step.groundSpoofed) {
            // Identity-owned Instant anchors intentionally use the same airborne reset as Packet NoFall.
            fallDistance = 0.0
            continue
        }
        fallDistance = projectedMaceKillFallDistance(fallDistance, step.movement)
        if (step.grounded) {
            if (fallDistance > safeFallDistance) {
                return MaceKillFallSafetyPreflight.UnsafeLanding(index, fallDistance)
            }
            fallDistance = 0.0
        }
    }
    return MaceKillFallSafetyPreflight.Safe
}

internal fun projectedMaceKillFallDistance(
    confirmedFallDistance: Double,
    movement: Vec3,
): Double = when {
    movement.y < 0.0 -> confirmedFallDistance - movement.y
    else -> confirmedFallDistance
}
