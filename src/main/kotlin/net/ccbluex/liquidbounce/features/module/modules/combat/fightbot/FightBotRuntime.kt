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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.AllowAutoJumpEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.ModuleToggleEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.CRITICAL_MODIFICATION

internal class FightBotRuntime(
    val settings: FightBotSettingsPort,
    val targetTracker: FightBotTargetPort,
    val combat: FightBotCombatPort,
    val remoteWeapons: FightBotRemoteWeaponPort,
    val debug: FightBotDebugPort,
) : EventListener, MinecraftShortcuts {

    var killAuraLease = FightBotKillAuraLease.start(autoEnable = false, killAuraEnabled = false)
    var currentTargetHandoff: FightBotTargetHandoff = FightBotTargetHandoff.Idle

    override fun parent(): EventListener = settings.eventParent

    val combatOperational: Boolean
        get() = killAuraLease.isOperational(combat.killAuraRunning)

    fun onEnabled() {
        killAuraLease = FightBotKillAuraLease.start(settings.autoEnableKillAura, combat.killAuraEnabled)
        if (killAuraLease.enableKillAura) combat.killAuraEnabled = true
        currentTargetHandoff = FightBotTargetHandoff.Idle
    }

    fun onDisabled() {
        val disableLeasedKillAura = killAuraLease.shouldDisableKillAuraOnRelease
        clearTargetAndWeapons(SpearKillFightBotTerminal.Disable, FightBotMaceTerminal.Disable)
        killAuraLease = FightBotKillAuraLease.start(autoEnable = false, killAuraEnabled = false)
        if (disableLeasedKillAura && combat.killAuraEnabled) combat.killAuraEnabled = false
    }

    private val killAuraToggleHandler = handler<ModuleToggleEvent> { event ->
        if (!event.moduleName.equals(combat.killAuraName, ignoreCase = true) || event.enabled) return@handler
        killAuraLease = killAuraLease.onKillAuraDisabled()
        clearTargetAndWeapons(SpearKillFightBotTerminal.TargetLoss, FightBotMaceTerminal.TargetLoss)
    }

    private val targetUpdateHandler = handler<RotationUpdateEvent> {
        handleTargetUpdate()
    }

    private val worldChangeHandler = handler<WorldChangeEvent> {
        clearTargetAndWeapons(SpearKillFightBotTerminal.WorldChange, FightBotMaceTerminal.WorldChange)
    }

    private val disconnectHandler = handler<DisconnectEvent> {
        clearTargetAndWeapons(SpearKillFightBotTerminal.Disconnect, FightBotMaceTerminal.Disconnect)
    }

    private val inputHandler = handler<MovementInputEvent>(priority = CRITICAL_MODIFICATION) { event ->
        handleMovementInput(event)
    }

    private val sprintHandler = handler<SprintEvent>(priority = CRITICAL_MODIFICATION) { event ->
        handleSprint(event)
    }

    private val autoJumpHandler = handler<AllowAutoJumpEvent> { event ->
        if (combatOperational && FightBotAutoAction.JUMP in settings.automaticActions) event.isAllowed = true
    }
}
