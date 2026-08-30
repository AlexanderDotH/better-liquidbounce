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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.fastutil.objectLinkedSetOf
import net.ccbluex.liquidbounce.config.ConfigMigrationOrder
import net.ccbluex.liquidbounce.config.ConfigMigrationRegistry
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.combat.runtime.TargetPriority
import net.ccbluex.liquidbounce.features.combat.runtime.TargetTracker
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.fightBotRouteTarget
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.doesNotCollideBelow
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Settings and compatibility facade for autonomous FightBot orchestration. */
object ModuleFightBot : ClientModule("FightBot", ModuleCategories.COMBAT) {

    private val opponentRange by float("OpponentRange", 3f, 0.1f..10f)
    private val dangerousYawDiff by float("DangerousYaw", 55f, 0f..90f, suffix = "°")
    private val runawayOnCooldown by boolean("RunawayOnCooldown", true)
    private val autoEnableKillAura by boolean("AutoEnableKillAura", true)
    private val spearAutomation by enumChoice("SpearAutomation", FightBotSpearAutomation.HeldOrHotbar)
    private val maceAutomation by enumChoice("MaceAutomation", FightBotMaceAutomation.Off)
    private val autoAction by multiEnumChoice("Auto", FightBotAutoAction.entries)

    private val targetPort = ModuleFightBotTargetTracker()
    private val runtime = FightBotRuntime(SettingsPort, targetPort, CombatPort, RemoteWeaponPort, DebugPort)
    private val targetTracker = tree(targetPort)

    internal object LeaderFollower : ToggleableValueGroup(ModuleFightBot, "Leader", false) {
        val username by text("Username", "")
        val radius by float("Radius", 5f, 2f..10f)
    }

    init {
        tree(LeaderFollower)
        ConfigMigrationRegistry.register(
            id = "fight-bot",
            order = ConfigMigrationOrder.FIGHT_BOT,
            migration = ::migrateLegacyFightBotConfig,
        )
    }

    override fun children(): List<EventListener> = listOf(runtime)

    internal val opponentRangeValue: Float
        get() = opponentRange
    internal val dangerousYawValue: Float
        get() = dangerousYawDiff
    internal val runawayOnCooldownValue: Boolean
        get() = runawayOnCooldown
    internal val autoEnableKillAuraValue: Boolean
        get() = autoEnableKillAura
    internal val automaticActions: Set<FightBotAutoAction>
        get() = autoAction
    internal val leaderFollower: LeaderFollower
        get() = LeaderFollower

    internal val targetHandoff: FightBotTargetHandoff
        get() = if (running) runtime.currentTargetHandoff else FightBotTargetHandoff.Inactive

    internal val configuredSpearAutomation: FightBotSpearAutomation
        get() = spearAutomation

    internal val configuredMaceAutomation: FightBotMaceAutomation
        get() = maceAutomation

    override fun onEnabled() = runtime.onEnabled()

    override fun onDisabled() = runtime.onDisabled()

    internal fun getMovementRotation(): Rotation = runtime.movementRotation()

    private object SettingsPort : FightBotSettingsPort {
        override val eventParent get() = ModuleFightBot
        override val opponentRange get() = opponentRangeValue
        override val dangerousYaw get() = dangerousYawValue
        override val runawayOnCooldown get() = runawayOnCooldownValue
        override val autoEnableKillAura get() = autoEnableKillAuraValue
        override val automaticActions get() = ModuleFightBot.automaticActions
        override val leaderRunning get() = LeaderFollower.running
        override val leaderUsername get() = LeaderFollower.username
        override val leaderRadius get() = LeaderFollower.radius
        override val spearAutomation get() = configuredSpearAutomation
        override val maceAutomation get() = configuredMaceAutomation
    }

    private object CombatPort : FightBotCombatPort {
        override val killAuraName get() = ModuleKillAura.name
        override var killAuraEnabled
            get() = ModuleKillAura.enabled
            set(value) { ModuleKillAura.enabled = value }
        override val killAuraRunning get() = ModuleKillAura.running
        override val interactionRange get() = ModuleKillAura.range.interactionRange
        override val extendedInteractionRange get() = ModuleKillAura.extendedInteractionRange
        override fun willClickAt() = ModuleKillAura.clicker.willClickAt()
    }

    private object RemoteWeaponPort : FightBotRemoteWeaponPort {
        override val maceRunning get() = ModuleMaceKill.running
        override val spearRunning get() = ModuleSpearKill.running
        override val maceRouteTarget get() = ModuleMaceKill.fightBotRouteTarget
        override val spearRouteTarget get() = ModuleSpearKill.fightBotRouteTarget

        override fun maceStateFor(target: LivingEntity) = ModuleMaceKill.fightBotStateFor(target).toFightBotState()
        override fun spearStateFor(target: LivingEntity) = ModuleSpearKill.fightBotStateFor(target)
        override fun requestMaceUse(target: LivingEntity) =
            ModuleMaceKill.requestFightBotMaceUse(target).toFightBotState()
        override fun requestSpearUse(target: LivingEntity) = ModuleSpearKill.requestFightBotSpearUse(target)
        override fun releaseMaceUse(terminal: FightBotMaceTerminal) =
            ModuleMaceKill.releaseFightBotMaceUse(terminal.toMaceKillTerminal())
        override fun releaseSpearUse(terminal: SpearKillFightBotTerminal) =
            ModuleSpearKill.releaseFightBotSpearUse(terminal)
    }

    private object DebugPort : FightBotDebugPort {
        override fun point(name: String, position: Vec3, color: FightBotDebugColor, size: Double) {
            val renderColor = when (color) {
                FightBotDebugColor.Blue -> Color4b.BLUE
                FightBotDebugColor.Magenta -> Color4b.MAGENTA
                FightBotDebugColor.Red -> Color4b.RED
                FightBotDebugColor.Green -> Color4b.GREEN
            }
            ModuleDebug.debugGeometry(ModuleFightBot, name, ModuleDebug.DebuggedPoint(position, renderColor, size))
        }
    }
}

private class ModuleFightBotTargetTracker : TargetTracker(
    fovRange = 0f..365f,
    defaultPriorities = objectLinkedSetOf(TargetPriority.DISTANCE),
), FightBotTargetPort {
    override val mode by enumChoice("Mode", FightBotTargetMode.Nearest)
    override val configuredName by text("Name", "")
    private val range by float("Range", 50f, 10f..100f)
    private val visibleOnly by boolean("VisibleOnly", true)
    private val notWhenVoid by boolean("NotWhenVoid", true)

    override fun validate(entity: LivingEntity): Boolean = super.validate(entity) &&
        entity.isAlive &&
        player.squaredBoxedDistanceTo(entity) <= range.sq() &&
        (!visibleOnly || !entity.isInvisible && player.hasLineOfSight(entity)) &&
        (!notWhenVoid || !entity.doesNotCollideBelow())
}

private fun MaceKillFightBotState.toFightBotState() = when (this) {
    MaceKillFightBotState.Unavailable -> FightBotMaceState.Unavailable
    MaceKillFightBotState.Ready -> FightBotMaceState.Ready
    MaceKillFightBotState.RouteActive -> FightBotMaceState.RouteActive
    MaceKillFightBotState.Rejected -> FightBotMaceState.Rejected
}

private fun FightBotMaceTerminal.toMaceKillTerminal() = when (this) {
    FightBotMaceTerminal.Completion -> MaceKillFightBotTerminal.Completion
    FightBotMaceTerminal.Rejection -> MaceKillFightBotTerminal.Rejection
    FightBotMaceTerminal.TargetLoss -> MaceKillFightBotTerminal.TargetLoss
    FightBotMaceTerminal.Disable -> MaceKillFightBotTerminal.Disable
    FightBotMaceTerminal.Death -> MaceKillFightBotTerminal.Death
    FightBotMaceTerminal.Disconnect -> MaceKillFightBotTerminal.Disconnect
    FightBotMaceTerminal.WorldChange -> MaceKillFightBotTerminal.WorldChange
}
