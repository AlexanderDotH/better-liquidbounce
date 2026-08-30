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


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.MaceKillIntegrationPort
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.MaceKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.MaceKillRouteSession
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.acceptsKillAuraDelegation
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.clearRuntime
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeCanAcceptKillAuraTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeFightBotRouteTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeFightBotStateFor
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeOnKillAuraDisabled
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeReleaseFightBotMaceUse
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeRequestFightBotMaceUse
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeRequestKillAuraMaceKill
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeReservesFightBotMaceUse
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.facadeShouldExcludeKillAuraTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.handleAcceptedAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.installMaceKillControlRegistries
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.registerMaceKillPreviewGlow
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigMigrationOrder
import net.ccbluex.liquidbounce.config.ConfigMigrationRegistry
import net.ccbluex.liquidbounce.features.combat.runtime.attackEntityWithResult
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeAttacked
import net.ccbluex.liquidbounce.features.global.GlobalSettingsCombat
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceUsePolicy
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.MaceKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.session.MaceKillRouteSessionControl
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.session.asMaceKillRouteSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillPacketBootSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedLimits
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.buildSpearKillProfiledAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.facade.SpearKillFacadeBridge
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.canReplaceRemainingApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.canReplaceRemainingOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.canStartChainedOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.replaceRemainingOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.startChainedOutbound
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Stable MaceKill module facade. Runtime, routing, research, rendering, and lifecycle stay in the
 * owning feature package while this object preserves settings, handlers, and cross-feature calls.
 */
object ModuleMaceKill : MaceKillModuleState() {

    internal override val routeSession: MaceKillRouteSession =
        SpearKillMaceRouteSessionControl().asMaceKillRouteSession()

    internal override val integration = object : MaceKillIntegrationPort {
        override val fightBotMacePolicy: MaceUsePolicy
            get() = when (ModuleFightBot.configuredMaceAutomation) {
                FightBotMaceAutomation.Off -> MaceUsePolicy.Off
                FightBotMaceAutomation.HeldMace -> MaceUsePolicy.HeldMace
                FightBotMaceAutomation.HeldOrHotbar -> MaceUsePolicy.HeldOrHotbar
            }

        override val acceptsKillAuraDelegation: Boolean
            get() = GlobalSettingsCombat.delegateKillAuraAttacks && ModuleKillAura.running

        override val blinkRunning: Boolean
            get() = ModuleBlink.running

        override val debugRunning: Boolean
            get() = ModuleDebug.running

        override fun killAuraTarget(): LivingEntity? = ModuleKillAura.targetForMaceKill()

        override fun stopKillAuraBlockingIfActive() {
            if (KillAuraAutoBlock.enforcedBlockingHand != null) {
                KillAuraAutoBlock.stopBlocking(pauses = true)
            }
        }

        override fun shouldAttack(target: LivingEntity): Boolean = target.shouldBeAttacked()

        override fun attackTarget(target: LivingEntity): AcceptedAttackResult =
            attackEntityWithResult(target, SwingMode.DO_NOT_HIDE, keepSprint = true)

        override fun buildProfiledAStarPacketRoute(
            origin: Vec3,
            outboundWaypoints: List<Vec3>,
            profile: MaceKillSpeedProfile,
            segmentValidator: SpearKillAStarSegmentValidator,
        ): SpearKillAStarPacketRoute? = buildSpearKillProfiledAStarPacketRoute(
            origin = origin,
            outboundWaypoints = outboundWaypoints,
            profile = SpearKillSpeedProfile(
                currentSpeed = profile.currentSpeed,
                limits = profile.limits.run {
                    SpearKillSpeedLimits(targetSpeed, acceleration, deceleration, stepDistance, vanillaBudget)
                },
            ),
            segmentValidator = segmentValidator,
        )
    }

    init {
        tree(preview)
        ConfigMigrationRegistry.register(
            id = "mace-kill",
            order = ConfigMigrationOrder.MACE_KILL,
            migration = ::migrateLegacyMaceKillConfig,
        )
        registerMaceKillPreviewGlow()
        MaceKillAttackHook.install(::handleAcceptedAttack)
        installMaceKillControlRegistries()

        registerMaceKillTickHandler()
        registerMaceKillPacketSafetyHandler()
        registerMaceKillPacketDeliveryHandler()
        registerMaceKillRenderHandler()
        registerMaceKillWorldChangeHandler()
        registerMaceKillDisconnectHandler()
    }

    internal val fightBotRouteTarget: LivingEntity?
        get() = facadeFightBotRouteTarget

    internal fun fightBotStateFor(target: LivingEntity): MaceKillFightBotState =
        facadeFightBotStateFor(target)

    internal fun reservesFightBotMaceUse(target: LivingEntity?): Boolean =
        facadeReservesFightBotMaceUse(target)

    internal fun requestFightBotMaceUse(target: LivingEntity): MaceKillFightBotState =
        facadeRequestFightBotMaceUse(target)

    internal fun releaseFightBotMaceUse(
        terminal: MaceKillFightBotTerminal = MaceKillFightBotTerminal.TargetLoss,
    ) = facadeReleaseFightBotMaceUse(terminal)

    internal fun canAcceptKillAuraTarget(target: LivingEntity): Boolean =
        facadeCanAcceptKillAuraTarget(target)

    internal fun shouldExcludeKillAuraTarget(target: LivingEntity): Boolean =
        facadeShouldExcludeKillAuraTarget(target)

    internal fun requestKillAuraMaceKill(target: LivingEntity): Boolean =
        facadeRequestKillAuraMaceKill(target)

    internal fun onKillAuraDisabled() = facadeOnKillAuraDisabled()

    override val running: Boolean
        get() = super.running || routeEngine.ownsMovement || researchExecution != null

    override fun onDisabled() {
        clearRuntime(MaceKillFightBotTerminal.Disable)
        failureNotificationGate.clear()
        if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()
        super.onDisabled()
    }
}

private class SpearKillMaceRouteSessionControl(
    private val delegate: SpearKillPacketBootSession = SpearKillPacketBootSession(
        SpearKillFacadeBridge.newPacketSessionPort,
    ),
) : MaceKillRouteSessionControl {
    override val remoteSession = delegate

    override val canReplaceRemainingOutbound: Boolean
        get() = delegate.canReplaceRemainingOutbound

    override val canReplaceRemainingApproach: Boolean
        get() = delegate.canReplaceRemainingApproach

    override val canStartChainedOutbound: Boolean
        get() = delegate.canStartChainedOutbound

    override fun replaceRemainingOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
        completeReturnMovements: List<Vec3>?,
    ): Boolean = delegate.replaceRemainingOutbound(
        outboundMovements,
        strikeHoldTicks,
        preStrikeHoldTicks,
        terminalSuffixSteps,
        terminalBurstSteps,
        requireTerminalAuthorization,
        completeReturnMovements,
    )

    override fun startChainedOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int,
        terminalSuffixSteps: Int,
        terminalBurstSteps: Int,
        requireTerminalAuthorization: Boolean,
    ): Boolean = delegate.startChainedOutbound(
        outboundMovements,
        strikeHoldTicks,
        preStrikeHoldTicks,
        terminalSuffixSteps,
        terminalBurstSteps,
        requireTerminalAuthorization,
    )
}
