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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal interface FightBotSettingsPort {
    val eventParent: EventListener
    val opponentRange: Float
    val dangerousYaw: Float
    val runawayOnCooldown: Boolean
    val autoEnableKillAura: Boolean
    val automaticActions: Set<FightBotAutoAction>
    val leaderRunning: Boolean
    val leaderUsername: String
    val leaderRadius: Float
    val spearAutomation: FightBotSpearAutomation
    val maceAutomation: FightBotMaceAutomation
}

internal interface FightBotCombatPort {
    val killAuraName: String
    var killAuraEnabled: Boolean
    val killAuraRunning: Boolean
    val interactionRange: Float
    val extendedInteractionRange: Float
    fun willClickAt(): Boolean
}

internal interface FightBotTargetPort {
    val mode: FightBotTargetMode
    val configuredName: String
    var target: LivingEntity?
    fun validate(entity: LivingEntity): Boolean
    fun targets(): List<LivingEntity>
    fun reset()
}

internal interface FightBotRemoteWeaponPort {
    val maceRunning: Boolean
    val spearRunning: Boolean
    val maceRouteTarget: LivingEntity?
    val spearRouteTarget: LivingEntity?

    fun maceStateFor(target: LivingEntity): FightBotMaceState
    fun spearStateFor(target: LivingEntity): SpearKillFightBotState
    fun requestMaceUse(target: LivingEntity): FightBotMaceState
    fun requestSpearUse(target: LivingEntity): SpearKillFightBotState
    fun releaseMaceUse(terminal: FightBotMaceTerminal)
    fun releaseSpearUse(terminal: SpearKillFightBotTerminal)
}

internal enum class FightBotDebugColor {
    Blue,
    Magenta,
    Red,
    Green,
}

internal fun interface FightBotDebugPort {
    fun point(name: String, position: Vec3, color: FightBotDebugColor, size: Double)
}
