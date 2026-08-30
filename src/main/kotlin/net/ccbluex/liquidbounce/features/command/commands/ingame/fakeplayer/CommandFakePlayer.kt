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
package net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.command.Command

/**
 * Stable command and event facade for client-side fake players.
 */
object CommandFakePlayer : Command.Factory, EventListener {
    private val session = FakePlayerSession()

    override fun createCommand(): Command = session.createCommand()

    @Suppress("unused")
    val explosionHandler = handler<PacketEvent>(handler = session::handlePacket)

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent>(handler = session::handleAttack)

    @Suppress("unused")
    val tickHandler = handler<GameTickEvent> { session.handleTick() }
}
