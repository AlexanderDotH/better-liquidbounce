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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.flyAutomationMoving
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.flyAutomationYaw
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.setReviveFlySpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.stopReviveFlySpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.sentinel.isSentinelOutgoingMovementPacket
import net.ccbluex.liquidbounce.features.network.sendPacketSilently
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.util.Mth
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Revive SentinelNoDown fly port.
 */
internal object FlySentinelNoDown : Mode("SentinelNoDown"), FlyAutomationProfile {

    private val horizontalSpeed by float("HorizontalSpeed", 0.4f, 0.1f..1f)

    private object FakeStrafe : ToggleableValueGroup(this@FlySentinelNoDown, "FakeStrafe", true) {
        val angle by float("Angle", 35f, 0f..60f, "degrees")
    }


    init {
        tree(FakeStrafe)
    }

    private var targetY = 0.0
    private var targetStart = 0.15
    private var changeY = false
    private var strafeRight = false

    private var packetRelease = TimeSource.Monotonic.markNow()
    private var packetDelay = nextPacketDelay()
    private var nextTargetGrow = TimeSource.Monotonic.markNow()
    private var nextDirectionFlip = TimeSource.Monotonic.markNow()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = false,
        descend = false,
        landing = false,
        kind = FlyAutomationKind.CONTINUOUS,
    )

    override fun automationReadiness(): FlyAutomationReadiness = FlyAutomationReadiness.Ready

    override fun enable() {
        targetY = 0.0
        targetStart = 0.15
        changeY = false
        strafeRight = false
        packetRelease = TimeSource.Monotonic.markNow()
        packetDelay = nextPacketDelay()
        nextTargetGrow = TimeSource.Monotonic.markNow()
        nextDirectionFlip = TimeSource.Monotonic.markNow()
        super.enable()
    }

    override fun disable() {
        player.stopReviveFlySpeed()
        sendPacketSilently(
            ServerboundMovePlayerPacket.PosRot(
                player.x,
                player.y,
                player.z,
                player.yRot,
                player.xRot,
                player.onGround(),
                player.horizontalCollision,
            )
        )
        network.send(ServerboundSetCarriedItemPacket(player.inventory.selectedSlot))
        super.disable()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (isSentinelOutgoingMovementPacket(event.origin, event.packet)) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.flyAutomationMoving()) {
            player.setReviveFlySpeed(horizontalSpeed.toDouble())
        }

        if (nextTargetGrow.elapsedNow() >= targetGrowDelay) {
            if (targetY <= targetStart) {
                targetY += 0.005
            }
            nextTargetGrow = TimeSource.Monotonic.markNow()
        }

        if (nextDirectionFlip.elapsedNow() >= directionFlipDelay) {
            changeY = !changeY
            nextDirectionFlip = TimeSource.Monotonic.markNow()
        }

        val y = if (changeY) player.y - targetY else player.y + targetY
        val pos = BlockPos.containing(player.x, y, player.z)

        if (packetRelease.elapsedNow() >= packetDelay) {
            if (world.getBlockState(pos).isAir) {
                sendPacketSilently(
                    ServerboundMovePlayerPacket.PosRot(
                        player.x,
                        y,
                        player.z,
                        sentinelServerYaw(
                            flyAutomationYaw(player.yRot),
                            FakeStrafe.running,
                            strafeRight,
                            FakeStrafe.angle,
                        ),
                        player.xRot,
                        true,
                        player.horizontalCollision,
                    )
                )
            }

            strafeRight = !strafeRight
            packetRelease = TimeSource.Monotonic.markNow()
            packetDelay = nextPacketDelay()
        }

        player.deltaMovement.y = 0.0
    }

    private fun nextPacketDelay() = Random.nextInt(MIN_PACKET_DELAY_MS, MAX_PACKET_DELAY_MS + 1).milliseconds

    private const val MIN_PACKET_DELAY_MS = 80
    private const val MAX_PACKET_DELAY_MS = 110

    private val targetGrowDelay = 70.milliseconds
    private val directionFlipDelay = 100.milliseconds

}

internal fun sentinelServerYaw(
    clientYaw: Float,
    fakeStrafe: Boolean,
    strafeRight: Boolean,
    angle: Float,
): Float {
    val offset = when {
        !fakeStrafe -> 0f
        strafeRight -> angle
        else -> -angle
    }

    return Mth.wrapDegrees(clientYaw + offset)
}
