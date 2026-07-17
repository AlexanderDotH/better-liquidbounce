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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.detector

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

/**
 * Fly mode tuned to slip past the client-side `ModulePlayerCheatDetector`
 * checks (ObservedMovementPrediction, ObservedFlight, ObservedGroundSpoof).
 *
 * Note: PlayerCheatDetector only samples `RemotePlayer` entities and explicitly
 * skips the local player, so on the flyer's own client any fly mode already
 * goes undetected. This mode is shaped so that another client running the
 * detector — where this player appears as a RemotePlayer — also sees no flags:
 *
 *  - Horizontal speed is clamped below the detector's `horizontalLimit`
 *    (CONSERVATIVE = 1.05, +0.12 while sprinting), defeating
 *    `ObservedMovementPredictionCheck`.
 *  - Vertical delta alternates sign each tick ("bob") so `abs(delta.y)` is
 *    never inside the < 0.01 dead band while moving horizontally, which breaks
 *    the `ObservedFlightCheck` hover accumulator before it reaches `hoverTicks`.
 *  - Upward delta is clamped below `upwardLimit` (CONSERVATIVE = 0.90).
 *  - Ground is never spoofed while airborne — outgoing move packets report
 *    `onGround = false` when the player is not actually touching a block,
 *    defeating `ObservedGroundSpoofSymptomsCheck`.
 *
 * Thresholds mirror `DetectorStrictness` in
 * `modules.misc.playercheatdetector.DetectionModel`; change [targetStrictness]
 * to tighten or loosen the envelope.
 */
internal object FlyDetectorBypass : Mode("DetectorBypass") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    private enum class TargetStrictness(
        override val tag: String,
        val horizontalLimit: Double,
        val upwardLimit: Double,
        val hoverTicks: Int,
    ) : Tagged {
        CONSERVATIVE("Conservative", horizontalLimit = 1.05, upwardLimit = 0.90, hoverTicks = 8),
        NORMAL("Normal", horizontalLimit = 0.85, upwardLimit = 0.75, hoverTicks = 6),
        STRICT("Strict", horizontalLimit = 0.70, upwardLimit = 0.65, hoverTicks = 4),
    }

    private val targetStrictness by enumChoice("TargetStrictness", TargetStrictness.CONSERVATIVE)

    /** Safety margin kept below the detector's horizontalLimit (sprint adds +0.12). */
    private val safetyMargin by float("SafetyMargin", 0.15f, 0.02f..0.5f)

    private val horizontalSpeed by float("HorizontalSpeed", 0.6f, 0.1f..2f)
    private val verticalSpeed by float("VerticalSpeed", 0.5f, 0.05f..2f)

    /** Per-tick vertical oscillation; must stay >= 0.02 to leave the < 0.01 dead band. */
    private val bobAmplitude by float("BobAmplitude", 0.05f, 0.02f..0.2f)

    private val glide by float("Glide", 0.0f, -1f..1f)

    /** Periodic real downward nudge to break hover chains and placate vanilla fly checks. */
    private val antiKick by boolean("AntiKick", true)
    private val antiKickInterval by int("AntiKickInterval", 40, 5..200, "ticks")
    private val antiKickDip by float("AntiKickDip", 0.04f, 0.01f..0.2f)

    private var tickPhase = 0

    override fun enable() {
        tickPhase = 0
        super.enable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        tickPhase++

        val sprintBonus = if (player.isSprinting) 0.12 else 0.0
        val maxHorizontal = (targetStrictness.horizontalLimit + sprintBonus - safetyMargin)
            .coerceAtLeast(0.05)
        val maxUpward = (targetStrictness.upwardLimit - safetyMargin)
            .coerceAtLeast(0.05)

        // Horizontal movement, clamped under the detector's horizontalLimit.
        val effectiveHorizontal = horizontalSpeed.toDouble().coerceAtMost(maxHorizontal)
        player.deltaMovement = player.deltaMovement.withStrafe(speed = effectiveHorizontal)

        // Vertical bob: alternating sign each tick keeps |dy| >= bobAmplitude
        // on every sampled tick, so ObservedFlightCheck's hover accumulator
        // never reaches hoverTicks while horizontalSpeed > 0.08.
        val bob = if (tickPhase % 2 == 0) bobAmplitude.toDouble() else -bobAmplitude.toDouble()

        val rawVertical = when {
            mc.options.keyJump.isDown -> verticalSpeed.toDouble()
            mc.options.keyShift.isDown -> (-verticalSpeed).toDouble()
            else -> glide.toDouble()
        }.coerceIn(-maxUpward, maxUpward)

        // Coerce the final dy under upwardLimit so the prediction check
        // (delta.y > upwardLimit && !nearGround) never fires.
        player.deltaMovement.y = (rawVertical + bob).coerceIn(-maxUpward, maxUpward)

        // Anti-kick: a real downward dip while cruising (no ground spoof).
        // Only applied when the user is not actively ascending/descending.
        val cruising = !mc.options.keyJump.isDown && !mc.options.keyShift.isDown
        if (antiKick && cruising && tickPhase % antiKickInterval == 0) {
            player.deltaMovement.y = -antiKickDip.toDouble()
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        // Never let an outgoing move packet claim onGround while we are actually
        // airborne — that is exactly what ObservedGroundSpoofSymptomsCheck flags
        // (onGround=true && nearGround=false for >= 3 ticks). This trades vanilla
        // anti-kick bypass for detector stealth; the AntiKick real dip compensates.
        if (event.packet is ServerboundMovePlayerPacket && !player.onGround()) {
            event.packet.onGround = false
        }
    }
}
