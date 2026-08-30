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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.movement.freeze.FreezeCancel
import net.ccbluex.liquidbounce.features.module.modules.movement.freeze.FreezeQueue
import net.ccbluex.liquidbounce.features.module.modules.movement.freeze.FreezeStationary
import net.ccbluex.liquidbounce.features.module.modules.movement.freeze.FreezeTickMovement
import net.ccbluex.liquidbounce.features.module.modules.movement.freeze.contract.FreezeStateHook
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.utils.MutableVertexList
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayerCache
import net.ccbluex.liquidbounce.utils.entity.anyHorizontal
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import java.util.function.BooleanSupplier

/**
 * Freeze module
 *
 * Allows you to freeze yourself without the server knowing.
 */
object ModuleFreeze : ClientModule("Freeze", ModuleCategories.MOVEMENT, disableOnQuit = true) {

    internal val modes = choices(
        "Mode",
        FreezeStationary,
        arrayOf(FreezeQueue, FreezeCancel, FreezeStationary, FreezeTickMovement),
    )
        .apply { tagBy(this) }
    private val disableOn by multiEnumChoice("DisableOn", enumSetOf(DisableOn.Flag))
    private val notification by boolean("Notification", false)
    private val balance by boolean("BalanceWarp", false)

    private enum class DisableOn(
        override val tag: String,
        val trigger: BooleanSupplier?,
    ) : Tagged {
        Flag("Flag", null),
        OnGround("OnGround", { player.onGround() }),
        OnMovementInput("OnMovementInput", { player.input.keyPresses.anyHorizontal }),
        InLiquid("InLiquid", { player.isInLiquid }),
        Void("Void", { player.y <= player.level().minY }),
        OnUseItem("OnUseItem", { player.isUsingItem }),
    }

    // todo: use global balance system
    private var missedOutTick = 0
    private var warpInProgress = false

    init {
        FreezeStateHook.install { running }
    }

    override fun onEnabled() {
        missedOutTick = 0
        super.onEnabled()
    }

    override fun onDisabled() {
        if (balance) {
            warpInProgress = true
            while (missedOutTick > 0) {
                // todo: does not run module tick if running at game tick layer
                player.tick()
                missedOutTick--
            }
            warpInProgress = false
        }

        missedOutTick = 0
        super.onDisabled()
    }

    private fun notifyAndDisable(reason: DisableOn) {
        if (notification) {
            notification(
                this.name,
                message("disabled", reason.tag),
                NotificationEvent.Severity.INFO
            )
        }
        enabled = false
    }

    private val tickHandler = handler<GameTickEvent> {
        for (reason in disableOn) {
            if (reason.trigger?.asBoolean ?: continue) {
                notifyAndDisable(reason)
            }
        }
    }

    /**
     * Acts as timer = 0 replacement
     */
    @Suppress("unused")
    private val moveHandler = handler<PlayerTickEvent> { event ->
        if (warpInProgress || modes.activeMode === FreezeTickMovement) return@handler

        event.cancelEvent()
        missedOutTick++
    }

    @Suppress("unused")
    val renderHandler = handler<WorldRenderEvent> { event ->
        if (!balance || missedOutTick < 0 || warpInProgress) {
            return@handler
        }

        // Create a simulated player from the client player, as we cannot use the player simulation cache
        // since we are going to modify the player's yaw and pitch
        val directionalInput = DirectionalInput(mc.options)

        val simulatedPlayer = SimulatedPlayer.fromClientPlayer(
            SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(
                directionalInput,
                mc.options.keyJump.isPressedOnAny,
                mc.options.keySprint.isPressedOnAny || player.isSprinting,
                mc.options.keyShift.isPressedOnAny
            )
        )

        // Alter the simulated player's yaw and pitch to match the camera
        simulatedPlayer.yRot = event.camera.yRot()
        simulatedPlayer.xRot = event.camera.xRot()

        // Create a cache for the simulated player
        val simulatedPlayerCache = SimulatedPlayerCache(simulatedPlayer)
        val cachedPositions = simulatedPlayerCache
            .getSnapshotsBetween(0 until this.missedOutTick)

        event.renderEnvironment {
            drawLineStrip(
                argb = Color4b(0x00, 0x80, 0xFF, 0xFF).argb,
                positions = MutableVertexList(cachedPositions.size)
                    .addAllRelativeToCamera(cachedPositions, camera) { it.pos },
            )
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.packet is ClientboundPlayerPositionPacket) {
            missedOutTick = 0
            if (DisableOn.Flag in disableOn) {
                notifyAndDisable(DisableOn.Flag)
            }
        }
    }

}
