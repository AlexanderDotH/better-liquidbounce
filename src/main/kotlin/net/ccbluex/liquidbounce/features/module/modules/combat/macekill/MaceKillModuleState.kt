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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.FightBotMaceUseSource
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceUsePolicy
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap

abstract class MaceKillModuleState : ClientModule(
    "MaceKill",
    ModuleCategories.COMBAT,
    disableOnQuit = true,
) {
    internal abstract val integration: MaceKillIntegrationPort
    internal abstract val routeSession: MaceKillRouteSession

    internal val fallHeight by int("FallHeight", 22, 1..170).apply { tagBy(this) }
    internal val maxTargetDistance by float(
        "TargetDistance",
        500f,
        3f..500f,
        aliases = listOf("MaxTargetDistance"),
    )
    internal val activationMode by enumChoice("Activation", DEFAULT_MACE_KILL_ACTIVATION_MODE)
    internal val targetSource by enumChoice("TargetSource", DEFAULT_MACE_KILL_TARGET_SOURCE)
    internal val movementConfiguration = MaceKillMovementConfiguration(this)
    internal val movement = tree(movementConfiguration.choice)
    internal val preview = MaceKillPreview(this)
    internal var previewTarget: LivingEntity? = null
    internal var activeRouteTarget: LivingEntity? = null
    internal var activeRouteOwner = MaceKillRouteOwner.NONE
    internal var remoteStrikeEndpoint: Vec3? = null
    internal var remoteStrikeTarget: LivingEntity? = null
    internal var remoteStrikeFallResetPlan: MacePostAttackFallResetPlan? = null
    internal var remoteStrikeEarliestTick = 0
    internal var fightBotMaceTarget: LivingEntity? = null
    internal var fightBotMaceState = MaceKillFightBotState.Unavailable
    internal var fightBotMaceSource: FightBotMaceUseSource? = null
    internal var pendingFightBotTerminal: MaceKillFightBotTerminal? = null
    internal val speedController = MaceKillSpeedController()
    internal val fallSafetyLifecycle = MaceKillFallSafetyLifecycle()
    internal val groundingPacketTracker = MaceKillGroundingPacketTracker()
    internal val routeEngine by lazy(LazyThreadSafetyMode.NONE) {
        RemoteKillRouteEngine(
            routeSession,
            RemoteKillWeaponAdapter<LivingEntity> { request -> commitRemoteStrike(request) },
            movementOwner = "MaceKill",
            retainMovementAfterCompletion = true,
        )
    }
    internal val routeAdmissionBackoff = MaceKillRouteAdmissionBackoff(MACE_KILL_ROUTE_ADMISSION_BACKOFF_TICKS)
    internal val instantRouteBackoff = MaceKillRouteAdmissionBackoff(MACE_KILL_INSTANT_FAILURE_BACKOFF_TICKS)
    internal val rejectedTargets = MaceKillTargetRejectionTracker<LivingEntity>(
        MACE_KILL_REJECTED_TARGET_RETRY_TICKS,
    )
    internal val returnConfirmation = MaceKillReturnConfirmationWindow(MACE_KILL_RETURN_CONFIRMATION_TICKS)
    internal val primingPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    internal val researchPacketContexts = IdentityHashMap<ServerboundMovePlayerPacket, MaceKillResearchPacketContext>()
    internal var plannedRoutePacket: ServerboundMovePlayerPacket? = null
    internal var routeOrigin: Vec3? = null
    internal var routeOriginBoundingBox: AABB? = null
    internal var routeRenderPath = emptyList<Vec3>()
    internal var routeStepWaitTicks = 0
    internal var routeStallTicks = 0
    internal var routeRejected = false
    internal var routeResumeTick = 0
    internal var localPacketRouteOrigin: Vec3? = null
    internal var primingDeliveryFailed = false
    internal var applyingStrikePackets = false
    internal var motionRouteActive = false
    internal var activeVanillaVClipSegments = emptySet<MaceKillVanillaVClipSegment>()
    internal var activeClipReachSession: MaceClipReachSession? = null
    internal var instantRecoveryPlan: MaceClipReachPlan? = null
    internal var instantCorrectionRecoveryActive = false
    internal var instantTerminalHandled = false
    internal var lastInstantPlanBlockReason: MaceClipReachBlockReason? = null
    internal var plannedTargetPosition: Vec3? = null
    internal var routeChainCount = 0
    internal var activeRouteConfiguration: MaceKillRouteExecutionConfiguration? = null
    internal var routeDeadlineTick = 0
    internal var holdAttackState = MaceKillHoldAttackState.IDLE
    internal var evidenceTargetId: Int? = null
    internal var evidenceDeadlineTick = 0
    internal var correctionState: MaceKillLocalCorrectionState? = null
    internal var correctionRecoveryAttempts = 0
    internal var instantServerRejected = false
    internal var researchExecution: MaceKillResearchExecution? = null
    internal val researchRuntime by lazy {
        MaceClipResearchRuntime(
            ConfigSystem.rootFolder.toPath().resolve("maceclip-research"),
        )
    }
    internal val researchControl = MaceClipResearchGuardedControl(
        hasActiveProbe = { researchRuntime.status() is MaceClipResearchStatus.Active },
        hasActiveRemoteKillSession = { routeEngine.ownsMovement },
        hasUnsafeMovementContext = this::hasUnsafeResearchMovementContext,
        startExecution = this::startResearchProbe,
        statusProvider = { researchRuntime.status() },
        abortExecution = this::abortResearchProbe,
    )
    internal val setbackListener = object : RemoteKillSetbackListener {
        override fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
            prepareRemoteCorrection(player)
        }

        override fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
            finishRemoteCorrection(player)
        }
    }

    internal val debugConsole = lazy(LazyThreadSafetyMode.NONE) {
        MaceKillDebugConsole(
            enabled = { integration.debugRunning },
            sink = { message -> logger.info(message) },
        )
    }
    internal val failureNotificationGate = MaceKillFailureNotificationGate(
        MACE_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS,
    )

    internal val maximumTargetRange: Float
        get() = facadeMaximumTargetRange

    internal val ownsKillAuraRoute: Boolean
        get() = facadeOwnsKillAuraRoute

    internal val suppressesNoFallPackets: Boolean
        get() = facadeSuppressesNoFallPackets

    internal val isKillAuraIntegrationAvailable: Boolean
        get() = facadeKillAuraIntegrationAvailable

    internal val isKillAuraIntegrationArmed: Boolean
        get() = facadeKillAuraIntegrationArmed

}

internal interface MaceKillIntegrationPort {
    val fightBotMacePolicy: MaceUsePolicy
    val acceptsKillAuraDelegation: Boolean
    val blinkRunning: Boolean
    val debugRunning: Boolean

    fun killAuraTarget(): LivingEntity?

    fun stopKillAuraBlockingIfActive()

    fun shouldAttack(target: LivingEntity): Boolean

    fun attackTarget(target: LivingEntity): AcceptedAttackResult

    fun buildProfiledAStarPacketRoute(
        origin: Vec3,
        outboundWaypoints: List<Vec3>,
        profile: MaceKillSpeedProfile,
        segmentValidator: SpearKillAStarSegmentValidator,
    ): SpearKillAStarPacketRoute?
}

/** MaceKill-owned view of the shared packet route session. */
internal interface MaceKillRouteSession : RemoteKillRouteSession {
    val canReplaceRemainingOutbound: Boolean
    val canReplaceRemainingApproach: Boolean
    val canStartChainedOutbound: Boolean

    fun replaceRemainingOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int = 0,
        terminalSuffixSteps: Int = 1,
        terminalBurstSteps: Int = 0,
        requireTerminalAuthorization: Boolean = false,
        completeReturnMovements: List<Vec3>? = null,
    ): Boolean

    fun startChainedOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
        preStrikeHoldTicks: Int = 0,
        terminalSuffixSteps: Int = 1,
        terminalBurstSteps: Int = 0,
        requireTerminalAuthorization: Boolean = false,
    ): Boolean
}
