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
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.gson.adapter.toUnderlinedString
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogRecord
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogEntry
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogKind
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionLogOrigin
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.PlayerPositionSupplementalLogFactory
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.capturePositionSample
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.capturePositionState
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.integration.playerPositionLogFileLink
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.packet.PlayerPositionPacketRecordContext
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.packet.routePlayerPositionLogRecords
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.session.PlayerPositionLogSession
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.session.PlayerPositionChangeTracker
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.toLogEntry
import net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger.summary
import net.ccbluex.liquidbounce.features.module.modules.render.playermodel.ServerPlayerModelStateTracker
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.network.protocol.Packet
import net.minecraft.world.phys.Vec3
import java.time.LocalDateTime
import java.util.EnumSet

/**
 * Records the local player's client and transmitted positions alongside every observable remote-player update.
 */
object ModulePlayerPositionLogger : ClientModule(
    "PlayerPositionLogger",
    ModuleCategories.MISC,
    disableOnQuit = true,
) {

    private val playerScopes by multiEnumChoice(
        "Players",
        EnumSet.allOf(PlayerScope::class.java),
        canBeNone = false,
    )
    private val stateChanges by boolean("StateChanges", true)
    private val clientEvents by boolean("ClientEvents", true)
    private val chatMirror by boolean("ChatMirror", false)

    private val outputDirectory = ConfigSystem.rootFolder.resolve("player-position-logger")
    private val positionTracker = PlayerPositionChangeTracker()

    private val session = PlayerPositionLogSession(outputDirectory)

    init {
        doNotIncludeAlways()
    }

    override fun onEnabled() {
        positionTracker.clear()
        session.open(LocalDateTime.now().toUnderlinedString()).onFailure {
            chat(markAsError("Failed to create player position log: $it"))
        }.onSuccess { file ->
            chat(regular("Recording player positions to "), playerPositionLogFileLink(file), regular("."))
        }
        super.onEnabled()
    }

    override fun onDisabled() {
        positionTracker.clear()
        val result = session.close()
        result.failure?.let { chat(markAsError("Failed to close player position log: $it")) }
        result.file?.let { file ->
            chat(regular("Player position log was written to "), playerPositionLogFileLink(file), regular("."))
        }
        super.onDisabled()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(
        priority = EventPriorityConvention.READ_FINAL_STATE,
    ) { event ->
        onPacket(event.origin, event.packet, event.isCancelled, event.original)
    }

    fun onPacket(
        origin: TransferOrigin,
        packet: Packet<*>,
        cancelled: Boolean = false,
        original: Boolean = true,
    ) {
        if (!running) {
            return
        }

        val level = mc.level ?: return
        val localPlayer = mc.player ?: return

        val context = PlayerPositionPacketRecordContext(
            origin, packet, cancelled, original, level, localPlayer,
            ServerPlayerModelStateTracker.snapshot.capturePositionState(),
        ) { uuid -> mc.connection?.getPlayerInfo(uuid)?.profile?.name }
        routePlayerPositionLogRecords(context)
            .filter { includes(it.identity?.local == true) }
            .forEach(::record)
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (!stateChanges) {
            return@handler
        }

        val level = mc.level ?: return@handler
        val localPlayer = mc.player ?: return@handler
        val samples = level.players()
            .map { it.capturePositionSample(it === localPlayer) }
            .filter { includes(it.identity.local) }

        positionTracker.update(samples).forEach { change ->
            record(PlayerPositionLogRecord(
                origin = PlayerPositionLogOrigin.CLIENT_STATE,
                kind = change.kind,
                sample = change.sample,
                previousClientState = change.previousState,
            ))
        }
    }

    @Suppress("unused")
    private val networkMoveHandler = handler<PlayerNetworkMovementTickEvent>(
        priority = EventPriorityConvention.READ_FINAL_STATE,
    ) { event ->
        if (!clientEvents || PlayerScope.LOCAL !in playerScopes) {
            return@handler
        }

        val localPlayer = mc.player ?: return@handler
        record(PlayerPositionLogRecord(
            origin = PlayerPositionLogOrigin.CLIENT_EVENT,
            kind = PlayerPositionLogKind.LOCAL_NETWORK_MOVEMENT,
            sample = localPlayer.capturePositionSample(local = true),
            observation = PlayerPositionSupplementalLogFactory.localNetworkMovement(
                Vec3(event.x, event.y, event.z),
                event.ground,
            ),
            eventState = event.state.stateName,
        ))
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent>(
        priority = EventPriorityConvention.READ_FINAL_STATE,
    ) { event ->
        if (!clientEvents || PlayerScope.LOCAL !in playerScopes) {
            return@handler
        }

        val localPlayer = mc.player ?: return@handler
        record(PlayerPositionLogRecord(
            origin = PlayerPositionLogOrigin.CLIENT_EVENT,
            kind = PlayerPositionLogKind.LOCAL_JUMP,
            sample = localPlayer.capturePositionSample(local = true),
            observation = PlayerPositionSupplementalLogFactory.localJump(event.motion, event.yaw),
        ))
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        positionTracker.clear()
        write(
            PlayerPositionLogEntry(
                timestampMs = System.currentTimeMillis(),
                tick = mc.player?.tickCount,
                dimension = event.world?.dimension()?.identifier()?.toString(),
                origin = PlayerPositionLogOrigin.CLIENT_STATE,
                kind = PlayerPositionLogKind.WORLD_CHANGED,
            ),
        )
    }

    private fun record(record: PlayerPositionLogRecord) {
        val entry = record.toLogEntry(
            timestampMs = System.currentTimeMillis(),
            tick = mc.player?.tickCount,
            dimension = mc.level?.dimension()?.identifier()?.toString(),
            lastTransmittedState = ServerPlayerModelStateTracker.snapshot.capturePositionState(),
        )
        write(entry)

        if (chatMirror) {
            chat(regular(entry.summary()))
        }
    }

    private fun write(entry: PlayerPositionLogEntry) {
        session.write(entry)?.let { chat(markAsError("Failed to write player position log: $it")) }
    }

    private fun includes(local: Boolean): Boolean = when {
        local -> PlayerScope.LOCAL in playerScopes
        else -> PlayerScope.REMOTE in playerScopes
    }

    private enum class PlayerScope(override val tag: String) : Tagged {
        LOCAL("Local"),
        REMOTE("OtherPlayers"),
    }
}
