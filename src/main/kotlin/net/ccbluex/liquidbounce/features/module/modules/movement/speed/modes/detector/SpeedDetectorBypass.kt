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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.detector

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.utils.entity.horizontalSpeed
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

/**
 * Speed mode tuned to slip past the client-side `ModulePlayerCheatDetector`
 * checks, mirroring the envelope used by [FlyDetectorBypass].
 *
 * Same caveat as the fly bypass: PlayerCheatDetector only samples
 * `RemotePlayer` entities and skips the local player, so this is shaped for
 * the case where another client running the detector observes us.
 *
 * Strategy:
 *  - Ground-strafe bhop: jump each ground tick while moving, then strafe
 *    with a speed that is *clamped* below the detector's `horizontalLimit`
 *    (CONSERVATIVE = 1.05, +0.12 sprint) every tick, defeating
 *    `ObservedMovementPredictionCheck`.
 *  - Natural jump arc: the vanilla 0.42 jump + gravity produces a dy that
 *    changes sign and magnitude each tick, so `ObservedFlightCheck`'s hover
 *    accumulator (needs `abs(dy) < 0.01` for `hoverTicks` consecutive ticks)
 *    never accumulates.
 *  - No ground spoof: outgoing move packets report `onGround = false` while
 *    actually airborne, defeating `ObservedGroundSpoofSymptomsCheck`.
 *
 * Thresholds mirror `DetectorStrictness` in
 * `modules.misc.playercheatdetector.DetectionModel`.
 */
class SpeedDetectorBypass(override val parent: ModeValueGroup<*>) : Mode("DetectorBypass") {

    private enum class TargetStrictness(
        override val tag: String,
        val horizontalLimit: Double,
        val upwardLimit: Double,
    ) : Tagged {
        CONSERVATIVE("Conservative", horizontalLimit = 1.05, upwardLimit = 0.90),
        NORMAL("Normal", horizontalLimit = 0.85, upwardLimit = 0.75),
        STRICT("Strict", horizontalLimit = 0.70, upwardLimit = 0.65),
    }

    private val targetStrictness by enumChoice("TargetStrictness", TargetStrictness.CONSERVATIVE)

    /** Safety margin kept below the detector's horizontalLimit (sprint adds +0.12). */
    private val safetyMargin by float("SafetyMargin", 0.15f, 0.02f..0.5f)

    /** Desired strafe speed; will be clamped to the detector envelope. */
    private val strafeSpeed by float("StrafeSpeed", 0.5f, 0.1f..2f)

    /** Extra ground-strafe boost applied only while onGround (pre-jump), also clamped. */
    private val groundBoost by float("GroundBoost", 0.2f, 0f..1f)

    /** Pull-down applied while airborne and descending, to sharpen the jump arc (stays > 0.01). */
    private val pullDown by float("PullDown", 0.02f, 0f..0.2f)

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        // Auto-jump while moving on ground (bhop). Skip if optimisations want to wait.
        if (!player.onGround() || !event.directionalInput.isMoving) {
            return@handler
        }

        if (ModuleSpeed.doOptimizationsPreventJump()) {
            return@handler
        }

        event.jump = true
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!player.moving) {
            return@tickHandler
        }

        val sprintBonus = if (player.isSprinting) 0.12 else 0.0
        val maxHorizontal = (targetStrictness.horizontalLimit + sprintBonus - safetyMargin)
            .coerceAtLeast(0.05)

        // Apply strafe, then hard-clamp the resulting horizontal speed under
        // the detector envelope so ObservedMovementPredictionCheck never fires.
        val desired = if (player.onGround()) strafeSpeed + groundBoost else strafeSpeed
        player.deltaMovement = player.deltaMovement.withStrafe(speed = desired.toDouble())

        val hSpeed = player.horizontalSpeed
        if (hSpeed > maxHorizontal) {
            val scale = maxHorizontal / hSpeed
            val dm = player.deltaMovement
            player.deltaMovement = Vec3(dm.x * scale, dm.y, dm.z * scale)
        }

        // Sharpen the descending half of the jump arc so sampled dy keeps
        // changing (keeps ObservedFlightCheck's hover accumulator at 0).
        // pullDown is small enough that dy never flattens into the < 0.01 band.
        if (!player.onGround() && player.deltaMovement.y < 0.0) {
            player.deltaMovement.y -= pullDown.toDouble()
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        // Never let an outgoing move packet claim onGround while we are actually
        // airborne — ObservedGroundSpoofSymptomsCheck flags onGround=true with
        // nearGround=false for >= 3 ticks.
        if (event.packet is ServerboundMovePlayerPacket && !player.onGround()) {
            event.packet.onGround = false
        }
    }
}
