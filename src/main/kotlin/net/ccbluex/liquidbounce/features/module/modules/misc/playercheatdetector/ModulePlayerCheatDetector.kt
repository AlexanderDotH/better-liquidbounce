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
package net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.text.regular
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import java.util.EnumSet

/**
 * Client-side, Grim-inspired observer for suspicious remote-player behavior.
 *
 * This is intentionally notification-only. A public client cannot see another
 * player's private C2S packets, so exact Grim checks are degraded to observable
 * movement/action heuristics.
 */
object ModulePlayerCheatDetector : ClientModule("PlayerCheatDetector", ModuleCategories.MISC) {

    private val checks by multiEnumChoice(
        "Checks",
        EnumSet.of(
            PlayerCheatCheck.MOVEMENT,
            PlayerCheatCheck.VELOCITY,
            PlayerCheatCheck.REACH,
        ),
        canBeNone = false,
    )

    private val strictness by enumChoice("Strictness", DetectorStrictness.CONSERVATIVE)
    private val minConfidence by int("MinConfidence", 70, 1..100, "%")
    private val notifyCooldownSeconds by int("NotifyCooldownSeconds", 15, 1..120, "s")
    private val sampleIntervalTicks by int("SampleIntervalTicks", 3, 1..20, "ticks")
    private val maxTrackedPlayers by int("MaxTrackedPlayers", 32, 8..256)
    private val maxBlockActionsPerTick by int("MaxBlockActionsPerTick", 4, 0..32)
    private val ignoreFriends by boolean("IgnoreFriends", true)
    private val ignoreBots by boolean("IgnoreBots", true)
    private val chatLog by boolean("ChatLog", false)
    private val debugUnsupportedChecks by boolean("DebugUnsupportedChecks", false)

    private val tracker = ObservedPlayerTracker()
    private val engine = GrimObserverEngine()
    private val budget = DetectorWorkBudget()

    override fun onEnabled() {
        tracker.reset()
        engine.reset()
        budget.reset()

        if (debugUnsupportedChecks) {
            chat(
                regular(
                    "Unsupported Grim checks in client-only mode: " +
                        ObserverCheckRegistry.unsupportedChecks().joinToString { it.name }
                )
            )
        }

        super.onEnabled()
    }

    override fun onDisabled() {
        tracker.reset()
        engine.reset()
        budget.reset()
        super.onDisabled()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (!budget.shouldSampleMovement(player.tickCount, sampleIntervalTicks)) {
            return@handler
        }

        val frames = tracker.sample(world.players(), player, maxTrackedPlayers, ::canObserve)
        val nowMs = System.currentTimeMillis()

        for (frame in frames) {
            val notices = engine.handleMovement(
                frame,
                checks,
                strictness,
                minConfidence,
                notifyCooldownSeconds * 1000L,
                nowMs,
            )
            notices.forEach(::sendNotice)
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING) {
            return@handler
        }

        val tick = player.tickCount
        when (val packet = event.packet) {
            is ClientboundSetEntityMotionPacket -> {
                if (PlayerCheatCheck.VELOCITY in checks) {
                    processAction(tracker.actionFromVelocity(packet.id, packet.movement, tick))
                }
            }

            is ClientboundDamageEventPacket -> {
                if (PlayerCheatCheck.REACH in checks) {
                    processAction(tracker.actionFromDamage(packet.entityId, packet.sourceCauseId, tick))
                }
            }

            is ClientboundBlockUpdatePacket -> {
                processBlockAction(packet, tick)
            }

            is ClientboundRemoveEntitiesPacket -> {
                packet.entityIds.forEach { entityId ->
                    tracker.removeEntity(entityId)?.let(engine::reset)
                }
            }

            is ClientboundLoginPacket -> {
                tracker.reset()
                engine.reset()
                budget.reset()
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        tracker.reset()
        engine.reset()
        budget.reset()
    }

    private fun processBlockAction(packet: ClientboundBlockUpdatePacket, tick: Int) {
        if (PlayerCheatCheck.SCAFFOLD !in checks && PlayerCheatCheck.BREAKING !in checks) {
            return
        }

        if (!budget.tryConsumeBlockAction(tick, maxBlockActionsPerTick)) {
            return
        }

        processAction(tracker.actionFromBlockUpdate(packet.pos, packet.blockState, tick))
    }

    private fun processAction(action: ObservedActionFrame?) {
        if (action == null) {
            return
        }

        val notices = engine.handleAction(
            action,
            checks,
            strictness,
            minConfidence,
            notifyCooldownSeconds * 1000L,
            System.currentTimeMillis(),
        )
        notices.forEach(::sendNotice)
    }

    private fun canObserve(player: RemotePlayer): Boolean {
        if (ignoreFriends && FriendManager.isFriend(player)) {
            return false
        }

        if (ignoreBots && ModuleAntiBot.isBot(player)) {
            return false
        }

        return true
    }

    private fun sendNotice(notice: DetectionNotice) {
        val flag = notice.flag
        val message = "${flag.playerName} flagged ${flag.checkName} " +
            "(${flag.confidence}%): ${flag.verbose}"
        val severity = when (flag.severity) {
            DetectionSeverity.INFO -> NotificationEvent.Severity.INFO
            DetectionSeverity.ERROR -> NotificationEvent.Severity.ERROR
        }

        notification("PlayerCheatDetector", message, severity)

        if (chatLog) {
            chat(regular(message))
        }
    }
}
