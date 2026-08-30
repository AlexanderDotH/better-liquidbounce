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
package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import net.ccbluex.liquidbounce.common.runtime.ClientDestructionState
import net.ccbluex.liquidbounce.common.runtime.SilentHotbarRuntimeHooks
import net.ccbluex.liquidbounce.common.runtime.SilentHotbarSelectionGate
import net.ccbluex.liquidbounce.common.runtime.SilentPacketObservationHooks
import net.ccbluex.liquidbounce.common.runtime.TimerOwnerLifecycle
import net.ccbluex.liquidbounce.common.runtime.TimerOwnerRunningProvider
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.SelectHotbarSlotSilentlyEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.misc.ModulePacketLogger
import net.ccbluex.liquidbounce.features.module.modules.misc.ModulePlayerPositionLogger
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY

internal object ClientRuntimeHooksAdapter : EventListener {

    @Suppress("unused")
    private val silentHotbarTick = handler<GameTickEvent>(priority = 1001) {
        SilentHotbar.advanceTick()
    }

    @Suppress("unused")
    private val silentHotbarWorldChange = handler<WorldChangeEvent> {
        SilentHotbar.clearForWorldChange()
    }

    @Suppress("unused")
    private val timerTick = handler<GameTickEvent>(priority = FIRST_PRIORITY) {
        Timer.advanceTick()
    }

    fun install() {
        ClientDestructionState.install(HideAppearance::isDestructed)
        SilentHotbarRuntimeHooks.installSelectionGate(SilentHotbarSelectionGate { requester, slot ->
            !EventManager.callEvent(SelectHotbarSlotSilentlyEvent(requester, slot)).isCancelled
        })
        TimerOwnerLifecycle.install(TimerOwnerRunningProvider { owner ->
            (owner as? EventListener)?.running == true
        })
        SilentPacketObservationHooks.install { packet ->
            ModulePacketLogger.onPacket(TransferOrigin.OUTGOING, packet)
            ModulePlayerPositionLogger.onPacket(TransferOrigin.OUTGOING, packet, original = false)
        }
    }
}
