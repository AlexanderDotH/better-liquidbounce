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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillPacketSessionPort
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillPacketBootSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillFallDamagePacketTracker
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.FightBotSpearAutomation
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteEngine
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillSpearAdapter
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.withVanillaSpearKillBlockShapes
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchRuntime
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap

abstract class SpearKillModuleState internal constructor(
    packetSession: SpearKillPacketSessionPort,
) : ClientModule(
    "SpearKill",
    ModuleCategories.COMBAT,
    aliases = listOf("AutoSpear"),
) {
    internal abstract val killAuraRunning: Boolean
    internal abstract val debugEnabled: Boolean
    internal abstract val fightBotSpearAutomation: FightBotSpearAutomation
    internal abstract fun delegatedKillAuraTarget(): LivingEntity?
    internal abstract fun shouldPrechargeDelegatedKillAura(): Boolean
    internal abstract fun stopDelegatedKillAuraBlocking(playerUsingItem: Boolean)
    internal abstract fun clearFightBotSpearUseEffect(terminal: SpearKillFightBotTerminal)
    internal abstract fun tryStartPacketChainEffect(defeatedTarget: LivingEntity): PacketChainStartResult

    internal fun clearAStarRenderPath() {
        plannedAStarRenderPath = emptyList()
    }

    internal fun resetVirtualFallSafety() = resetVirtualFallSafetyState()

    internal fun spearKillServerCollisionBoxAt(position: Vec3) = calculateSpearKillServerCollisionBoxAt(position)

    internal fun isSpearKillPositionNearGround(position: Vec3) = isSpearKillPositionNearGroundState(position)

    internal fun spearKillGroundProfile(origin: Vec3, movements: List<Vec3>) =
        spearKillGroundProfileState(origin, movements)

    internal fun beginVirtualFallSafety(
        outboundMovements: List<Vec3>,
        routeOrigin: Vec3 = player.position(),
    ) = beginVirtualFallSafetyForMovements(outboundMovements, routeOrigin)

    internal fun beginVirtualFallSafety(plan: SpearKillServerFallSafetyPlan) =
        beginVirtualFallSafetyPlan(plan)

    internal fun clearAStarTargetLock() {
        lockedAStarTarget = null
        packetRoutePreparationActive = false
        plannedAStarApproach = null
        plannedAStarTargetPosition = null
        plannedAStarTargetVelocity = Vec3.ZERO
        aStarPlanTick = 0
    }

    internal fun rejectSpearKillTarget(target: LivingEntity) {
        rejectedTargets.reject(target, player.tickCount)
    }

    internal fun isSpearKillTargetRejected(target: LivingEntity): Boolean =
        rejectedTargets.isRejected(target, player.tickCount)

    internal val maxTargetDistance by float(
        "TargetDistance",
        500f,
        3f..500f,
        aliases = listOf("MaxTargetDistance"),
    )
    internal val activationMode by enumChoice("Activation", DEFAULT_SPEAR_KILL_ACTIVATION_MODE)
    internal val targetSource by enumChoice("TargetSource", DEFAULT_SPEAR_KILL_TARGET_SOURCE)
    internal val movementConfiguration = SpearKillMovementConfiguration(this)
    internal val movement = tree(movementConfiguration.choice)
    internal val sneakWhileMoving by enumChoice(
        "SneakWhileMoving",
        SpearKillMovementAssistMode.NONE,
    )
    internal val elytraWhileMoving by enumChoice(
        "ElytraWhileMoving",
        SpearKillMovementAssistMode.NONE,
    )

    internal val attackMovements = ArrayDeque<Vec3>()
    internal val speedController = SpearKillSpeedController()
    internal val packetBootSession = SpearKillPacketBootSession(packetSession)
    internal val remoteKillRouteEngine = RemoteKillRouteEngine(
        session = packetBootSession,
        weaponAdapter = RemoteKillSpearAdapter,
        movementOwner = "SpearKill",
    )
    internal var standaloneRemoteMovementLease: RemoteKillMovementOwnership.Lease? = null
    internal val physicalReturnPositioner = SpearKillPhysicalReturnPositioner()
    internal val returnRecoveryTracker = SpearKillReturnRecoveryTracker()
    internal val setbackGuard = SpearKillSetbackGuard()
    internal val setbackRollback = SpearKillSetbackRollback()
    internal val fallDamageDeliveryTracker = SpearKillFallDamagePacketTracker()
    internal val fallSafetyLifecycle = SpearKillFallSafetyLifecycle()
    internal val attemptTracker = SpearKillAttemptTracker()
    internal val damageEvidenceTracker = SpearKillDamageEvidenceTracker()
    internal val networkOptimizer = SpearKillNetworkOptimizer()
    internal val debugConsole = lazy(LazyThreadSafetyMode.NONE) {
        SpearKillDebugConsole(
            enabled = { debugEnabled },
            sink = { message -> logger.info(message) },
        )
    }
    internal val failureNotificationGate = SpearKillFailureNotificationGate(
        SPEAR_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS,
    )
    internal val virtualSessionPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    internal val virtualFallGroundingPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    internal val virtualFallStabilizationPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    internal val primedMovementPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    internal val primedFinalMovementPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    internal val highSpeedResearch by lazy {
        SpearKillHighSpeedResearchRuntime(
            ConfigSystem.rootFolder.toPath().resolve("spearkill-high-speed-research"),
        )
    }
    internal val rejectedTargets = SpearKillTargetRejectionTracker<LivingEntity>(
        SPEAR_KILL_REJECTED_TARGET_RETRY_TICKS,
    )
    internal var previewTarget: LivingEntity? = null
    internal var plannedAStarRenderPath: List<Vec3> = emptyList()
    internal var packetAStarAttackActive = false
    internal var plannedPacket: ServerboundMovePlayerPacket? = null
    internal var awaitingVanillaMovementPacket = false
    internal var packetSessionOrigin: Vec3? = null
    internal var lockedAStarTarget: LivingEntity? = null
    internal var packetRoutePreparationActive = false
    internal var directTerminalReplanInstalled = false
    internal var plannedAStarApproach: SpearKillAStarAttackApproach? = null
    internal var plannedAStarTargetPosition: Vec3? = null
    internal var plannedAStarTargetVelocity = Vec3.ZERO
    internal var aStarPlanTick = 0
    internal var packetSetbackRecoveryAttempted = false
    internal var packetSessionSettings: SpearKillPacketSessionSettings? = null
    internal var activeMovementTransport: SpearKillMovementTransport? = null
    internal var movementAssistPreparationActive = false
    internal var motionPacketHeading: Rotation? = null
    internal var packetRecoveryStallTicks = 0
    internal var lastRequestedStep = SpearKillSpeedStep(0.0, 0.0)
    internal var lastDeliveredMovement = Vec3.ZERO
    internal var lastDeliveredOutboundMovement = Vec3.ZERO
    internal var terminalBurstDeliveredMovementThisTick = Vec3.ZERO
    internal var ownedMovementPacketsThisTick = 0
    internal var lastServerCorrectionTick: Int? = null
    internal var pendingSetbackFallDistance: Double? = null
    internal var pendingSetbackConfirmedOffset: Vec3? = null
    internal var virtualFallStabilizationDelivered = false
    internal var lastFallStabilizationDelivery: SpearKillOwnedPacketDelivery? = null
    internal var attemptRouteCompleted = false
    internal var manualAttackRequestLatched = false
    internal var holdUseLaunchTarget: LivingEntity? = null
    internal var serverSneaking = false
    internal var fightBotSpearTarget: LivingEntity? = null
    internal var fightBotSpearState = SpearKillFightBotState.Unavailable
    internal var fightBotStartedUse = false
    internal var fightBotSilentHotbarSlot: Int? = null
    internal var fightBotUseHand: InteractionHand? = null
    internal var pendingKillAuraTarget: LivingEntity? = null
    internal var killAuraSpearTarget: LivingEntity? = null
    internal var killAuraSpearPrechargeActive = false
    internal var killAuraStartedSpearUse = false
    internal var killAuraSpearUseHand: InteractionHand? = null
    internal var killAuraReturnActive = false
    internal var activePrimedStep: SpearKillPrimedPendingStep? = null
    internal var primedSessionPacketsDelivered = 0
    internal var awaitedPrimingPacket: ServerboundMovePlayerPacket? = null
    internal var awaitedPrimingDelivery: SpearKillOwnedPacketDelivery? = null
    internal var awaitedPrimedFinalPacket: ServerboundMovePlayerPacket? = null
    internal var awaitedPrimedFinalDelivery: SpearKillOwnedPacketDelivery? = null
    internal var highSpeedMoveProbeActive = false
}
