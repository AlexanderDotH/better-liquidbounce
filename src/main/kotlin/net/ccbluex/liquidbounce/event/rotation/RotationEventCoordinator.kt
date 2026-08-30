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

package net.ccbluex.liquidbounce.event.rotation

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MouseRotationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerVelocityStrafe
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.MODEL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.world.entity.Entity

object RotationEventCoordinator : EventListener {
    @Suppress("unused")
    private val lifecycleListener = object : EventListener {
        private val worldChangeHandler = handler<WorldChangeEvent> { RotationManager.reset() }
    }

    @Suppress("unused")
    private val mouseMovement = handler<MouseRotationEvent> { event ->
        RotationManager.adjustMouseRotation(event.cursorDeltaX, event.cursorDeltaY)
    }

    @Suppress("unused")
    private val velocityHandler = handler<PlayerVelocityStrafe>(priority = MODEL_STATE) { event ->
        val yaw = RotationManager.velocityRotationYaw() ?: return@handler
        event.velocity = Entity.getInputVector(event.movementInput, event.speed, yaw)
    }

    @Suppress("unused")
    private val gameTickHandler = handler<GameTickEvent>(priority = FIRST_PRIORITY) {
        EventManager.callEvent(RotationUpdateEvent)
        RotationManager.update()
    }

    val packetHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        RotationManager.trackPacket(
            packet = event.packet,
            incoming = event.origin == TransferOrigin.INCOMING,
            cancelled = event.isCancelled,
        )
    }

    override val running: Boolean
        get() = inGame
}
