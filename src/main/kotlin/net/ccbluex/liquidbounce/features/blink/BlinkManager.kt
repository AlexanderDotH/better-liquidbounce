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
package net.ccbluex.liquidbounce.features.blink

import com.google.common.collect.Queues
import net.ccbluex.fastutil.filterIsInstance
import net.ccbluex.fastutil.forEachIsInstance
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.BlinkPacketAction
import net.ccbluex.liquidbounce.event.events.GameRenderTaskQueueEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PerspectiveEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspBox
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspData
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspModel
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspNone
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspWireframe
import net.ccbluex.liquidbounce.features.rotation.contract.RotationLagState
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.utils.MutableVertexList
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.network.position
import net.minecraft.client.CameraType
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Allows queueing packets and flush them later on demand.
 *
 * Fires [BlinkPacketEvent] to determine whether a packet should be queued or not. They can be
 * from origin [TransferOrigin.INCOMING] or [TransferOrigin.OUTGOING], but will be handled separately.
 */
object BlinkManager : EventListener, ValueGroup("BlinkManager") {

    val packetQueue: ConcurrentLinkedQueue<PacketSnapshot> = Queues.newConcurrentLinkedQueue()
    val positions
        get() = packetQueue
            .map { snapshot -> snapshot.packet }
            .filterIsInstance(ServerboundMovePlayerPacket::hasPosition)
            .map { p -> p.position }

    val isLagging
        get() = packetQueue.isNotEmpty()

    private val espMode = modes(this, "Esp", 2) {
        arrayOf(
            BlinkEspBox(it, ::getEspData),
            BlinkEspModel(it, getEspData = ::getEspData),
            BlinkEspWireframe(it, ::getEspData),
            BlinkEspNone(it),
        )
    }.apply {
        doNotIncludeAlways()
    }

    private val lineColor by color("Line", Color4b.LIQUID_BOUNCE)

    init {
        RotationLagState.bindBlinkLag { isLagging }
    }

    @Suppress("unused")
    private val flushHandler = handler<GameRenderTaskQueueEvent> {
        if (mc.connection?.connection?.isConnected != true) {
            packetQueue.clear()
            return@handler
        }

        if (fireEvent(null, TransferOrigin.OUTGOING) == BlinkPacketAction.FLUSH) {
            flush(TransferOrigin.OUTGOING)
        }
    }

    @Suppress("unused")
    private val flushReceiveHandler = handler<TickPacketProcessEvent> {
        if (mc.connection?.connection?.isConnected != true) {
            packetQueue.clear()
            return@handler
        }

        if (fireEvent(null, TransferOrigin.INCOMING) == BlinkPacketAction.FLUSH) {
            flush(TransferOrigin.INCOMING)
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = FINAL_DECISION) { event ->
        // Ignore packets that are already cancelled, as they are already handled
        if (event.isCancelled) {
            return@handler
        }

        val packet = event.packet
        val origin = event.origin

        // If we shouldn't lag, don't do anything
        val lagResult = fireEvent(packet, origin)
        if (lagResult == BlinkPacketAction.FLUSH) {
            flush(origin)
            return@handler
        }

        if (lagResult == BlinkPacketAction.PASS) {
            return@handler
        }

        when (blinkPacketDecision(packet)) {
            BlinkPacketDecision.PASS -> return@handler
            BlinkPacketDecision.FLUSH -> {
                flush(origin)
                return@handler
            }
            BlinkPacketDecision.QUEUE -> Unit
        }

        event.cancelEvent()
        packetQueue.add(
            PacketSnapshot(
                packet,
                origin,
                System.currentTimeMillis()
            )
        )
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        // Clear packets on disconnect
        if (event.world == null) {
            packetQueue.clear()
        }
    }

    private fun getEspData() = positions
        .firstOrNull()
        ?.takeUnless { PerspectiveEvent.perspective == CameraType.FIRST_PERSON }
        ?.let { BlinkEspData(player, it, RotationManager.actualServerRotation) }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (lineColor.a > 0) {
            event.renderEnvironment {
                drawLineStrip(
                    argb = lineColor.argb,
                    positions = MutableVertexList(positions.size).addAllRelativeToCamera(positions, camera),
                )
            }
        }
    }

    fun flush(flushWhen: (PacketSnapshot) -> Boolean) {
        flushBlinkPackets(packetQueue, flushWhen)
    }

    fun flush(origin: TransferOrigin) {
        flush { it.origin == origin }
    }

    fun flush(count: Int) {
        flushBlinkPacketCount(packetQueue, count)
    }

    fun cancel() {
        cancelBlinkPackets(packetQueue, positions.firstOrNull())
    }

    /** Removes one exact queued packet without flushing it, allowing its owner to retry safely. */
    internal fun takeQueued(packet: Packet<*>, origin: TransferOrigin): Boolean =
        packetQueue.takeQueuedPacket(packet, origin)

    fun isAboveTime(delay: Long): Boolean {
        val entryPacketTime = (packetQueue.firstOrNull()?.timestamp ?: return false)
        return System.currentTimeMillis() - entryPacketTime >= delay
    }

    inline fun <reified T> rewrite(action: (T) -> Unit) {
        packetQueue.forEachIsInstance<T>(action)
    }

    private fun fireEvent(packet: Packet<*>?, origin: TransferOrigin) =
        EventManager.callEvent(BlinkPacketEvent(packet, origin)).action

}
