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
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.additions.forceSneak
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.global.GlobalSettingsCombat
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.ServerObserver
import net.ccbluex.liquidbounce.utils.client.isNewerThanOrEquals1_21_6
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.ccbluex.liquidbounce.utils.entity.ping
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.OBJECTION_AGAINST_EVERYTHING
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.utils.network.send1_21_5StartSneaking
import net.ccbluex.liquidbounce.utils.network.send1_21_5StopSneaking
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.KineticWeapon
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Spear kill module
 *
 * Automatically attacks enemies using a charged spear.
 * Direct routes are preferred; the optional AStar route falls back around collision-blocked paths.
 */
@Suppress("TooManyFunctions", "LargeClass")
object ModuleSpearKill : ClientModule("SpearKill", ModuleCategories.COMBAT, aliases = listOf("AutoSpear")) {

    private val maxTargetDistance by float(
        "TargetDistance",
        500f,
        3f..500f,
        aliases = listOf("MaxTargetDistance"),
    )
    private val activationMode by enumChoice("Activation", DEFAULT_SPEAR_KILL_ACTIVATION_MODE)
    private val targetSource by enumChoice("TargetSource", DEFAULT_SPEAR_KILL_TARGET_SOURCE)
    private val movementConfiguration = SpearKillMovementConfiguration(this)
    private val movement = tree(movementConfiguration.choice)
    private val sneakWhileMoving by enumChoice(
        "SneakWhileMoving",
        SpearKillMovementAssistMode.NONE,
    )
    private val elytraWhileMoving by enumChoice(
        "ElytraWhileMoving",
        SpearKillMovementAssistMode.NONE,
    )

    private object Preview : ToggleableValueGroup(this, "Preview", true) {
        val renderPath by boolean("RenderPath", false)
        val mode = choices("Mode", 0) { arrayOf(Box, Glow) }

        object Box : Mode("Box") {
            override val parent: ModeValueGroup<Mode>
                get() = mode

            val fillColor by color("FillColor", Color4b.RED.alpha(67))
            val outlineColor by color("OutlineColor", Color4b.WHITE.alpha(167))
        }

        object Glow : Mode("Glow") {
            override val parent: ModeValueGroup<Mode>
                get() = mode

            val glowColor by color("GlowColor", Color4b.RED)
            val glowStyle = EspGlowStyleConfig(this)
        }

        override fun prepareDeserialize(jsonObject: JsonObject) {
            super.prepareDeserialize(jsonObject)
            migrateLegacySpearKillPreviewConfig(jsonObject)
        }
    }

    init {
        tree(Preview)
        TargetGlowSourceRegistry.register(::currentPreviewGlow)
    }

    private val attackMovements = ArrayDeque<Vec3>()
    private val speedController = SpearKillSpeedController()
    private val packetBootSession = SpearKillPacketBootSession()
    private val physicalReturnPositioner = SpearKillPhysicalReturnPositioner()
    private val returnRecoveryTracker = SpearKillReturnRecoveryTracker()
    private val setbackGuard = SpearKillSetbackGuard()
    private val setbackRollback = SpearKillSetbackRollback()
    private val fallDamageDeliveryTracker = SpearKillFallDamagePacketTracker()
    private val fallSafetyLifecycle = SpearKillFallSafetyLifecycle()
    private val attemptTracker = SpearKillAttemptTracker()
    private val damageEvidenceTracker = SpearKillDamageEvidenceTracker()
    private val networkOptimizer = SpearKillNetworkOptimizer()
    private val failureNotificationGate = SpearKillFailureNotificationGate(
        SPEAR_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS,
    )
    private val virtualSessionPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    private val virtualFallGroundingPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    private val virtualFallStabilizationPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    private val rejectedTargets = SpearKillTargetRejectionTracker<LivingEntity>(
        SPEAR_KILL_REJECTED_TARGET_RETRY_TICKS,
    )
    private var previewTarget: LivingEntity? = null
    private var plannedAStarRenderPath: List<Vec3> = emptyList()
    private var packetAStarAttackActive = false
    private var plannedPacket: ServerboundMovePlayerPacket? = null
    private var awaitingVanillaMovementPacket = false
    private var packetSessionOrigin: Vec3? = null
    private var lockedAStarTarget: LivingEntity? = null
    private var packetRoutePreparationActive = false
    private var directTerminalReplanInstalled = false
    private var plannedAStarApproach: SpearKillAStarAttackApproach? = null
    private var plannedAStarTargetPosition: Vec3? = null
    private var plannedAStarTargetVelocity = Vec3.ZERO
    private var aStarPlanTick = 0
    private var packetSetbackRecoveryAttempted = false
    private var packetSessionSettings: SpearKillPacketSessionSettings? = null
    private var activeMovementTransport: SpearKillMovementTransport? = null
    private var movementAssistPreparationActive = false
    private var motionPacketHeading: Rotation? = null
    private var packetRecoveryStallTicks = 0
    private var lastRequestedStep = SpearKillSpeedStep(0.0, 0.0)
    private var lastDeliveredMovement = Vec3.ZERO
    private var lastDeliveredOutboundMovement = Vec3.ZERO
    private var terminalBurstDeliveredMovementThisTick = Vec3.ZERO
    private var ownedMovementPacketsThisTick = 0
    private var lastServerCorrectionTick: Int? = null
    private var pendingSetbackFallDistance: Double? = null
    private var pendingSetbackConfirmedOffset: Vec3? = null
    private var virtualFallStabilizationDelivered = false
    private var attemptRouteCompleted = false
    private var manualAttackRequestLatched = false
    private var holdUseLaunchTarget: LivingEntity? = null
    private var serverSneaking = false
    private var fightBotSpearTarget: LivingEntity? = null
    private var fightBotSpearState = SpearKillFightBotState.Unavailable
    private var fightBotStartedUse = false
    private var fightBotSilentHotbarSlot: Int? = null
    private var fightBotUseHand: InteractionHand? = null
    private var pendingKillAuraTarget: LivingEntity? = null
    private var killAuraSpearTarget: LivingEntity? = null
    private var killAuraSpearPrechargeActive = false
    private var killAuraStartedSpearUse = false
    private var killAuraSpearUseHand: InteractionHand? = null
    private var killAuraReturnActive = false

    private object FightBotSpearUseRequester

    @Suppress("LongParameterList")
    private data class AStarAttackPlan(
        val approach: SpearKillAStarAttackApproach,
        val packetRoute: SpearKillAStarPacketRoute,
        val renderPath: List<Vec3>,
        val targetPosition: Vec3,
        val targetVelocity: Vec3,
        val schedule: SpearKillPathSchedule,
        val preStrikeHoldTicks: Int,
        val terminalSuffixCount: Int,
    )

    private data class AStarSpatialPlan(
        val approach: SpearKillAStarAttackApproach,
        val packetRoute: SpearKillAStarPacketRoute,
        val renderPath: List<Vec3>,
        val terminalSuffixCount: Int,
    )

    private data class DirectPacketRoutePlan(
        val route: SpearKillAStarPacketRoute,
        val targetSnapshot: SpearKillRouteTargetSnapshot,
    )

    private data class SpearKillPlayerRouteSnapshot(
        val eyeOffset: Vec3,
        val lookAngle: Vec3,
        val sessionBoundingBox: AABB,
        val speedProfile: SpearKillSpeedProfile,
        val safeVerticalStep: Double,
        val maximumTargetDistance: Double,
    )

    private data class SpearKillPacketSessionSettings(
        val transport: SpearKillMovementTransport,
        val stepWaitTicks: Int,
        val routingMode: SpearKillRoutingMode,
        val aStar: SpearKillAStarSessionSettings,
        val damageEvidenceWindowTicks: Int,
        val setbackBackoffTicks: Int,
        val allowTerminalBurst: Boolean,
        val instantMaxPackets: Int,
    ) {
        val networkOptimized: Boolean
            get() = routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED

        val strikeHoldTicks: Int
            get() = spearKillStrikeHoldTicks(routingMode)
    }

    private data class SpearKillAStarSessionSettings(
        val maxCost: Int,
        val diagonal: Boolean,
        val lineOfSightShortcuts: Boolean,
    )

    private data class PacketChainPlan(
        val outboundMovements: List<Vec3>,
        val routeMode: String,
        val hitTicks: Int,
        val strikeHoldTicks: Int,
        val terminalBurstSteps: Int = 0,
        val preStrikeHoldTicks: Int = 0,
        val terminalAuthorizationRequired: Boolean = false,
        val aStarPlan: AStarAttackPlan? = null,
    )

    private data class InstantStepDelivery(
        val packetSent: Boolean,
        val continueBurst: Boolean,
    )

    private enum class PacketFollowTermination(
        val rejectTarget: Boolean,
        val notificationKey: String?,
    ) {
        DEFEATED(rejectTarget = false, notificationKey = null),
        UNREACHABLE(rejectTarget = true, notificationKey = "targetUnreachable"),
        BLOCKED(rejectTarget = true, notificationKey = "pathBlocked"),
    }

    private enum class PacketChainStartResult {
        STARTED,
        FAILED,
    }

    internal val currentAttackVelocity get() = if (packetBootSession.active) 0.0 else currentMovement.length()
    internal val currentAttackDirection get() = currentMovement.normalize()
    internal val usesPacketMovement get() = packetBootSession.active
    private val currentMovement get() = attackMovements.firstOrNull() ?: Vec3.ZERO
    private val hasActiveAttackPath get() = attackMovements.isNotEmpty() || packetBootSession.active
    private val hasSpearKillReturnWork
        get() = hasActiveAttackPath || setbackGuard.armed || setbackRollback.confirming ||
            packetSetbackRecoveryAttempted
    private val activeRouteHeading: Rotation?
        get() = when {
            packetBootSession.active -> packetBootSession.pathHeading
            attackMovements.isNotEmpty() -> spearKillKineticHeading(currentMovement)
            else -> null
        }
    internal val controlsSpearUse
        get() = shouldControlSpearKillUse(
            spearKillRunning = running,
            attackPathActive = hasActiveAttackPath,
            routePreparationActive = packetRoutePreparationActive,
            physicalUseRequested = isUseInputHeld,
            automaticUseRequested = hasFightBotSpearRequest || hasKillAuraSpearUseRequest,
        )
    internal val maximumTargetRange get() = maxTargetDistance
    internal val currentAttemptSnapshot get() = attemptTracker.current
    internal val lastAttemptSnapshot get() = attemptTracker.lastCompleted
    internal val fightBotRouteTarget: LivingEntity?
        get() = fightBotSpearTarget.takeIf {
            fightBotSpearState == SpearKillFightBotState.RouteActive && hasActiveAttackPath
        }
    private val killAuraOwnsAttempt
        get() = attemptTracker.current?.targetSource == KILL_AURA_INHERITED_TARGET_SOURCE
    internal val ownsKillAuraRoute
        get() = killAuraOwnsAttempt &&
            (hasActiveAttackPath || setbackRollback.confirming || packetSetbackRecoveryAttempted)

    private val acceptsKillAuraDelegation: Boolean
        get() = GlobalSettingsCombat.delegateKillAuraAttacks && ModuleKillAura.running

    internal val isKillAuraIntegrationAvailable: Boolean
        get() = isSpearKillKillAuraAcquisitionAvailable(
            moduleEnabled = enabled,
            moduleRunning = running,
            delegationEnabled = acceptsKillAuraDelegation,
            holdingSpear = holdingSpear,
            routeBlocked = packetBootSession.recovering || setbackRollback.confirming ||
                packetSetbackRecoveryAttempted || hasActiveAttackPath && !ownsKillAuraRoute,
        )

    internal val isKillAuraIntegrationArmed: Boolean
        get() = isSpearKillKillAuraAttackArmed(
            acquisitionAvailable = isKillAuraIntegrationAvailable,
            usingSpear = isUsingSpear,
            activationRequested = hasActivationRequest,
            hasKineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) != null,
        )

    internal fun fightBotStateFor(target: LivingEntity): SpearKillFightBotState =
        fightBotSpearState.takeIf { fightBotSpearTarget === target } ?: SpearKillFightBotState.Unavailable

    internal fun reservesFightBotSpearUse(target: LivingEntity?): Boolean = target != null &&
        fightBotSpearTarget === target &&
        fightBotSpearState.reservesKillAuraSubsystems

    /** Starts or maintains a scoped use/slot reservation for FightBot's current distant target. */
    internal fun requestFightBotSpearUse(target: LivingEntity): SpearKillFightBotState = when {
        isSpearKillTargetRejected(target) -> rejectFightBotSpearUse(target)
        !canPrepareFightBotSpearUse(target) -> unavailableFightBotSpearUse(target)
        else -> prepareFightBotSpearUse(target)
    }

    private fun prepareFightBotSpearUse(target: LivingEntity): SpearKillFightBotState {
        if (fightBotSpearTarget !== target) {
            clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
        }
        fightBotSpearTarget = target

        return when {
            hasActiveAttackPath -> setFightBotSpearState(SpearKillFightBotState.RouteActive)
            !refreshFightBotSilentSlot() -> failFightBotSpearUse()
            isUsingSpear -> setFightBotSpearState(SpearKillFightBotState.Charging)
            else -> startFightBotSpearUse()
        }
    }

    private fun refreshFightBotSilentSlot(): Boolean = fightBotSilentHotbarSlot?.let { slot ->
        SilentHotbar.selectSlotSilently(FightBotSpearUseRequester, slot, 2)
    } ?: true

    private fun startFightBotSpearUse(): SpearKillFightBotState {
        if (player.isUsingItem && KillAuraAutoBlock.enforcedBlockingHand != null) {
            KillAuraAutoBlock.stopBlocking(pauses = true)
        }

        return if (player.isUsingItem) failFightBotSpearUse() else resolveAndStartFightBotSpearUse()
    }

    private fun resolveAndStartFightBotSpearUse(): SpearKillFightBotState {
        val source = resolveFightBotSpearUseSource() ?: return failFightBotSpearUse()
        val hand = resolveFightBotSpearHand(source) ?: return failFightBotSpearUse()
        return if (useItem(hand) is InteractionResult.Success) {
            fightBotStartedUse = true
            fightBotUseHand = hand
            setFightBotSpearState(SpearKillFightBotState.Charging)
        } else {
            failFightBotSpearUse()
        }
    }

    private fun resolveFightBotSpearHand(source: FightBotSpearUseSource): InteractionHand? = when (source) {
        FightBotSpearUseSource.MainHand -> InteractionHand.MAIN_HAND
        FightBotSpearUseSource.Offhand -> InteractionHand.OFF_HAND
        is FightBotSpearUseSource.Hotbar -> {
            if (!SilentHotbar.selectSlotSilently(FightBotSpearUseRequester, source.slot, 2)) {
                null
            } else {
                fightBotSilentHotbarSlot = source.slot
                InteractionHand.MAIN_HAND
            }
        }
    }

    private fun unavailableFightBotSpearUse(target: LivingEntity): SpearKillFightBotState {
        if (fightBotSpearTarget === target) {
            clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
        }
        return SpearKillFightBotState.Unavailable
    }

    private fun failFightBotSpearUse(): SpearKillFightBotState {
        clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
        return SpearKillFightBotState.Unavailable
    }

    private fun setFightBotSpearState(state: SpearKillFightBotState): SpearKillFightBotState {
        fightBotSpearState = state
        return state
    }

    internal fun releaseFightBotSpearUse(
        terminal: SpearKillFightBotTerminal = SpearKillFightBotTerminal.TargetLoss,
    ) {
        if (fightBotSpearTarget != null && hasActiveAttackPath) {
            clearAttack("fightbot-${terminal.name.lowercase()}")
            return
        }
        clearFightBotSpearUse(terminal)
    }

    private fun canPrepareFightBotSpearUse(target: LivingEntity): Boolean {
        val lockedTarget = lockedAStarTarget
        return enabled && running && acceptsKillAuraDelegation &&
            ModuleFightBot.configuredSpearAutomation != FightBotSpearAutomation.Off &&
            (lockedTarget == null || lockedTarget === target) &&
            (!hasActiveAttackPath || fightBotSpearTarget === target) &&
            !packetBootSession.recovering && !setbackRollback.confirming && !packetSetbackRecoveryAttempted &&
            isSpearKillTargetCandidateEligible(
                isCombatSafe = target.shouldBeAttacked(),
                isAlive = target.isAlive && !target.isRemoved,
                isInCurrentWorld = target.level() === world,
                isWithinRange = player.distanceTo(target) in 3f..maxTargetDistance,
                isRejected = false,
            )
    }

    private fun resolveFightBotSpearUseSource(): FightBotSpearUseSource? = selectFightBotSpearUseSource(
        automation = ModuleFightBot.configuredSpearAutomation,
        mainHandSpear = player.mainHandItem.isSpear,
        offhandSpear = player.offhandItem.isSpear,
        selectedHotbarSlot = SilentHotbar.serversideSlot,
        hotbarSpearSlots = Slots.Hotbar.asSequence()
            .filter { it.itemStack.isSpear }
            .mapNotNull { it.hotbarIndex }
            .toList(),
    )

    private fun rejectFightBotSpearUse(target: LivingEntity): SpearKillFightBotState {
        clearFightBotSpearUse(SpearKillFightBotTerminal.Rejection)
        fightBotSpearTarget = target
        fightBotSpearState = SpearKillFightBotState.Rejected
        return fightBotSpearState
    }

    private fun clearFightBotSpearUse(terminal: SpearKillFightBotTerminal) {
        val cleanup = fightBotSpearCleanup(
            terminal = terminal,
            startedUse = fightBotStartedUse,
            selectedSilentSlot = fightBotSilentHotbarSlot != null,
        )
        val currentPlayer = mc.player
        if (currentPlayer != null && shouldStopFightBotSpearUse(
                startedUse = cleanup.stopUse,
                isUsingItem = currentPlayer.isUsingItem,
                isSameHand = fightBotUseHand == null || currentPlayer.usedItemHand == fightBotUseHand,
                isUsingSpear = currentPlayer.useItem.isSpear,
            )
        ) {
            mc.gameMode?.releaseUsingItem(currentPlayer)
        }
        if (cleanup.resetSilentSlot) {
            SilentHotbar.resetSlot(FightBotSpearUseRequester)
        }

        fightBotSpearTarget = null
        fightBotSpearState = SpearKillFightBotState.Unavailable
        fightBotStartedUse = false
        fightBotSilentHotbarSlot = null
        fightBotUseHand = null
    }

    internal fun canAcceptKillAuraTarget(target: LivingEntity): Boolean {
        val lockedTarget = lockedAStarTarget
        return isKillAuraIntegrationAvailable &&
            (lockedTarget == null || lockedTarget === target) &&
            isSpearKillTargetCandidateEligible(
                isCombatSafe = target.shouldBeAttacked(),
                isAlive = target.isAlive && !target.isRemoved,
                isInCurrentWorld = target.level() === world,
                isWithinRange = player.distanceTo(target) in 3f..maxTargetDistance,
                isRejected = isSpearKillTargetRejected(target),
            )
    }

    /**
     * Exact route heading used by packets that carry their own rotation, such as use-item.
     */
    @JvmStatic
    fun routeRotationOverride(): Rotation? = activeRouteHeading.takeIf { running }

    /** True while SpearKill drives the local raised-spear pose instead of FastUse. */
    @JvmStatic
    val controlsSpearAnimation: Boolean
        get() = shouldRaiseSpearAnimation
    private val shouldRaiseSpearAnimation
        get() = shouldRaiseSpearKillAnimation(
            spearKillRunning = running,
            holdingSpear = holdingSpear,
            attackPathActive = hasActiveAttackPath,
            attackRequested = isSpearUseRequested,
            isUsingSpear = isUsingSpear,
        )
    private val usesPacketMovementMode get() = movement.activeMode === movementConfiguration.packet
    private val packetRoutingMode
        get() = when (movementConfiguration.packet.routing.activeMode) {
            movementConfiguration.packet.aStar -> SpearKillRoutingMode.A_STAR
            movementConfiguration.packet.networkOptimized -> SpearKillRoutingMode.NETWORK_OPTIMIZED
            movementConfiguration.packet.instant -> SpearKillRoutingMode.INSTANT
            else -> SpearKillRoutingMode.DIRECT
        }
    private val activePacketRoutingMode
        get() = packetSessionSettings?.routingMode ?: packetRoutingMode
    private val usesNetworkOptimizedRouting
        get() = usesPacketMovementMode && packetRoutingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED
    private val packetRoutingSupportsAStar
        get() = usesPacketMovementMode &&
            (activePacketRoutingMode == SpearKillRoutingMode.A_STAR ||
                activePacketRoutingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED)
    private val recoveryPlanningStepLimit
        get() = currentSpeedProfile(activeSpeedStepDistance).maximumStepLimit
    private val activePacketStepWaitTicks
        get() = packetSessionSettings?.stepWaitTicks ?: movementConfiguration.packet.stepDelay
    private val activeStepLimit
        get() = if (usesPacketMovementMode) {
            movementConfiguration.packet.stepDistance
        } else {
            movementConfiguration.motion.stepDistance
        }
    private val activeSpeedStepDistance
        get() = activeMovementTransport?.stepLimit ?: activeStepLimit.toDouble()

    private val currentVanillaMovementBudget: Double
        get() = calculateSpearKillVanillaMovementBudget(
            serverPhysicsVelocity = player.deltaMovement,
            fallFlying = player.isFallFlying,
        )

    private fun currentSpeedLimits(stepDistance: Double): SpearKillSpeedLimits = SpearKillSpeedLimits(
        targetSpeed = activeMovementTransport?.maxSpeed ?: movementConfiguration.targetSpeed.toDouble(),
        acceleration = movementConfiguration.acceleration.toDouble(),
        deceleration = movementConfiguration.deceleration.toDouble(),
        stepDistance = stepDistance,
        vanillaBudget = currentVanillaMovementBudget,
    )

    private fun currentSpeedProfile(stepDistance: Double): SpearKillSpeedProfile {
        val limits = currentSpeedLimits(stepDistance)
        val initialSpeed = if (speedController.active) {
            speedController.currentSpeed
        } else {
            player.deltaMovement.length().takeIf(Double::isFinite)?.coerceIn(0.0, limits.targetSpeed) ?: 0.0
        }
        return SpearKillSpeedProfile(initialSpeed, limits)
    }

    private fun beginSpearKillSpeedSession() {
        if (speedController.active) return
        speedController.begin(
            observedSpeed = player.deltaMovement.length(),
            targetSpeed = activeMovementTransport?.maxSpeed ?: movementConfiguration.targetSpeed.toDouble(),
        )
    }

    private fun previewSpearKillOutboundStep(): SpearKillSpeedStep {
        beginSpearKillSpeedSession()
        return speedController.preview(currentSpeedLimits(activeSpeedStepDistance))
            .also { lastRequestedStep = it }
    }

    private fun confirmSpearKillOutboundStep() {
        if (!speedController.active) return
        lastRequestedStep = speedController.confirmOutbound(
            currentSpeedLimits(activeSpeedStepDistance),
        )
    }

    private fun resetSpearKillSpeedSession() {
        speedController.reset()
        lastRequestedStep = SpearKillSpeedStep(0.0, 0.0)
        lastDeliveredMovement = Vec3.ZERO
        lastDeliveredOutboundMovement = Vec3.ZERO
        terminalBurstDeliveredMovementThisTick = Vec3.ZERO
    }

    private fun resolveSpearKillPacketSettings(prepareElytra: Boolean = false): SpearKillPacketSessionSettings {
        val packet = movementConfiguration.packet
        val routingMode = packetRoutingMode
        val aStarSettings = resolveSpearKillAStarSessionSettings(routingMode)
        val networkConfiguration = packet.networkOptimized
        val networkBudget = if (routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED) {
            networkOptimizer.resolve(
                observation = SpearKillNetworkObservation(
                    serverTps = ServerObserver.tps,
                    pingMillis = player.ping,
                ),
                settings = SpearKillNetworkSettings(
                    maxSpeed = networkConfiguration.maxSpeed.toDouble(),
                    minimumStepWaitTicks = maxOf(
                        packet.stepDelay,
                        networkConfiguration.minimumStepDelay,
                    ),
                    setbackBackoffTicks = networkConfiguration.setbackBackoff,
                ),
            )
        } else {
            null
        }
        val routeSpeed = minOf(
            movementConfiguration.targetSpeed.toDouble(),
            networkBudget?.maxSpeed ?: Double.POSITIVE_INFINITY,
        )
        val routeStepLimit = minOf(
            packet.stepDistance.toDouble(),
            networkBudget?.maxSpeed ?: Double.POSITIVE_INFINITY,
        )
        if (prepareElytra) {
            requestSpearKillPacketFallFlight()
        }
        return SpearKillPacketSessionSettings(
            transport = resolveSpearKillMovementTransport(
                configuredSpeed = routeSpeed,
                configuredStepLimit = routeStepLimit,
                elytraActive = isSpearKillElytraActive,
            ),
            stepWaitTicks = if (routingMode == SpearKillRoutingMode.INSTANT) {
                0
            } else {
                networkBudget?.stepWaitTicks ?: packet.stepDelay
            },
            routingMode = routingMode,
            aStar = aStarSettings,
            damageEvidenceWindowTicks = networkBudget?.damageEvidenceWindowTicks
                ?: SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS,
            setbackBackoffTicks = networkBudget?.setbackBackoffTicks
                ?: networkConfiguration.setbackBackoff,
            allowTerminalBurst = networkBudget?.allowTerminalBurst ?: true,
            instantMaxPackets = packet.instant.maxPackets,
        )
    }

    private fun resolveSpearKillAStarSessionSettings(
        routingMode: SpearKillRoutingMode,
    ): SpearKillAStarSessionSettings = if (routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED) {
        movementConfiguration.packet.networkOptimized.let { network ->
            SpearKillAStarSessionSettings(
                maxCost = network.maxCost,
                diagonal = network.diagonal,
                lineOfSightShortcuts = network.lineOfSightShortcuts,
            )
        }
    } else {
        movementConfiguration.packet.aStar.let { aStar ->
            SpearKillAStarSessionSettings(
                maxCost = aStar.maxCost,
                diagonal = aStar.diagonal,
                lineOfSightShortcuts = aStar.lineOfSightShortcuts,
            )
        }
    }

    private fun canStartSpearKillElytraFlight(): Boolean {
        val chestItem = player.getItemBySlot(EquipmentSlot.CHEST)
        return canStartSpearKillElytraFlight(
            isFallFlying = player.isFallFlying,
            hasFlyingAbility = player.abilities.flying,
            isPassenger = player.isPassenger,
            isOnClimbable = player.onClimbable(),
            isInWater = player.isInWater,
            hasLevitation = player.hasEffect(MobEffects.LEVITATION),
            isOnGround = player.onGround(),
            hasUsableElytra = chestItem.`is`(Items.ELYTRA) && !chestItem.nextDamageWillBreak(),
        )
    }

    private val hasUsableSpearKillElytra
        get() = player.getItemBySlot(EquipmentSlot.CHEST).let { chestItem ->
            chestItem.`is`(Items.ELYTRA) && !chestItem.nextDamageWillBreak()
        }

    private val isSpearKillElytraActive
        get() = elytraWhileMoving != SpearKillMovementAssistMode.NONE &&
            hasUsableSpearKillElytra && player.isFallFlying

    private fun requestSpearKillPacketFallFlight() {
        if (elytraWhileMoving != SpearKillMovementAssistMode.PACKET ||
            player.isFallFlying || !canStartSpearKillElytraFlight()
        ) {
            return
        }

        player.startFallFlying()
        network.send(
            ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING),
        )
    }

    private val shouldProtectFallDamage
        get() = shouldProtectSpearKillFallDamage(
            fallDistance = player.fallDistance.toDouble(),
            verticalVelocity = player.deltaMovement.y,
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
            tickCount = player.tickCount,
        )
    private val safeVirtualFallStep
        get() = spearKillSafeVirtualVerticalStep(
            player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        )

    private val isUsingSpear get() = player.isUsingItem && player.useItem.isSpear
    private val holdingSpear get() = player.mainHandItem.isSpear || player.offhandItem.isSpear
    private val isUseInputHeld get() = mc.options.keyUse.isPressedOnAny
    private val hasFightBotSpearRequest
        get() = fightBotSpearState == SpearKillFightBotState.Charging ||
            fightBotSpearState == SpearKillFightBotState.RouteActive
    private val hasKillAuraSpearRequest get() = killAuraSpearTarget != null
    private val hasKillAuraSpearUseRequest
        get() = hasKillAuraSpearRequest || killAuraSpearPrechargeActive
    private val hasAutomaticSpearRequest
        get() = hasFightBotSpearRequest || hasKillAuraSpearRequest
    private val isSpearUseRequested
        get() = isUseInputHeld || hasFightBotSpearRequest || hasKillAuraSpearUseRequest
    private val isAttackInputHeld get() = mc.options.keyAttack.isPressedOnAny
    private val movementAssistLease
        get() = resolveSpearKillMovementAssistLease(
            preparationActive = movementAssistPreparationActive,
            routeActive = hasActiveAttackPath,
            sneakMode = sneakWhileMoving,
            elytraMode = elytraWhileMoving,
            elytraUsable = hasUsableSpearKillElytra,
            elytraActive = activeMovementTransport?.elytraActive ?: isSpearKillElytraActive,
        )
    private val shouldMaintainSpearKillServerSneak
        get() = SpearKillServerSneak.shouldMaintain(
            requestedByRoute = movementAssistLease.serverSneak,
            serverSneaking = serverSneaking,
            isFallFlying = player.isFallFlying,
            currentHeight = player.boundingBox.ysize,
            crouchingHeight = player.getDimensions(Pose.CROUCHING).height.toDouble(),
        )
    private val physicalAttackRequest
        get() = isSpearKillAttackRequested(
            attackKeyDown = isAttackInputHeld,
            attackPressedRecently = mc.options.keyAttack.wasPressedRecently(SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS),
        )
    private val hasAttackRequest get() = manualAttackRequestLatched || physicalAttackRequest
    private val hasActivationRequest
        get() = hasFightBotSpearRequest || isSpearKillActivationSatisfied(
            activationMode = activationMode,
            attackRequested = hasAttackRequest,
            useKeyDown = isUseInputHeld,
            inheritedKillAuraRequest = hasKillAuraSpearRequest,
        )
    private fun updateManualAttackRequestLatch() {
        manualAttackRequestLatched = nextSpearKillManualAttackRequestLatch(
            activationMode = activationMode,
            holdingSpear = holdingSpear,
            isUsingSpear = isUsingSpear,
            useInputHeld = isUseInputHeld,
            wasLatched = manualAttackRequestLatched,
            attackPressed = physicalAttackRequest,
        )
    }

    private fun updateHoldUseLaunchCycle(
        launchStarted: Boolean = false,
        launchedTarget: LivingEntity? = null,
    ) {
        holdUseLaunchTarget = nextSpearKillHoldUseLaunchTarget(
            activationMode = activationMode,
            holdingSpear = holdingSpear,
            useInputHeld = isUseInputHeld,
            currentTarget = holdUseLaunchTarget,
            launchedTarget = launchedTarget,
            launchStarted = launchStarted,
        )
    }

    /** Keeps vanilla from releasing the continuous spear use started for KillAura inheritance. */
    @JvmStatic
    fun ownsKillAuraSpearUse(): Boolean {
        val currentPlayer = mc.player ?: return false

        return shouldPreserveSpearKillInheritedUse(
            startedUse = killAuraStartedSpearUse,
            isUsingItem = currentPlayer.isUsingItem,
            isSameHand = killAuraSpearUseHand == currentPlayer.usedItemHand,
            isUsingSpear = currentPlayer.useItem.isSpear,
        )
    }

    private fun updateKillAuraSpearUseRequest() {
        if (fightBotSpearTarget != null) {
            clearKillAuraSpearUse()
            return
        }

        val target = currentKillAuraSpearUseTarget()
        val precharge = target == null &&
            acceptsKillAuraDelegation &&
            ModuleKillAura.shouldPrechargeForSpearKill()
        if (target == null && !precharge) {
            clearKillAuraSpearUse()
            return
        }

        killAuraSpearTarget = target
        killAuraSpearPrechargeActive = precharge
        if (!maintainKillAuraSpearUse()) {
            clearKillAuraSpearUse()
        }
    }

    private fun currentKillAuraSpearUseTarget(): LivingEntity? {
        val ownedTarget = activeSpearKillTargetLock(
            lockedTarget = lockedAStarTarget,
            routeActive = ownsKillAuraRoute && hasActiveAttackPath,
            routePreparationActive = packetRoutePreparationActive &&
                pendingKillAuraTarget === lockedAStarTarget,
        )
        if (ownedTarget != null) return ownedTarget
        if (!acceptsKillAuraDelegation) return null
        return ModuleKillAura.targetForSpearKill()
    }

    private fun maintainKillAuraSpearUse(): Boolean {
        if (player.isUsingItem && KillAuraAutoBlock.enforcedBlockingHand != null) {
            KillAuraAutoBlock.stopBlocking(pauses = true)
        }

        return when (resolveSpearKillInheritedUseAction(
            requestActive = true,
            mainHandSpear = player.mainHandItem.isSpear,
            offhandSpear = player.offhandItem.isSpear,
            isUsingItem = player.isUsingItem,
            isUsingSpear = isUsingSpear,
        )) {
            SpearKillInheritedUseAction.NONE -> false
            SpearKillInheritedUseAction.KEEP_CURRENT_USE -> true
            SpearKillInheritedUseAction.START_MAIN_HAND -> startKillAuraSpearUse(InteractionHand.MAIN_HAND)
            SpearKillInheritedUseAction.START_OFF_HAND -> startKillAuraSpearUse(InteractionHand.OFF_HAND)
        }
    }

    private fun startKillAuraSpearUse(hand: InteractionHand): Boolean {
        if (useItem(hand) !is InteractionResult.Success) return false

        killAuraStartedSpearUse = true
        killAuraSpearUseHand = hand
        return true
    }

    private fun clearKillAuraSpearUse() {
        val currentPlayer = mc.player
        if (currentPlayer != null && shouldStopSpearKillInheritedUse(
                startedUse = killAuraStartedSpearUse,
                isUsingItem = currentPlayer.isUsingItem,
                isSameHand = killAuraSpearUseHand == currentPlayer.usedItemHand,
                isUsingSpear = currentPlayer.useItem.isSpear,
            )
        ) {
            mc.gameMode?.releaseUsingItem(currentPlayer)
        }

        killAuraSpearTarget = null
        killAuraSpearPrechargeActive = false
        killAuraStartedSpearUse = false
        killAuraSpearUseHand = null
    }

    /** KillAura releases only the SpearKill work it owns; the independently enabled module stays on. */
    internal fun onKillAuraDisabled() {
        val action = resolveSpearKillKillAuraReleaseAction(
            killAuraOwnsAttempt = killAuraOwnsAttempt,
            killAuraPreparationActive = packetRoutePreparationActive &&
                (pendingKillAuraTarget != null || killAuraSpearTarget != null),
            inheritedUseActive = hasKillAuraSpearUseRequest || killAuraStartedSpearUse,
        )
        when (action) {
            SpearKillKillAuraReleaseAction.NONE -> return
            SpearKillKillAuraReleaseAction.RELEASE_INHERITED_USE -> clearKillAuraSpearUse()
            SpearKillKillAuraReleaseAction.CANCEL_INHERITED_PREPARATION -> {
                cancelKillAuraPreparation()
                clearKillAuraSpearUse()
            }
            SpearKillKillAuraReleaseAction.CANCEL_INHERITED_ROUTE -> beginKillAuraOwnedReturn()
        }
    }

    private fun cancelKillAuraPreparation() {
        if (killAuraOwnsAttempt) abortSpearKillAttempt(KILL_AURA_DISABLED_REASON)
        movementAssistPreparationActive = false
        pendingKillAuraTarget = null
        clearAStarTargetLock()
    }

    private fun beginKillAuraOwnedReturn() {
        killAuraReturnActive = true
        val motionReturnPrepared = prepareKillAuraOwnedMotionReturn()

        abortSpearKillAttempt(KILL_AURA_DISABLED_REASON)
        clearKillAuraSpearUse()
        clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
        manualAttackRequestLatched = false
        movementAssistPreparationActive = false
        pendingKillAuraTarget = null
        previewTarget = null

        if (packetBootSession.active) {
            resetAttack()
        } else if (!motionReturnPrepared) {
            attackMovements.clear()
            player.deltaMovement = Vec3.ZERO
        }

        packetAStarAttackActive = false
        clearAStarRenderPath()
        clearAStarTargetLock()
        fallDamageDeliveryTracker.clear()
        synchronizeSpearKillServerSneak()
        killAuraReturnActive = hasSpearKillReturnWork
    }

    private fun prepareKillAuraOwnedMotionReturn(): Boolean {
        if (packetBootSession.active || attackMovements.isEmpty()) return false

        val attempt = attemptTracker.current
        val recovery = if (attempt == null) {
            attackMovements.toList()
        } else {
            spearKillMotionReturnTailOnDisable(
                queuedMovements = attackMovements.toList(),
                plannedOutboundSteps = attempt.plannedOutboundStepCount,
                confirmedOutboundSteps = attempt.outboundStepCount,
            )
        }
        if (recovery == null) return false

        attackMovements.clear()
        attackMovements.addAll(recovery)
        player.deltaMovement = Vec3.ZERO
        motionPacketHeading = null
        resetSpearKillSpeedSession()
        return attackMovements.isNotEmpty()
    }

    /** Collision pose the server will use after SpearKill's optional sneak input has arrived. */
    private fun spearKillServerCollisionBoxAt(position: Vec3): AABB = if (shouldMaintainSpearKillServerSneak) {
        player.getDimensions(Pose.CROUCHING).makeBoundingBox(position)
    } else {
        player.boundingBox.move(position.subtract(player.position()))
    }

    /** Matches the observer's near-ground envelope instead of claiming ground while airborne. */
    private fun isSpearKillPositionNearGround(position: Vec3): Boolean {
        if (!position.isFinite()) return false
        val probe = spearKillServerCollisionBoxAt(position)
            .inflate(-SPEAR_KILL_NEAR_GROUND_HORIZONTAL_INSET, 0.0, -SPEAR_KILL_NEAR_GROUND_HORIZONTAL_INSET)
            .move(0.0, -SPEAR_KILL_NEAR_GROUND_PROBE_DEPTH, 0.0)
        return withVanillaSpearKillBlockShapes { !world.noCollision(player, probe) }
    }

    private fun spearKillGroundProfile(
        origin: Vec3,
        movements: List<Vec3>,
    ): List<Boolean> {
        var position = origin
        return movements.map { movement ->
            position = position.add(movement)
            isSpearKillPositionNearGround(position)
        }
    }

    private fun spearKillPacketPosition(packet: ServerboundMovePlayerPacket): Vec3 {
        val fallback = player.position()
        return Vec3(
            packet.getX(fallback.x),
            packet.getY(fallback.y),
            packet.getZ(fallback.z),
        )
    }

    /**
     * Brackets a Packet route with server-visible sneaking without changing the local input or
     * rendering pose. The start packet is emitted before the first movement packet; later input
     * packets are forced to retain it until the route has physically returned or aborts.
     */
    private fun synchronizeSpearKillServerSneak() {
        if (mc.player == null || mc.level == null) {
            serverSneaking = false
            return
        }

        when (SpearKillServerSneak.nextAction(serverSneaking, shouldMaintainSpearKillServerSneak)) {
            SpearKillServerSneak.Action.START -> {
                serverSneaking = true
                sendSpearKillServerSneakInput(forceSneak = true)
            }

            SpearKillServerSneak.Action.STOP -> {
                serverSneaking = false
                sendSpearKillServerSneakInput(forceSneak = false)
            }

            SpearKillServerSneak.Action.NONE -> Unit
        }
    }

    private fun sendSpearKillServerSneakInput(forceSneak: Boolean) {
        val input = player.input.keyPresses
        if (usesViaFabricPlus && !isNewerThanOrEquals1_21_6) {
            if (forceSneak) {
                network.send1_21_5StartSneaking()
            } else if (!input.shift) {
                network.send1_21_5StopSneaking()
            }
            return
        }

        network.send(
            ServerboundPlayerInputPacket(
                Input(
                    input.forward,
                    input.backward,
                    input.left,
                    input.right,
                    input.jump,
                    forceSneak || input.shift,
                    input.sprint,
                ),
            ),
        )
    }

    /** Forces the first-person item-use pose when SpearKill wants the spear raised. */
    @JvmStatic
    fun shouldAnimateRaisedSpear(): Boolean = shouldAnimateSpearKillUseItem(
        shouldRaise = shouldRaiseSpearAnimation,
        isUsingItem = player.isUsingItem,
    )

    /** Hand that should render the SpearKill raise pose. */
    @JvmStatic
    fun raisedSpearHand(): InteractionHand? = spearKillRaisedHand(
        shouldRaise = shouldRaiseSpearAnimation,
        mainHandIsSpear = player.mainHandItem.isSpear,
        offHandIsSpear = player.offhandItem.isSpear,
        isUsingItem = player.isUsingItem,
        usedHand = player.usedItemHand,
    )

    /**
     * Client-only charged spear pose. Leaves the server use duration untouched; FastUse still owns
     * non-SpearKill spear visuals.
     */
    @JvmStatic
    fun getSpearAnimationTicks(hand: InteractionHand, originalTicks: Float): Float {
        if (!shouldRaiseSpearAnimation || raisedSpearHand() != hand) return originalTicks

        val spearStack = when (hand) {
            InteractionHand.MAIN_HAND -> player.mainHandItem
            InteractionHand.OFF_HAND -> player.offhandItem
        }
        val delayTicks = spearStack.get(DataComponents.KINETIC_WEAPON)?.delayTicks ?: return originalTicks
        return spearKillAnimationTicks(
            shouldRaise = true,
            delayTicks = delayTicks,
            originalTicks = originalTicks,
        )
    }

    @JvmStatic
    fun getSpearAnimationTicks(entity: LivingEntity, originalTicks: Float): Float =
        if (entity === player) {
            getSpearAnimationTicks(raisedSpearHand() ?: player.usedItemHand, originalTicks)
        } else {
            originalTicks
        }

    private fun currentPreviewGlow(): TargetGlowSelection? {
        if (!enabled || !Preview.enabled || Preview.mode.activeMode !== Preview.Glow || !isUsingSpear) return null
        val target = previewTarget ?: return null
        return TargetGlowSelection(target, Preview.Glow.glowColor, Preview.Glow.glowStyle.style)
    }

    private fun beginSpearKillAttempt(
        target: LivingEntity,
        routeMode: String,
        outboundSteps: Int,
        hitTicks: Int,
        terminalAuthorizationRequired: Boolean,
        targetSourceOverride: String? = null,
    ) {
        if (attemptTracker.current != null) {
            attemptTracker.complete()
        }
        val predictedHitTick = player.tickCount + hitTicks
        damageEvidenceTracker.clear()
        damageEvidenceTracker.arm(
            targetEntityId = target.id,
            predictedHitTick = predictedHitTick,
            windowTicks = packetSessionSettings?.damageEvidenceWindowTicks
                ?: SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS,
        )
        attemptRouteCompleted = false
        attemptTracker.begin(
            SpearKillAttemptPlan(
                targetIdentity = target.uuid.toString(),
                targetName = target.scoreboardName.ifBlank { "entity-${target.id}" },
                targetSource = targetSourceOverride ?: if (pendingKillAuraTarget === target) {
                    KILL_AURA_INHERITED_TARGET_SOURCE
                } else {
                    targetSource.name
                },
                plannedRouteMode = routeMode,
                plannedOutboundStepCount = outboundSteps,
                predictedHitTick = predictedHitTick,
                chargeTicks = player.ticksUsingItem,
                terminalAuthorizationRequired = terminalAuthorizationRequired,
            ),
        )
    }

    private fun recordRejectedSpearKillAttempt(
        target: LivingEntity,
        routeMode: String,
    ) {
        beginSpearKillAttempt(
            target = target,
            routeMode = routeMode,
            outboundSteps = 0,
            hitTicks = 0,
            terminalAuthorizationRequired = false,
        )
        damageEvidenceTracker.clear()
        attemptTracker.markBlocked()
        attemptTracker.complete()
    }

    private fun requestSpearKillAttemptCompletion() {
        if (attemptTracker.current == null) return
        attemptRouteCompleted = true
        if (!hasActiveAttackPath) {
            clearAStarTargetLock()
            packetAStarAttackActive = false
            clearAStarRenderPath()
            activeMovementTransport = null
            resetVirtualFallSafety()
        }
        if (!damageEvidenceTracker.isArmed) {
            attemptTracker.complete()
            attemptRouteCompleted = false
        }
        if (!hasActiveAttackPath && fightBotSpearState == SpearKillFightBotState.RouteActive) {
            clearFightBotSpearUse(SpearKillFightBotTerminal.Completion)
        }
    }

    private fun abortSpearKillAttempt(reason: String) {
        attemptTracker.abort(reason)
        damageEvidenceTracker.clear()
        attemptRouteCompleted = false
    }

    private fun updateSpearKillAttemptEvidence() {
        if (damageEvidenceTracker.expire(player.tickCount) && attemptRouteCompleted) {
            attemptTracker.complete()
            attemptRouteCompleted = false
        }

        val snapshot = attemptTracker.current ?: attemptTracker.lastCompleted
        debugParameter("Attempt Target") { snapshot?.targetName }
        debugParameter("Attempt Target Source") { snapshot?.targetSource }
        debugParameter("Attempt Route") { snapshot?.plannedRouteMode }
        debugParameter("Attempt Outbound Steps") {
            snapshot?.let { "${it.outboundStepCount}/${it.plannedOutboundStepCount}" }
        }
        debugParameter("Attempt Predicted Hit Tick") { snapshot?.predictedHitTick }
        debugParameter("Attempt Charge Ticks") { snapshot?.chargeTicks }
        debugParameter("Attempt Terminal Authorization Tick") { snapshot?.terminalAuthorizationTick }
        debugParameter("Attempt Setback") { snapshot?.setback }
        debugParameter("Attempt Blocked Edge") { snapshot?.blocked }
        debugParameter("Attempt Recovery") { snapshot?.recovery }
        debugParameter("Attempt Target Defeated") { snapshot?.defeated }
        debugParameter("Attempt Target Removed") { snapshot?.targetRemoved }
        debugParameter("Attempt Damage Evidence") { snapshot?.damageEvidence }
        debugParameter("Attempt Outcome") { snapshot?.outcome }
        debugParameter("Target Speed") { movementConfiguration.targetSpeed }
        debugParameter("Current Speed") { speedController.currentSpeed }
        debugParameter("Acceleration") { movementConfiguration.acceleration }
        debugParameter("Deceleration") { movementConfiguration.deceleration }
        debugParameter("Step Distance") { activeMovementTransport?.stepLimit ?: activeStepLimit }
        debugParameter("Estimated Vanilla Budget") { currentVanillaMovementBudget }
        debugParameter("Requested Displacement") { lastRequestedStep.stepLimit }
        debugParameter("Delivered Displacement") { lastDeliveredMovement.length() }
        debugParameter("Owned Movement Packets Previous Tick") { ownedMovementPacketsThisTick }
        debugParameter("Server Correction") {
            lastServerCorrectionTick?.let { player.tickCount - it <= 1 } ?: false
        }
        debugParameter("Look Vector") { player.lookAngle }
        debugParameter("Move Direction") { lastDeliveredMovement.normalize() }
        debugParameter("Estimated Attacker Kinetic Speed") {
            currentSpearKillKineticEstimate().attackerSpeed
        }
        debugParameter("Estimated Relative Kinetic Speed") {
            currentSpearKillKineticEstimate().relativeSpeed
        }
        debugParameter("Estimated Kinetic Bonus Damage") {
            currentSpearKillKineticDamageEstimate()?.bonusDamage
        }
        debugParameter("Kinetic Requirements Met") {
            currentSpearKillKineticDamageEstimate()?.meetsRequirements
        }
    }

    private fun currentSpearKillKineticEstimate(): SpearKillKineticSpeedEstimate {
        val targetMovement = lockedAStarTarget?.let { it.position().subtract(it.lastPos) } ?: Vec3.ZERO
        return estimateSpearKillKineticSpeed(lastDeliveredOutboundMovement, targetMovement, player.lookAngle)
    }

    private fun currentSpearKillKineticDamageEstimate(): SpearKillKineticDamageEstimate? {
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return null
        val requirements = spearKillKineticDamageRequirements(kineticWeapon) ?: return null
        val targetMovement = lockedAStarTarget?.let { it.position().subtract(it.lastPos) } ?: Vec3.ZERO
        return estimateSpearKillKineticDamage(
            deliveredMovement = lastDeliveredOutboundMovement,
            targetMovement = targetMovement,
            lookDirection = player.lookAngle,
            requirements = requirements,
        )
    }

    private fun spearKillKineticDamageRequirements(
        kineticWeapon: KineticWeapon,
    ): SpearKillKineticDamageRequirements? {
        val condition = kineticWeapon.damageConditions.orElse(null) ?: return null
        return SpearKillKineticDamageRequirements(
            minimumAttackerSpeed = condition.minSpeed.toDouble(),
            minimumRelativeSpeed = condition.minRelativeSpeed.toDouble(),
            damageMultiplier = kineticWeapon.damageMultiplier.toDouble(),
        )
    }

    private fun hasSpearKillKineticDamageRequirements(
        plan: DirectPacketRoutePlan,
        requirements: SpearKillKineticDamageRequirements,
    ): Boolean {
        val terminalMovement = plan.route.outboundMovements.lastOrNull() ?: return false
        return estimateSpearKillKineticDamage(
            deliveredMovement = terminalMovement,
            targetMovement = plan.targetSnapshot.velocity,
            lookDirection = terminalMovement,
            requirements = requirements,
        ).meetsRequirements
    }

    private fun resetAttack() {
        val motionAttemptActive = attackMovements.isNotEmpty()
        val retainAStarRenderPath = packetAStarAttackActive && packetBootSession.active
        previewTarget = null
        if (!retainAStarRenderPath) {
            packetAStarAttackActive = false
            clearAStarRenderPath()
            clearAStarTargetLock()
        }
        if (attackMovements.isNotEmpty()) player.deltaMovement = Vec3.ZERO
        attackMovements.clear()
        movementAssistPreparationActive = false
        if (motionAttemptActive) {
            abortSpearKillAttempt("motion-reset")
            resetSpearKillSpeedSession()
        }
        motionPacketHeading = null
        fallDamageDeliveryTracker.clear()
        beginSafeExactReturn()
        applyConfirmedPhysicalReturnPosition()
        if (!packetBootSession.active) {
            packetSessionSettings = null
            activeMovementTransport = null
        }
        synchronizeSpearKillServerSneak()
    }

    private fun clearVirtualAttack(
        finishFallSafety: Boolean = true,
        allowFallSafetyPacket: Boolean = true,
    ) {
        val abortSnap = spearKillSessionAbortSnapPosition(
            sessionOrigin = packetSessionOrigin,
            committedOffset = packetBootSession.committedOffset,
            physicalReturnConfigured = packetBootSession.physicalReturnConfigured,
        )
        clearVirtualMovementState()
        packetSessionOrigin = null
        packetSessionSettings = null
        activeMovementTransport = null
        physicalReturnPositioner.clear()
        packetRecoveryStallTicks = 0
        abortSnap?.let { origin ->
            player.setPos(origin)
            player.deltaMovement = Vec3.ZERO
        }
        if (finishFallSafety) {
            finishSpearKillFallSafety(
                finalPosition = abortSnap ?: player.position(),
                allowPacket = allowFallSafetyPacket && !player.isPassenger,
            )
        }
        resetSpearKillSpeedSession()
    }

    /** Discards the current virtual path while retaining the recovery origins and transport. */
    private fun clearVirtualMovementState() {
        previewTarget = null
        rejectedTargets.clear()
        packetAStarAttackActive = false
        directTerminalReplanInstalled = false
        clearAStarRenderPath()
        attackMovements.clear()
        motionPacketHeading = null
        BlinkManager.packetQueue.removeIf { snapshot ->
            val packet = snapshot.packet
            packet === plannedPacket || packet is ServerboundMovePlayerPacket &&
                (packet in virtualSessionPackets || packet in virtualFallGroundingPackets ||
                    packet in virtualFallStabilizationPackets)
        }
        virtualSessionPackets.clear()
        fallDamageDeliveryTracker.clear()
        fallSafetyLifecycle.invalidate()
        resetVirtualFallSafety()
        plannedPacket = null
        awaitingVanillaMovementPacket = false
        movementAssistPreparationActive = false
        packetBootSession.clear()
        clearAStarTargetLock()
    }

    private fun clearAStarRenderPath() {
        plannedAStarRenderPath = emptyList()
    }

    private fun beginVirtualFallSafety(
        outboundMovements: List<Vec3>,
        routeOrigin: Vec3 = player.position(),
    ): Boolean {
        val movements = outboundMovements + outboundMovements.asReversed().map { it.scale(-1.0) }
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = movements,
            outboundStepCount = outboundMovements.size,
            initialFallDistance = player.fallDistance.toDouble(),
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
            groundedSteps = spearKillGroundProfile(routeOrigin, movements),
            expectedNetMovement = Vec3.ZERO,
        )
        val plan = (result as? SpearKillServerFallSafetyPlanResult.Ready)?.plan ?: run {
            fallSafetyLifecycle.invalidate()
            resetVirtualFallSafety()
            return false
        }
        beginVirtualFallSafety(plan)
        return true
    }

    private fun beginVirtualFallSafety(plan: SpearKillServerFallSafetyPlan) {
        virtualFallGroundingPackets.clear()
        virtualFallStabilizationPackets.clear()
        virtualFallStabilizationDelivered = false
        fallSafetyLifecycle.begin(plan)
    }

    private fun replanVirtualFallSafety(plan: SpearKillServerFallSafetyPlan) {
        virtualFallGroundingPackets.clear()
        virtualFallStabilizationPackets.clear()
        virtualFallStabilizationDelivered = false
        fallSafetyLifecycle.replan(plan)
    }

    private fun createFutureFallSafetyPlan(
        routeOrigin: Vec3,
        movements: List<Vec3>,
        outboundStepCount: Int,
        expectedNetMovement: Vec3,
        initialFallDistance: Double = fallSafetyLifecycle.confirmedFallDistance,
    ): SpearKillServerFallSafetyPlan? {
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = movements,
            outboundStepCount = outboundStepCount,
            initialFallDistance = initialFallDistance,
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
            groundedSteps = spearKillGroundProfile(routeOrigin, movements),
            expectedNetMovement = expectedNetMovement,
        )
        return (result as? SpearKillServerFallSafetyPlanResult.Ready)?.plan
    }

    private fun createReplacementFallSafetyPlan(
        outboundMovements: List<Vec3>,
    ): SpearKillServerFallSafetyPlan? {
        if (!fallSafetyLifecycle.active || outboundMovements.isEmpty()) return null
        val committedOffset = packetBootSession.committedOffset
        val committedRecovery = if (committedOffset.lengthSqr() < SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED) {
            emptyList()
        } else {
            packetBootSession.exactRecoveryMovementsFrom(committedOffset) ?: return null
        }
        val futureMovements = buildList(outboundMovements.size * 2 + committedRecovery.size) {
            addAll(outboundMovements)
            outboundMovements.asReversed().forEach { add(it.scale(-1.0)) }
            addAll(committedRecovery)
        }
        return createFutureFallSafetyPlan(
            routeOrigin = packetSessionOrigin?.add(committedOffset) ?: player.position(),
            movements = futureMovements,
            outboundStepCount = outboundMovements.size,
            expectedNetMovement = committedOffset.scale(-1.0),
        )
    }

    /** Rechecks only delivery-confirmed movement before discarding any unfinished outbound route. */
    @Suppress("ReturnCount")
    private fun beginSafeExactReturn(initialFallDistance: Double? = null): Boolean {
        if (!packetBootSession.active) {
            fallSafetyLifecycle.invalidate()
            resetVirtualFallSafety()
            return true
        }
        if (packetBootSession.recovering && fallSafetyLifecycle.active && initialFallDistance == null) {
            return true
        }

        val committedOffset = packetBootSession.committedOffset
        if (committedOffset.lengthSqr() < SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED) {
            packetBootSession.beginExactReturn()
            fallSafetyLifecycle.invalidate()
            resetVirtualFallSafety()
            return !packetBootSession.active
        }
        if (!fallSafetyLifecycle.active && initialFallDistance == null) {
            return stopFailClosedPacketRoute()
        }
        val recoveryMovements = packetBootSession.exactRecoveryMovementsFrom(committedOffset)
            ?: return stopFailClosedPacketRoute()
        val plan = createFutureFallSafetyPlan(
            routeOrigin = packetSessionOrigin?.add(committedOffset) ?: player.position(),
            movements = recoveryMovements,
            outboundStepCount = 0,
            expectedNetMovement = committedOffset.scale(-1.0),
            initialFallDistance = initialFallDistance
                ?: fallSafetyLifecycle.confirmedFallDistance.takeIf { fallSafetyLifecycle.active }
                ?: player.fallDistance.toDouble(),
        ) ?: return stopFailClosedPacketRoute()

        replanVirtualFallSafety(plan)
        if (packetBootSession.recovering) {
            if (packetBootSession.physicalReturnConfigured) {
                packetBootSession.beginPhysicalExactRecoveryFrom(
                    committedOffset,
                    recoveryMovements,
                    activePacketStepWaitTicks,
                )
            } else {
                packetBootSession.beginPacketExactRecoveryFrom(
                    committedOffset,
                    recoveryMovements,
                    activePacketStepWaitTicks,
                )
            }
        } else {
            packetBootSession.beginExactReturn()
        }
        return packetBootSession.active
    }

    private fun stopFailClosedPacketRoute(): Boolean {
        fallSafetyLifecycle.invalidate()
        resetVirtualFallSafety()
        packetBootSession.clear()
        packetSetbackRecoveryAttempted = true
        attemptTracker.markBlocked()
        plannedPacket = null
        awaitingVanillaMovementPacket = false
        return false
    }

    private fun resetVirtualFallSafety() {
        virtualFallGroundingPackets.clear()
        virtualFallStabilizationPackets.clear()
        virtualFallStabilizationDelivered = false
    }

    private fun finishSpearKillFallSafety(
        finalPosition: Vec3?,
        allowPacket: Boolean,
        targetPlayer: Player = player,
    ) {
        val position = finalPosition?.takeIf {
            it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
        }
        val action = fallSafetyLifecycle.finish(
            finalPositionKnown = position != null,
            connectionOpen = allowPacket && mc.connection != null,
            physicallyNearGround = position?.let(::isSpearKillPositionNearGround) == true,
        )
        if (action.resetLocalFallDistance) {
            targetPlayer.resetFallDistance()
        }
        if (action.sendGroundedPacket && position != null) {
            sendSpearKillGroundingPacket(position, targetPlayer)
        }
        if (!fallSafetyLifecycle.active) resetVirtualFallSafety()
    }

    private fun sendSpearKillGroundingPacket(
        position: Vec3,
        targetPlayer: Player = player,
        heading: Rotation? = null,
    ) {
        if (!isSpearKillPositionNearGround(position)) {
            fallSafetyLifecycle.confirmGrounding(delivered = false)
            return
        }
        val packet = ServerboundMovePlayerPacket.PosRot(
            position.x,
            position.y,
            position.z,
            heading?.yaw ?: targetPlayer.yRot,
            heading?.pitch ?: targetPlayer.xRot,
            true,
            targetPlayer.horizontalCollision,
        )
        virtualFallGroundingPackets += packet
        network.send(packet)
        virtualFallGroundingPackets.remove(packet)
    }

    private fun clearAStarTargetLock() {
        lockedAStarTarget = null
        packetRoutePreparationActive = false
        plannedAStarApproach = null
        plannedAStarTargetPosition = null
        plannedAStarTargetVelocity = Vec3.ZERO
        aStarPlanTick = 0
    }

    private fun clearAttack(
        reason: String = "cleared",
        finishFallSafety: Boolean = true,
        allowFallSafetyPacket: Boolean = true,
    ) {
        killAuraReturnActive = false
        abortSpearKillAttempt(reason)
        clearKillAuraSpearUse()
        clearVirtualAttack(finishFallSafety, allowFallSafetyPacket)
        setbackGuard.clear()
        setbackRollback.clear()
        packetSetbackRecoveryAttempted = false
        pendingSetbackFallDistance = null
        pendingSetbackConfirmedOffset = null
        returnRecoveryTracker.clear()
        manualAttackRequestLatched = false
        clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
        resetSpearKillSpeedSession()
        ownedMovementPacketsThisTick = 0
        lastServerCorrectionTick = null
        synchronizeSpearKillServerSneak()
    }

    private fun isTargetCandidateEligible(entity: LivingEntity): Boolean =
        isTargetCandidateEligibleAt(entity, player.position())

    private fun rejectSpearKillTarget(target: LivingEntity) {
        rejectedTargets.reject(target, player.tickCount)
    }

    private fun isSpearKillTargetRejected(target: LivingEntity): Boolean =
        rejectedTargets.isRejected(target, player.tickCount)

    private fun isTargetCandidateEligibleAt(entity: LivingEntity, referencePosition: Vec3): Boolean =
        isSpearKillTargetCandidateEligible(
            isCombatSafe = entity.shouldBeAttacked(),
            isAlive = entity.isAlive && !entity.isRemoved,
            isInCurrentWorld = entity.level() === world,
            isWithinRange = referencePosition.distanceTo(entity.position()) in
                3.0..maxTargetDistance.toDouble(),
            isRejected = isSpearKillTargetRejected(entity),
        )

    private fun isLockedTargetEligibleAt(entity: LivingEntity, referencePosition: Vec3): Boolean =
        isSpearKillLockedTargetEligible(
            isCombatSafe = entity.shouldBeAttacked(),
            isAlive = entity.isAlive && !entity.isRemoved,
            isInCurrentWorld = entity.level() === world,
            distance = referencePosition.distanceTo(entity.position()),
            maximumDistance = maxTargetDistance.toDouble(),
            hysteresis = spearKillTargetSelectionMargin(),
            isRejected = isSpearKillTargetRejected(entity),
        )

    private fun findLookRayTarget(): Pair<LivingEntity, Double>? {
        val eye = player.eyePosition
        val lookDirection = player.lookAngle.normalize()
        val lookEnd = eye.add(lookDirection.scale(maxTargetDistance.toDouble()))
        val searchDistance = maxTargetDistance.toDouble()
        val selectionMargin = spearKillTargetSelectionMargin()
        val targetSearchBox = player.boundingBox.inflate(searchDistance + selectionMargin)
        var bestEntity: LivingEntity? = null
        var bestDistanceSquared = 0.0
        var bestPriority: SpearKillLookRayPriority? = null

        for (entity in world.getEntitiesOfClass(
            LivingEntity::class.java,
            targetSearchBox,
            ::isTargetCandidateEligible,
        )) {
            val distSq = player.distanceToSqr(entity)
            val dist = sqrt(distSq)
            if (dist !in 3.0..searchDistance) continue
            val priority = spearKillLookRayPriority(
                entityBox = entity.box,
                eye = eye,
                lookEnd = lookEnd,
                hitboxMargin = selectionMargin,
            ) ?: continue

            val currentBestPriority = bestPriority
            if (
                currentBestPriority != null &&
                compareSpearKillLookRayPriority(
                    left = priority,
                    right = currentBestPriority,
                    throughTerrain = packetRoutingSupportsAStar,
                ) >= 0
            ) {
                continue
            }

            bestEntity = entity
            bestDistanceSquared = distSq
            bestPriority = priority
        }

        val entity = bestEntity ?: return null
        // Selection is look-ray only. Direct travel / LOS still gate attack start below.
        return entity to calculateSpearKillTravel(sqrt(bestDistanceSquared))
    }

    private fun findCombatTarget(): Pair<LivingEntity, Double>? {
        val searchDistance = maxTargetDistance.toDouble()
        val target = world.getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(searchDistance + spearKillTargetSelectionMargin()),
            ::isTargetCandidateEligible,
        ).minWithOrNull(
            compareBy<LivingEntity>(RotationUtil::crosshairAngleToEntity)
                .thenBy { player.distanceToSqr(it) },
        ) ?: return null

        return target to calculateSpearKillTravel(player.distanceTo(target).toDouble())
    }

    private fun findSelectedTarget(): Pair<LivingEntity, Double>? {
        pendingKillAuraTarget = null
        if (acceptsKillAuraDelegation) {
            return (killAuraSpearTarget ?: ModuleKillAura.targetForSpearKill())
                ?.takeIf(::isTargetCandidateEligible)
                ?.let { target ->
                    pendingKillAuraTarget = target
                    target to calculateSpearKillTravel(player.distanceTo(target).toDouble())
                }
        }

        return selectSpearKillTargetForSource(
            targetSource = targetSource,
            lookRayTarget = ::findLookRayTarget,
            combatTarget = ::findCombatTarget,
        )
    }

    private fun calculateSpearKillTravel(distance: Double): Double {
        val profile = currentSpeedProfile(activeSpeedStepDistance)
        return calculateSpearKillProfiledTravel(distance, profile).distance
    }

    private fun findSpearKillChainCandidates(
        defeatedTarget: LivingEntity,
        chainAnchor: Vec3,
    ): List<LivingEntity> {
        val radius = maxTargetDistance.toDouble() + spearKillTargetSelectionMargin()
        val searchBox = AABB.ofSize(chainAnchor, radius * 2.0, radius * 2.0, radius * 2.0)
        return world.getEntitiesOfClass(LivingEntity::class.java, searchBox) { candidate ->
            candidate !== defeatedTarget && isTargetCandidateEligibleAt(candidate, chainAnchor)
        }
    }

    private fun lockedAStarTargetCandidate(): Pair<LivingEntity, Double>? {
        val target = lockedAStarTarget ?: return null
        val routePosition = packetSessionOrigin
            ?.takeIf { packetBootSession.active }
            ?.add(packetBootSession.committedOffset)
            ?: player.position()
        if (!isLockedTargetEligibleAt(target, routePosition)) return null

        val distance = routePosition.distanceTo(target.position())
        return target to calculateSpearKillTravel(distance)
    }

    private fun isDirectSpearKillTargetEligible(entity: LivingEntity, travel: Double): Boolean {
        val eye = player.eyePosition
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = eye,
            predictedTargetPosition = entity.position(),
            targetEyeOffset = entity.eyePosition.subtract(entity.position()),
            fallbackDirection = player.lookAngle,
        )
        val hasVisibleAttackRay = hasVisibleSpearKillAttackRay(
            eye = eye,
            direction = entity.eyePosition.subtract(eye),
            targetBox = entity.boundingBox,
            range = maxTargetDistance.toDouble(),
        )
        val hasClearDirectTravel = hasClearSpearKillDirectTravel(direction, travel)

        return isSpearKillAStarTargetEligible(
            hasLineOfSight = hasVisibleAttackRay,
            hasClearDirectTravel = hasClearDirectTravel,
            packetAStarEnabled = packetRoutingSupportsAStar,
            packetMovementMode = usesPacketMovementMode,
        )
    }

    private fun hasClearSpearKillDirectTravel(direction: Vec3, travel: Double): Boolean {
        val normalizedDirection = direction.normalize()
        if (normalizedDirection.lengthSqr() == 0.0) return false

        val origin = player.position()
        val destination = origin.add(normalizedDirection.scale(travel))
        return createFastSpearKillSegmentValidator(
            origin = origin,
            playerBoundingBox = spearKillServerCollisionBoxAt(origin),
        ).isClear(origin, destination)
    }

    private fun createFastSpearKillSegmentValidator(
        origin: Vec3,
        playerBoundingBox: AABB,
    ) = createSpearKillAStarSegmentValidator(
        origin = origin,
        playerBoundingBox = playerBoundingBox,
        hasHitboxRaycastCollision = ::hasSpearKillHitboxRaycastCollision,
    )

    private fun createServerValidatedSpearKillDirectPacketSegmentValidator(
        origin: Vec3,
        playerBoundingBox: AABB,
    ): SpearKillAStarSegmentValidator {
        val fastValidator = createFastSpearKillSegmentValidator(origin, playerBoundingBox)
        val serverValidator = createServerMovementSpearKillSegmentValidator(origin, playerBoundingBox)
        return SpearKillAStarSegmentValidator { from, to ->
            fastValidator.isClear(from, to) && serverValidator.isClear(from, to)
        }
    }

    private fun createServerMovementSpearKillSegmentValidator(
        origin: Vec3,
        playerBoundingBox: AABB,
    ) = createSpearKillServerPacketSegmentValidator(
        origin = origin,
        playerBoundingBox = playerBoundingBox,
        hasDestinationCollision = { box ->
            withVanillaSpearKillBlockShapes { !world.noCollision(player, box) }
        },
        resolveMovement = { box, movement ->
            withVanillaSpearKillBlockShapes {
                resolveSpearKillServerPacketMovement(player, box, movement)
            }
        },
    )

    /** Collects the live vanilla collision shapes, then casts the full player hitbox through them. */
    private fun hasSpearKillHitboxRaycastCollision(
        playerBoundingBox: AABB,
        movement: Vec3,
    ): Boolean = withVanillaSpearKillBlockShapes {
        if (playerBoundingBox.hasNaN() ||
            !movement.x.isFinite() || !movement.y.isFinite() || !movement.z.isFinite()
        ) {
            return@withVanillaSpearKillBlockShapes true
        }

        // This is broad-phase partitioning only: each span is still a continuous full-hitbox
        // raycast. It prevents a 500-block diagonal target check from asking the world for every
        // shape inside one huge square while retaining exact collision at every point on the ray.
        val movementLength = movement.length()
        if (!movementLength.isFinite()) {
            return@withVanillaSpearKillBlockShapes true
        }
        val spanCount = ceil(movementLength / SPEAR_KILL_HITBOX_RAYCAST_MAX_SPAN_LENGTH)
            .toInt()
            .coerceAtLeast(1)
        val spanMovement = movement.scale(1.0 / spanCount)
        (0 until spanCount).any { spanIndex ->
            val spanBox = playerBoundingBox.move(spanMovement.scale(spanIndex.toDouble()))
            val sweptBox = spanBox.expandTowards(spanMovement)
            val collisionBoxes = buildList {
                world.getBlockCollisions(player, sweptBox).forEach { shape ->
                    addAll(shape.toAabbs())
                }
                world.getEntityCollisions(player, sweptBox).forEach { shape ->
                    addAll(shape.toAabbs())
                }
                world.worldBorder.takeIf { it.isInsideCloseToBorder(player, sweptBox) }
                    ?.collisionShape
                    ?.toAabbs()
                    ?.let(::addAll)
            }
            hasSpearKillHitboxRaycastCollision(spanBox, spanMovement, collisionBoxes)
        }
    }

    private fun hasVisibleSpearKillAttackRay(
        eye: Vec3,
        direction: Vec3,
        targetBox: AABB,
        range: Double,
        lineOfSight: (Vec3, Vec3) -> Boolean = { from, to -> hasLineOfSight(from, to, player) },
    ): Boolean {
        val hitPoint = findSpearKillAttackHitPoint(eye, direction, targetBox, range) ?: return false
        return lineOfSight(eye, hitPoint)
    }

    private fun createAttackMovement(target: LivingEntity, distance: Double): SpearKillAttackStartResult {
        if (!usesPacketMovementMode) {
            beginSpearKillSpeedSession()
            return startSpearKillMotionAttack(target, distance)
        }

        val settings = resolveSpearKillPacketSettings(prepareElytra = true)
        activeMovementTransport = settings.transport
        beginSpearKillSpeedSession()
        val startResult = startSpearKillPacketRoute(
            mode = settings.routingMode,
            startDirect = {
                startDirectPacketAttack(
                    target = target,
                    distance = distance,
                    settings = settings,
                    routeMode = settings.routingMode.directRouteLabel(),
                )
            },
            startAStar = {
                motionPacketHeading = null
                packetSessionSettings = settings
                startAStarPacketAttack(
                    target = target,
                    settings = settings,
                    routeMode = settings.routingMode.aStarRouteLabel(),
                )
            },
        )
        if (startResult != SpearKillAttackStartResult.STARTED) {
            packetSessionSettings = null
            activeMovementTransport = null
            resetSpearKillSpeedSession()
        }
        return startResult
    }

    private fun startSpearKillMotionAttack(target: LivingEntity, distance: Double): SpearKillAttackStartResult {
        packetSessionSettings = null
        clearAStarRenderPath()
        requestSpearKillPacketFallFlight()
        val transport = resolveSpearKillMovementTransport(
            configuredSpeed = movementConfiguration.targetSpeed.toDouble(),
            configuredStepLimit = movementConfiguration.motion.stepDistance.toDouble(),
            elytraActive = isSpearKillElytraActive,
        )
        activeMovementTransport = transport
        val movements = createDirectAttackMovements(
            target = target,
            distance = distance,
            profile = currentSpeedProfile(transport.stepLimit),
        )
        val outboundSteps = (movements.size - 1) / 2
        attackMovements.addAll(movements)
        beginSpearKillAttempt(
            target = target,
            routeMode = "Direct",
            outboundSteps = outboundSteps,
            hitTicks = outboundSteps,
            terminalAuthorizationRequired = false,
        )
        return SpearKillAttackStartResult.STARTED
    }

    @Suppress("LongMethod", "ReturnCount")
    private fun startDirectPacketAttack(
        target: LivingEntity,
        distance: Double,
        settings: SpearKillPacketSessionSettings,
        routeMode: String,
    ): SpearKillAttackStartResult {
        val origin = player.position()
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
            ?: return SpearKillAttackStartResult.RETRY_LATER
        val kineticRequirements = spearKillKineticDamageRequirements(kineticWeapon)
            ?: return SpearKillAttackStartResult.REJECTED
        val plan = calculateDirectPacketRoute(
            target = target,
            routeOrigin = origin,
            travel = distance,
            settings = settings,
            sessionOrigin = origin,
        ) ?: return SpearKillAttackStartResult.BLOCKED
        if (!hasSpearKillKineticDamageRequirements(plan, kineticRequirements)) {
            return SpearKillAttackStartResult.REJECTED
        }
        val route = plan.route
        val instantBurst = if (settings.routingMode == SpearKillRoutingMode.INSTANT) {
            buildSpearKillInstantPacketBurst(route, settings.instantMaxPackets)
                ?: return SpearKillAttackStartResult.BLOCKED
        } else {
            null
        }
        if (!isServerAcceptedSpearKillRoute(
                sessionOrigin = origin,
                routeOrigin = origin,
                route = route,
                routingAttempt = SpearKillRoutingAttempt.DIRECT,
            )
        ) {
            return SpearKillAttackStartResult.BLOCKED
        }
        val damageUseDuration = kineticWeapon.computeDamageUseDuration()
        val hitTicks = spearKillDirectRouteHitTicks(
            routingMode = settings.routingMode,
            outboundTickCount = route.outboundTickCount,
            stepWaitTicks = settings.stepWaitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
        )
        if (instantBurst != null) {
            when (resolveSpearKillInstantChargeAction(
                ticksUsingItem = player.ticksUsingItem,
                delayTicks = kineticWeapon.delayTicks,
                damageUseDuration = damageUseDuration,
                hitTicks = hitTicks,
            )) {
                SpearKillInstantChargeAction.READY -> Unit
                SpearKillInstantChargeAction.REFRESH -> return SpearKillAttackStartResult.RETRY_LATER
                SpearKillInstantChargeAction.INVALID -> return SpearKillAttackStartResult.REJECTED
            }
        } else if (!hasSpearKillRefreshableTerminalDamageWindow(
                delayTicks = kineticWeapon.delayTicks,
                damageUseDuration = damageUseDuration,
                terminalStepCount = route.terminalBurstSteps.coerceAtLeast(1),
                stepWaitTicks = settings.stepWaitTicks,
                strikeHoldTicks = settings.strikeHoldTicks,
            )
        ) {
            return SpearKillAttackStartResult.REJECTED
        }
        if (!beginVirtualFallSafety(
                outboundMovements = route.outboundMovements,
                routeOrigin = origin,
            )
        ) {
            return SpearKillAttackStartResult.REJECTED
        }

        motionPacketHeading = null
        packetSessionSettings = settings
        packetAStarAttackActive = false
        clearAStarRenderPath()
        plannedAStarApproach = null
        packetSessionOrigin = origin
        physicalReturnPositioner.clear()
        returnRecoveryTracker.begin(origin)
        if (instantBurst != null) {
            startSpearKillInstantPacketSession(packetBootSession, instantBurst)
        } else {
            startSpearKillDirectPacketSession(
                session = packetBootSession,
                route = route,
                stepWaitTicks = settings.stepWaitTicks,
                strikeHoldTicks = settings.strikeHoldTicks,
            )
        }
        directTerminalReplanInstalled = false
        lockedAStarTarget = target
        plannedAStarTargetPosition = plan.targetSnapshot.observedPosition
        plannedAStarTargetVelocity = plan.targetSnapshot.velocity
        aStarPlanTick = player.tickCount
        beginSpearKillAttempt(
            target = target,
            routeMode = routeMode,
            outboundSteps = route.outboundMovements.size,
            hitTicks = hitTicks,
            terminalAuthorizationRequired = instantBurst == null,
        )
        return SpearKillAttackStartResult.STARTED
    }

    @Suppress("LongMethod", "ReturnCount")
    private fun startAStarPacketAttack(
        target: LivingEntity,
        settings: SpearKillPacketSessionSettings,
        routeMode: String,
    ): SpearKillAttackStartResult {
        clearAStarRenderPath()
        val origin = player.position()
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        val damageUseDuration = kineticWeapon?.computeDamageUseDuration()
        val plan = calculateAStarAttackPlan(
            target = target,
            routeOrigin = origin,
            sessionOrigin = origin,
            settings = settings,
        )
        val serverRouteAccepted = plan == null || kineticWeapon == null || isServerAcceptedSpearKillRoute(
            sessionOrigin = origin,
            routeOrigin = origin,
            route = plan.packetRoute,
            routingAttempt = SpearKillRoutingAttempt.A_STAR,
        )
        val startResult = classifySpearKillAStarStartFailure(
            routeFound = plan != null && kineticWeapon != null,
            serverRouteAccepted = serverRouteAccepted,
            hasRefreshableTerminalDamageWindow = plan != null &&
                kineticWeapon != null &&
                damageUseDuration != null &&
                hasSpearKillRefreshableTerminalDamageWindow(
                    delayTicks = kineticWeapon.delayTicks,
                    damageUseDuration = damageUseDuration,
                    terminalStepCount = plan.terminalSuffixCount,
                    stepWaitTicks = settings.stepWaitTicks,
                    strikeHoldTicks = settings.strikeHoldTicks,
                ),
        )
        if (startResult != SpearKillAttackStartResult.STARTED || plan == null) {
            packetSessionSettings = null
            return startResult
        }
        if (!beginVirtualFallSafety(plan.packetRoute.outboundMovements, origin)) {
            packetSessionSettings = null
            return SpearKillAttackStartResult.BLOCKED
        }

        packetAStarAttackActive = true
        directTerminalReplanInstalled = false
        packetSessionOrigin = origin
        physicalReturnPositioner.clear()
        returnRecoveryTracker.begin(origin)
        packetBootSession.startPhysicalReturn(
            path = plan.packetRoute.roundTripMovements,
            outboundSteps = plan.packetRoute.outboundMovements.size,
            strikeHoldTicks = settings.strikeHoldTicks,
            stepWaitTicks = settings.stepWaitTicks,
            preStrikeHoldTicks = plan.preStrikeHoldTicks,
            terminalSuffixSteps = plan.terminalSuffixCount,
            requireTerminalAuthorization = true,
        )
        plannedAStarRenderPath = plan.renderPath
        plannedAStarApproach = plan.approach
        plannedAStarTargetPosition = plan.targetPosition
        plannedAStarTargetVelocity = plan.targetVelocity
        aStarPlanTick = player.tickCount
        beginSpearKillAttempt(
            target = target,
            routeMode = routeMode,
            outboundSteps = plan.packetRoute.outboundMovements.size,
            hitTicks = plan.schedule.hitTick,
            terminalAuthorizationRequired = true,
        )
        return SpearKillAttackStartResult.STARTED
    }

    private fun createDirectAttackMovements(
        target: LivingEntity,
        distance: Double,
        profile: SpearKillSpeedProfile,
    ): List<Vec3> {
        val stepCount = buildSpearKillProfiledMovements(Vec3(1.0, 0.0, 0.0), distance, profile).size
        val ticks = if (usesPacketMovementMode) {
            spearKillPacketTravelTicks(stepCount, activePacketStepWaitTicks)
        } else {
            stepCount
        }

        val predictedTargetPosition = PositionExtrapolation.getBestForEntity(target)
            .getPositionInTicks(ticks.toDouble())
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = player.eyePosition,
            predictedTargetPosition = predictedTargetPosition,
            targetEyeOffset = target.eyePosition.subtract(target.position()),
            fallbackDirection = player.lookAngle,
        )

        return buildSpearKillProfiledAttackMovements(direction, distance, profile)
    }

    @Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
    private fun createAStarAttackPlan(
        target: SpearKillRouteTargetSnapshot,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
        stepWaitTicks: Int,
        strikeHoldTicks: Int,
        aStar: SpearKillAStarSessionSettings,
        playerSnapshot: SpearKillPlayerRouteSnapshot,
        collisionSnapshot: SpearKillCollisionSnapshot,
    ): AStarAttackPlan? {
        val eyeOffset = playerSnapshot.eyeOffset
        val routeEyePosition = routeOrigin.add(eyeOffset)
        val speedProfile = playerSnapshot.speedProfile
        val effectiveMaxSpeed = speedProfile.maximumStepLimit
        val terminalLungeDistance = effectiveMaxSpeed
        val seedStepCount = calculateSpearKillProfiledTravel(
            distance = routeOrigin.distanceTo(target.observedPosition),
            profile = speedProfile,
        ).stepCount
        val seedPrediction = target.predict(spearKillPacketTravelTicks(seedStepCount, stepWaitTicks))
        val sessionBoundingBox = playerSnapshot.sessionBoundingBox
        val segmentValidator = collisionSnapshot.createSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
        val routePlanner = SpearKillAStarRoutePlanner(
            allowDiagonal = aStar.diagonal,
            maxCost = aStar.maxCost,
            isPassable = collisionSnapshot::isPassable,
            canTraverse = segmentValidator::isClear,
        )
        val preferredDirection = calculateSpearKillAttackDirection(
            playerEyePosition = routeEyePosition,
            predictedTargetPosition = seedPrediction.position,
            targetEyeOffset = seedPrediction.eyePosition.subtract(seedPrediction.position),
            fallbackDirection = playerSnapshot.lookAngle,
        )
        val approaches = filterSpearKillAStarApproachesByTerminalClearance(
            approaches = createSpearKillAStarAttackApproachCandidates(
                targetBox = seedPrediction.boundingBox,
                targetEyePosition = seedPrediction.eyePosition,
                playerEyeOffset = eyeOffset,
                preferredDirection = preferredDirection,
                terminalLungeDistance = terminalLungeDistance,
            ),
            segmentValidator = segmentValidator,
        )

        var best: AStarAttackPlan? = null
        for (approach in approaches) {
            val lowerBoundHitTick = spearKillAStarCandidateLowerBoundHitTick(
                routeOrigin = routeOrigin,
                plannerGoal = approach.plannerGoal,
                stepLimit = effectiveMaxSpeed,
                terminalLungeDistance = terminalLungeDistance,
                stepWaitTicks = stepWaitTicks,
                strikeHoldTicks = strikeHoldTicks,
            )
            if (best != null && lowerBoundHitTick > best.schedule.hitTick) continue

            val candidate = buildTimedAStarAttackPlanForApproach(
                approach = approach,
                routeOrigin = routeOrigin,
                routePlanner = routePlanner,
                segmentValidator = segmentValidator,
                effectiveMaxSpeed = effectiveMaxSpeed,
                speedProfile = speedProfile,
                seedPrediction = seedPrediction,
                eyeOffset = eyeOffset,
                lineOfSightShortcuts = aStar.lineOfSightShortcuts,
                target = target,
                stepWaitTicks = stepWaitTicks,
                strikeHoldTicks = strikeHoldTicks,
                terminalLungeDistance = terminalLungeDistance,
                safeVerticalStep = playerSnapshot.safeVerticalStep,
                hasAttackRay = collisionSnapshot::isRayClear,
            ) ?: continue

            if (best == null || isBetterSpearKillTimedAStarPlan(
                    candidateHitTick = candidate.schedule.hitTick,
                    candidateOutboundSteps = candidate.packetRoute.outboundMovements.size,
                    bestHitTick = best.schedule.hitTick,
                    bestOutboundSteps = best.packetRoute.outboundMovements.size,
                )
            ) {
                best = candidate
            }
        }
        return best
    }

    @Suppress("LongParameterList", "LongMethod", "ReturnCount")
    private fun buildTimedAStarAttackPlanForApproach(
        approach: SpearKillAStarAttackApproach,
        routeOrigin: Vec3,
        routePlanner: SpearKillAStarRoutePlanner,
        segmentValidator: SpearKillAStarSegmentValidator,
        effectiveMaxSpeed: Double,
        speedProfile: SpearKillSpeedProfile,
        seedPrediction: SpearKillRouteTargetPrediction,
        eyeOffset: Vec3,
        lineOfSightShortcuts: Boolean,
        target: SpearKillRouteTargetSnapshot,
        stepWaitTicks: Int,
        strikeHoldTicks: Int,
        terminalLungeDistance: Double,
        safeVerticalStep: Double,
        hasAttackRay: (Vec3, Vec3) -> Boolean,
    ): AStarAttackPlan? {
        val seedSpatialPlan = buildSpatialAStarAttackPlanForApproach(
            approach = approach,
            routeOrigin = routeOrigin,
            routePlanner = routePlanner,
            segmentValidator = segmentValidator,
            effectiveMaxSpeed = effectiveMaxSpeed,
            speedProfile = speedProfile,
            lineOfSightShortcuts = lineOfSightShortcuts,
            safeVerticalStep = safeVerticalStep,
        ) ?: return null

        val seedSchedule = buildSpearKillAStarPathSchedule(
            outboundStepCount = seedSpatialPlan.packetRoute.outboundMovements.size,
            stepWaitTicks = stepWaitTicks,
            terminalSuffixCount = seedSpatialPlan.terminalSuffixCount,
            strikeHoldTicks = strikeHoldTicks,
        ) ?: return null
        val hitPrediction = target.predict(seedSchedule.hitTick)

        val refinedSpatialPlan = if (shouldRefineSpearKillAStarApproach(
                seedPrediction.position,
                hitPrediction.position,
            )
        ) {
            val approachDirection = approach.terminalWaypoint.subtract(approach.plannerGoal)
            createSpearKillAStarAttackApproachCandidates(
                targetBox = hitPrediction.boundingBox,
                targetEyePosition = hitPrediction.eyePosition,
                playerEyeOffset = eyeOffset,
                preferredDirection = approachDirection,
                terminalLungeDistance = terminalLungeDistance,
                bearingCount = 1,
            ).firstOrNull()?.takeIf { candidate ->
                segmentValidator.isClear(candidate.plannerGoal, candidate.terminalWaypoint)
            }?.let { refinedApproach ->
                buildSpatialAStarAttackPlanForApproach(
                    approach = refinedApproach,
                    routeOrigin = routeOrigin,
                    routePlanner = routePlanner,
                    segmentValidator = segmentValidator,
                    effectiveMaxSpeed = effectiveMaxSpeed,
                    speedProfile = speedProfile,
                    lineOfSightShortcuts = lineOfSightShortcuts,
                    safeVerticalStep = safeVerticalStep,
                )
            }
        } else {
            null
        }

        val preferredSpatialPlan = refinedSpatialPlan ?: seedSpatialPlan
        return timeSpatialAStarAttackPlan(
            spatialPlan = preferredSpatialPlan,
            eyeOffset = eyeOffset,
            target = target,
            stepWaitTicks = stepWaitTicks,
            strikeHoldTicks = strikeHoldTicks,
            hasAttackRay = hasAttackRay,
        ) ?: refinedSpatialPlan?.let {
            timeSpatialAStarAttackPlan(
                spatialPlan = seedSpatialPlan,
                eyeOffset = eyeOffset,
                target = target,
                stepWaitTicks = stepWaitTicks,
                strikeHoldTicks = strikeHoldTicks,
                hasAttackRay = hasAttackRay,
            )
        }
    }

    @Suppress("LongParameterList", "ReturnCount")
    private fun buildSpatialAStarAttackPlanForApproach(
        approach: SpearKillAStarAttackApproach,
        routeOrigin: Vec3,
        routePlanner: SpearKillAStarRoutePlanner,
        segmentValidator: SpearKillAStarSegmentValidator,
        effectiveMaxSpeed: Double,
        speedProfile: SpearKillSpeedProfile,
        lineOfSightShortcuts: Boolean,
        safeVerticalStep: Double,
    ): AStarSpatialPlan? {
        val route = resolveSpearKillAStarApproachRoute(
            origin = routeOrigin,
            plannerGoal = approach.plannerGoal,
            segmentValidator = segmentValidator,
            routeSearch = { routePlanner.plan(routeOrigin, approach.plannerGoal) },
        ) ?: return null
        val compactedRoute = compactSpearKillAStarWaypoints(
            origin = routeOrigin,
            waypoints = route,
            maxSpeed = effectiveMaxSpeed,
            segmentValidator = segmentValidator,
            lineOfSightShortcuts = lineOfSightShortcuts,
        )
        val outboundWaypoints = compactedRoute + approach.plannerGoal + approach.terminalWaypoint
        val packetRoute = buildSpearKillProfiledAStarPacketRoute(
            origin = routeOrigin,
            outboundWaypoints = outboundWaypoints,
            profile = speedProfile,
            segmentValidator = segmentValidator,
            maxVerticalStep = safeVerticalStep,
        ) ?: return null
        if (!isSpearKillAStarTerminalStepValid(packetRoute.outboundMovements, approach, effectiveMaxSpeed)) {
            return null
        }
        val terminalSuffixCount = countSpearKillAStarTerminalSuffix(
            outboundMovements = packetRoute.outboundMovements,
            approach = approach,
            stepLimit = effectiveMaxSpeed,
        ) ?: return null

        return AStarSpatialPlan(
            approach = approach,
            packetRoute = packetRoute,
            renderPath = buildSpearKillAStarRenderPath(routeOrigin, outboundWaypoints),
            terminalSuffixCount = terminalSuffixCount,
        )
    }

    @Suppress("LongParameterList", "ReturnCount")
    private fun timeSpatialAStarAttackPlan(
        spatialPlan: AStarSpatialPlan,
        eyeOffset: Vec3,
        target: SpearKillRouteTargetSnapshot,
        stepWaitTicks: Int,
        strikeHoldTicks: Int,
        hasAttackRay: (Vec3, Vec3) -> Boolean,
    ): AStarAttackPlan? {
        val schedule = buildSpearKillAStarPathSchedule(
            outboundStepCount = spatialPlan.packetRoute.outboundMovements.size,
            stepWaitTicks = stepWaitTicks,
            terminalSuffixCount = spatialPlan.terminalSuffixCount,
            strikeHoldTicks = strikeHoldTicks,
        ) ?: return null
        val hitPrediction = target.predict(schedule.hitTick)
        if (!hasValidAStarTerminalAttackRay(
                targetBox = hitPrediction.boundingBox,
                eyeOffset = eyeOffset,
                approach = spatialPlan.approach,
                lineOfSight = hasAttackRay,
            )
        ) {
            return null
        }
        return AStarAttackPlan(
            approach = spatialPlan.approach,
            packetRoute = spatialPlan.packetRoute,
            renderPath = spatialPlan.renderPath,
            targetPosition = hitPrediction.observedPosition,
            targetVelocity = target.velocity,
            schedule = schedule,
            preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
            terminalSuffixCount = spatialPlan.terminalSuffixCount,
        )
    }

    private fun hasValidAStarTerminalAttackRay(
        targetBox: AABB,
        eyeOffset: Vec3,
        approach: SpearKillAStarAttackApproach,
        lineOfSight: (Vec3, Vec3) -> Boolean = { from, to -> hasLineOfSight(from, to, player) },
    ): Boolean {
        val virtualEyePosition = approach.terminalWaypoint.add(eyeOffset)
        val attackHitPoint = findSpearKillTerminalAttackHitPoint(
            eye = virtualEyePosition,
            terminalMovement = approach.terminalWaypoint.subtract(approach.plannerGoal),
            targetBox = targetBox,
            range = SPEAR_KILL_ATTACK_RAY_RANGE,
        ) ?: return false
        val hitDistance = virtualEyePosition.distanceTo(attackHitPoint)
        return hitDistance in SPEAR_KILL_MIN_ATTACK_RAY_RANGE..SPEAR_KILL_ATTACK_RAY_RANGE &&
            lineOfSight(virtualEyePosition, attackHitPoint)
    }

    private fun followLockedMotionTarget() {
        if (usesPacketMovementMode || attackMovements.isEmpty()) return

        val target = lockedAStarTarget ?: return
        val attempt = attemptTracker.current ?: return
        if (attempt.outboundStepCount < attempt.plannedOutboundStepCount || target.isAlive) return

        if (target.isRemoved) {
            attemptTracker.markTargetRemoved()
        }
        attemptTracker.markDefeated()
        rejectedTargets.allow(target)
        if (!tryStartMotionChain(target)) {
            clearAStarTargetLock()
        }
    }

    private fun tryStartMotionChain(defeatedTarget: LivingEntity): Boolean {
        val transport = activeMovementTransport ?: return false
        val routeOrigin = player.position()
        val chainAnchor = defeatedTarget.position()
        val inheritedTargetSource = attemptTracker.current?.targetSource
        val selection = selectNearestReachableSpearKillChainTarget(
            candidates = findSpearKillChainCandidates(defeatedTarget, chainAnchor),
            distanceSquared = { candidate -> chainAnchor.distanceToSqr(candidate.position()) },
            createRoute = { candidate ->
                val rawDistance = routeOrigin.distanceTo(candidate.position())
                if (rawDistance !in 3.0..maxTargetDistance.toDouble()) {
                    null
                } else {
                    val travel = calculateSpearKillTravel(rawDistance)
                    if (!isDirectSpearKillTargetEligible(candidate, travel)) {
                        null
                    } else {
                        val roundTrip = createDirectAttackMovements(
                            target = candidate,
                            distance = travel,
                            profile = currentSpeedProfile(transport.stepLimit),
                        )
                        roundTrip.take((roundTrip.size - 1) / 2).takeIf { it.isNotEmpty() }
                    }
                }
            },
        ) ?: return false

        val chainedMovements = buildSpearKillChainedAttackMovements(
            outboundMovements = selection.route,
            existingReturnMovements = attackMovements.toList(),
        )
        attackMovements.clear()
        attackMovements.addAll(chainedMovements)
        handoffSpearKillRouteTarget(defeatedTarget, selection.target)
        beginSpearKillAttempt(
            target = selection.target,
            routeMode = "Direct Chain",
            outboundSteps = selection.route.size,
            hitTicks = selection.route.size,
            terminalAuthorizationRequired = false,
            targetSourceOverride = inheritedTargetSource,
        )
        return true
    }

    private fun tryStartPacketChain(defeatedTarget: LivingEntity): PacketChainStartResult {
        if (!packetBootSession.canReplaceRemainingOutbound && !packetBootSession.canStartChainedOutbound) {
            return PacketChainStartResult.FAILED
        }

        val sessionOrigin = packetSessionOrigin ?: return PacketChainStartResult.FAILED
        val settings = packetSessionSettings ?: return PacketChainStartResult.FAILED
        val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
        val chainAnchor = defeatedTarget.position()
        val inheritedTargetSource = attemptTracker.current?.targetSource
        val selection = selectNearestReachableSpearKillChainTarget(
            candidates = findSpearKillChainCandidates(defeatedTarget, chainAnchor),
            distanceSquared = { candidate -> chainAnchor.distanceToSqr(candidate.position()) },
            createRoute = { candidate ->
                createPacketChainPlan(candidate, routeOrigin, sessionOrigin, settings)
            },
        ) ?: return PacketChainStartResult.FAILED
        val plan = selection.route
        val aStarPlan = plan.aStarPlan
        if (!installPacketChainPlan(plan)) return PacketChainStartResult.FAILED

        packetAStarAttackActive = aStarPlan != null
        directTerminalReplanInstalled = false
        plannedAStarApproach = aStarPlan?.approach
        plannedAStarRenderPath = aStarPlan?.renderPath.orEmpty()
        plannedAStarTargetPosition = aStarPlan?.targetPosition ?: selection.target.position()
        plannedAStarTargetVelocity = aStarPlan?.targetVelocity
            ?: selection.target.position().subtract(selection.target.lastPos)
        aStarPlanTick = player.tickCount
        packetRecoveryStallTicks = 0
        physicalReturnPositioner.clear()
        returnRecoveryTracker.observeCombatPosition(routeOrigin)
        handoffSpearKillRouteTarget(defeatedTarget, selection.target)
        beginSpearKillAttempt(
            target = selection.target,
            routeMode = "${plan.routeMode} Chain",
            outboundSteps = plan.outboundMovements.size,
            hitTicks = plan.hitTicks,
            terminalAuthorizationRequired = plan.terminalAuthorizationRequired,
            targetSourceOverride = inheritedTargetSource,
        )
        synchronizeSpearKillServerSneak()
        return PacketChainStartResult.STARTED
    }

    private fun installPacketChainPlan(plan: PacketChainPlan): Boolean {
        val fallSafetyPlan = createReplacementFallSafetyPlan(plan.outboundMovements) ?: return false
        val aStarPlan = plan.aStarPlan
        val terminalSuffixSteps = aStarPlan?.terminalSuffixCount
            ?: plan.terminalBurstSteps.coerceAtLeast(1)
        val install: (List<Vec3>, Int, Int, Int, Int, Boolean) -> Boolean =
            if (packetBootSession.canStartChainedOutbound) {
                packetBootSession::startChainedOutbound
            } else {
                packetBootSession::replaceRemainingOutbound
            }
        val installed = install(
            plan.outboundMovements,
            plan.strikeHoldTicks,
            plan.preStrikeHoldTicks,
            terminalSuffixSteps,
            plan.terminalBurstSteps,
            plan.terminalAuthorizationRequired,
        )
        if (installed) replanVirtualFallSafety(fallSafetyPlan)
        return installed
    }

    @Suppress("ReturnCount")
    private fun createPacketChainPlan(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
        settings: SpearKillPacketSessionSettings,
    ): PacketChainPlan? {
        if (settings.routingMode == SpearKillRoutingMode.INSTANT) return null

        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
            ?: return null
        val kineticRequirements = spearKillKineticDamageRequirements(kineticWeapon)
            ?: return null
        val damageUseDuration = kineticWeapon.computeDamageUseDuration()
        val directPlan = createDirectPacketRouteForMovedTarget(
            target,
            routeOrigin,
            sessionOrigin,
        )
        if (directPlan != null) {
            if (!hasSpearKillKineticDamageRequirements(directPlan, kineticRequirements)) return null
            return createDirectPacketChainPlan(directPlan.route, settings, damageUseDuration)
        }
        return if (settings.routingMode == SpearKillRoutingMode.A_STAR ||
            settings.routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED
        ) {
            createAStarPacketChainPlan(
                target = target,
                routeOrigin = routeOrigin,
                sessionOrigin = sessionOrigin,
                settings = settings,
                damageUseDuration = damageUseDuration,
            )
        } else {
            null
        }
    }

    private fun createDirectPacketChainPlan(
        route: SpearKillAStarPacketRoute,
        settings: SpearKillPacketSessionSettings,
        damageUseDuration: Int,
    ): PacketChainPlan? {
        val hitTicks = spearKillDirectRouteHitTicks(
            routingMode = settings.routingMode,
            outboundTickCount = route.outboundTickCount,
            stepWaitTicks = settings.stepWaitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
        )
        return PacketChainPlan(
            outboundMovements = route.outboundMovements,
            routeMode = settings.routingMode.directRouteLabel(),
            hitTicks = hitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
            terminalBurstSteps = route.terminalBurstSteps,
            preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
            terminalAuthorizationRequired = true,
        ).takeIf {
            hasSpearKillScheduleDamageWindow(
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = damageUseDuration,
                hitTick = hitTicks,
            )
        }
    }

    @Suppress("ReturnCount")
    private fun createAStarPacketChainPlan(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
        settings: SpearKillPacketSessionSettings,
        damageUseDuration: Int,
    ): PacketChainPlan? {
        val aStarPlan = calculateAStarAttackPlan(
            target = target,
            routeOrigin = routeOrigin,
            sessionOrigin = sessionOrigin,
            settings = settings,
        ) ?: return null
        if (!hasSpearKillScheduleDamageWindow(
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = damageUseDuration,
                hitTick = aStarPlan.schedule.hitTick,
            )
        ) {
            return null
        }
        return PacketChainPlan(
            outboundMovements = aStarPlan.packetRoute.outboundMovements,
            routeMode = settings.routingMode.aStarRouteLabel(),
            hitTicks = aStarPlan.schedule.hitTick,
            strikeHoldTicks = settings.strikeHoldTicks,
            preStrikeHoldTicks = aStarPlan.preStrikeHoldTicks,
            terminalAuthorizationRequired = true,
            aStarPlan = aStarPlan,
        )
    }

    private fun handoffSpearKillRouteTarget(previousTarget: LivingEntity, nextTarget: LivingEntity) {
        if (fightBotSpearTarget === previousTarget) fightBotSpearTarget = nextTarget
        if (killAuraSpearTarget === previousTarget) killAuraSpearTarget = nextTarget
        if (pendingKillAuraTarget === previousTarget) pendingKillAuraTarget = nextTarget
        lockedAStarTarget = nextTarget
        previewTarget = nextTarget
    }

    @Suppress("ReturnCount")
    private fun followLockedPacketTarget() {
        if (!usesPacketMovementMode || !packetBootSession.active) return

        val target = lockedAStarTarget ?: return
        if (target.isRemoved) {
            attemptTracker.markTargetRemoved()
        }
        when (classifySpearKillPacketTargetState(
                isAlive = target.isAlive,
                isRemoved = target.isRemoved,
                isInCurrentWorld = target.level() === world,
                isWithinRange = (packetSessionOrigin?.add(packetBootSession.committedOffset)
                    ?: player.position()).distanceTo(target.position()) <= maxTargetDistance,
            )
        ) {
            SpearKillPacketTargetState.ACTIVE -> Unit
            SpearKillPacketTargetState.DEFEATED -> {
                terminatePacketFollow(target, PacketFollowTermination.DEFEATED)
                return
            }
            SpearKillPacketTargetState.UNREACHABLE -> {
                terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
                return
            }
        }
        if (!target.shouldBeAttacked()) {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            return
        }
        if (packetBootSession.recovering) return
        if (activePacketRoutingMode == SpearKillRoutingMode.INSTANT) return

        if (handlePendingTerminalCommit(target)) return

        val plannedPosition = plannedAStarTargetPosition ?: return
        val canReplacePath = if (packetAStarAttackActive) {
            packetBootSession.canReplaceRemainingApproach
        } else {
            packetBootSession.canReplaceRemainingOutbound
        }
        if (!shouldReplanSpearKillAStarTarget(
                plannedPosition,
                target.position(),
                player.tickCount - aStarPlanTick,
                plannedAStarTargetVelocity,
            ) || !canReplacePath || plannedPacket != null ||
            awaitingVanillaMovementPacket
        ) {
            return
        }

        val sessionOrigin = packetSessionOrigin ?: return
        val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
        if (packetAStarAttackActive) {
            val result = replanLockedAStarTarget(target, routeOrigin, sessionOrigin)
            if (!shouldKeepSpearKillTerminalPending(result)) {
                terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
            }
        } else {
            replanLockedDirectPacketTarget(target, routeOrigin, sessionOrigin)
        }
    }

    private fun handlePendingTerminalCommit(target: LivingEntity): Boolean {
        if (!packetBootSession.awaitingTerminalCommitAuthorization) return false

        if (packetAStarAttackActive) {
            commitOrReplanAStarTerminal(target)
        } else {
            commitOrReplanDirectTerminal(target)
        }
        return true
    }

    private fun commitOrReplanAStarTerminal(target: LivingEntity) {
        if (!packetBootSession.terminalAimLockComplete ||
            plannedPacket != null ||
            awaitingVanillaMovementPacket
        ) {
            return
        }
        if (!ensureSpearKillTerminalCharge(target)) return
        if (hasSafeLiveAStarTerminalCommit(target)) {
            if (!packetBootSession.authorizeTerminalCommit()) {
                terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            } else {
                attemptTracker.authorizeTerminal(player.tickCount)
            }
            return
        }

        val sessionOrigin = packetSessionOrigin ?: run {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            return
        }
        val replanResult = replanLockedAStarTarget(
            target = target,
            routeOrigin = sessionOrigin.add(packetBootSession.committedOffset),
            sessionOrigin = sessionOrigin,
        )
        if (!shouldKeepSpearKillTerminalPending(replanResult)) {
            terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
        }
    }

    private fun commitOrReplanDirectTerminal(target: LivingEntity) {
        if (!packetBootSession.terminalAimLockComplete ||
            plannedPacket != null ||
            awaitingVanillaMovementPacket
        ) {
            return
        }
        if (!ensureSpearKillTerminalCharge(target)) return

        val plannedPosition = plannedAStarTargetPosition
        if (plannedPosition == null) {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            return
        }
        if (!shouldReplanSpearKillDirectTerminal(
                plannedPosition = plannedPosition,
                currentPosition = target.position(),
                ticksSincePlan = player.tickCount - aStarPlanTick,
                plannedVelocity = plannedAStarTargetVelocity,
                terminalReplanInstalled = directTerminalReplanInstalled,
            )
        ) {
            if (!packetBootSession.authorizeTerminalCommit()) {
                terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            } else {
                attemptTracker.authorizeTerminal(player.tickCount)
            }
            return
        }

        val sessionOrigin = packetSessionOrigin
        if (sessionOrigin == null) {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            return
        }
        when (installReplannedDirectPacketRoute(
            target = target,
            routeOrigin = sessionOrigin.add(packetBootSession.committedOffset),
            sessionOrigin = sessionOrigin,
        )) {
            SpearKillPacketRouteReplanResult.INSTALLED -> directTerminalReplanInstalled = true
            SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE -> Unit
            SpearKillPacketRouteReplanResult.BLOCKED ->
                terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
        }
    }

    private fun ensureSpearKillTerminalCharge(target: LivingEntity): Boolean {
        val schedule = remainingSpearKillTerminalSchedule()
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        val action = if (schedule == null || kineticWeapon == null) {
            SpearKillTerminalChargeAction.INVALID
        } else {
            resolveSpearKillTerminalChargeAction(
                isUsingSpear = isUsingSpear,
                ticksUsingItem = player.ticksUsingItem,
                delayTicks = kineticWeapon.delayTicks,
                damageUseDuration = kineticWeapon.computeDamageUseDuration(),
                remainingHitTicks = schedule.hitTick,
            )
        }

        return when (action) {
            SpearKillTerminalChargeAction.READY -> true
            SpearKillTerminalChargeAction.WAIT -> false
            SpearKillTerminalChargeAction.REFRESH -> {
                refreshSpearKillServerUse()
                false
            }
            SpearKillTerminalChargeAction.INVALID -> {
                terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
                false
            }
        }
    }

    private fun remainingSpearKillTerminalSchedule(): SpearKillPathSchedule? {
        val settings = packetSessionSettings ?: return null
        val terminalSteps = packetBootSession.terminalSuffixSteps
        return buildSpearKillTerminalSchedule(
            terminalStepCount = terminalSteps,
            stepWaitTicks = settings.stepWaitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
        )
    }

    private fun hasSafeLiveAStarTerminalCommit(target: LivingEntity): Boolean {
        val approach = plannedAStarApproach ?: return false
        val remainingSchedule = remainingSpearKillTerminalSchedule() ?: return false
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return false
        val prediction = captureSpearKillRouteTargetSnapshot(target)
            .predict(remainingSchedule.hitTick)
        val eyeOffset = player.eyePosition.subtract(player.position())
        val virtualEye = approach.terminalWaypoint.add(eyeOffset)
        val terminalMovement = approach.terminalWaypoint.subtract(approach.plannerGoal)

        return canCommitSpearKillTerminalLunge(
            isUsingSpear = isUsingSpear,
            ticksUsingItem = player.ticksUsingItem,
            delayTicks = kineticWeapon.delayTicks,
            damageUseDuration = kineticWeapon.computeDamageUseDuration(),
            remainingHitTicks = remainingSchedule.hitTick,
            hasLiveAttackRay = hasValidAStarTerminalAttackRay(
                targetBox = prediction.boundingBox,
                eyeOffset = eyeOffset,
                approach = approach,
            ),
            aimAligned = isSpearKillTerminalAimAligned(
                eye = virtualEye,
                terminalMovement = terminalMovement,
                targetPoint = prediction.eyePosition,
            ),
        )
    }

    @Suppress("ReturnCount")
    private fun replanLockedAStarTarget(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ): SpearKillPacketRouteReplanResult {
        val settings = packetSessionSettings ?: return SpearKillPacketRouteReplanResult.BLOCKED
        val plan = calculateAStarAttackPlan(
            target = target,
            routeOrigin = routeOrigin,
            sessionOrigin = sessionOrigin,
            settings = settings,
        )
        val damageUseDuration = player.useItem.get(DataComponents.KINETIC_WEAPON)?.computeDamageUseDuration()
        if (plan == null || damageUseDuration == null ||
            !hasSpearKillScheduleDamageWindow(player.ticksUsingItem, damageUseDuration, plan.schedule.hitTick)
        ) {
            // Transient prediction misses should not destroy an already safe route.
            aStarPlanTick = player.tickCount
            return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
        }
        val fallSafetyPlan = createReplacementFallSafetyPlan(plan.packetRoute.outboundMovements)
            ?: return SpearKillPacketRouteReplanResult.BLOCKED
        if (!packetBootSession.replaceRemainingOutbound(
                outboundMovements = plan.packetRoute.outboundMovements,
                strikeHoldTicks = settings.strikeHoldTicks,
                preStrikeHoldTicks = plan.preStrikeHoldTicks,
                terminalSuffixSteps = plan.terminalSuffixCount,
                requireTerminalAuthorization = true,
            )
        ) {
            return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
        }
        replanVirtualFallSafety(fallSafetyPlan)
        plannedAStarRenderPath = plan.renderPath
        plannedAStarApproach = plan.approach
        plannedAStarTargetPosition = plan.targetPosition
        plannedAStarTargetVelocity = plan.targetVelocity
        aStarPlanTick = player.tickCount
        refreshReplannedPacketAttempt(
            target = target,
            outboundSteps = plan.packetRoute.outboundMovements.size,
            hitTicks = plan.schedule.hitTick,
            terminalAuthorizationRequired = true,
        )
        return SpearKillPacketRouteReplanResult.INSTALLED
    }

    private fun replanLockedDirectPacketTarget(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ) {
        when (installReplannedDirectPacketRoute(target, routeOrigin, sessionOrigin)) {
            SpearKillPacketRouteReplanResult.INSTALLED,
            SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE,
            -> Unit
            SpearKillPacketRouteReplanResult.BLOCKED ->
                terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
        }
    }

    @Suppress("ReturnCount")
    private fun installReplannedDirectPacketRoute(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ): SpearKillPacketRouteReplanResult {
        val settings = packetSessionSettings ?: return SpearKillPacketRouteReplanResult.BLOCKED
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
            ?: return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
        val kineticRequirements = spearKillKineticDamageRequirements(kineticWeapon)
            ?: return SpearKillPacketRouteReplanResult.BLOCKED
        val plan = createDirectPacketRouteForMovedTarget(
            target,
            routeOrigin,
            sessionOrigin,
        ) ?: return SpearKillPacketRouteReplanResult.BLOCKED
        if (!hasSpearKillKineticDamageRequirements(plan, kineticRequirements)) {
            return SpearKillPacketRouteReplanResult.BLOCKED
        }
        val route = plan.route
        val hitTicks = spearKillDirectRouteHitTicks(
            routingMode = settings.routingMode,
            outboundTickCount = route.outboundTickCount,
            stepWaitTicks = activePacketStepWaitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
        )
        val damageUseDuration = kineticWeapon.computeDamageUseDuration()
        if (!hasSpearKillScheduleDamageWindow(player.ticksUsingItem, damageUseDuration, hitTicks)) {
            return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
        }
        val fallSafetyPlan = createReplacementFallSafetyPlan(route.outboundMovements)
            ?: return SpearKillPacketRouteReplanResult.BLOCKED
        if (!packetBootSession.replaceRemainingOutbound(
                route.outboundMovements,
                strikeHoldTicks = settings.strikeHoldTicks,
                preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
                terminalSuffixSteps = route.terminalBurstSteps.coerceAtLeast(1),
                terminalBurstSteps = route.terminalBurstSteps,
                requireTerminalAuthorization = true,
            )
        ) {
            return SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE
        }
        replanVirtualFallSafety(fallSafetyPlan)
        plannedAStarTargetPosition = plan.targetSnapshot.observedPosition
        plannedAStarTargetVelocity = plan.targetSnapshot.velocity
        aStarPlanTick = player.tickCount
        refreshReplannedPacketAttempt(
            target = target,
            outboundSteps = route.outboundMovements.size,
            hitTicks = hitTicks,
            terminalAuthorizationRequired = true,
        )
        return SpearKillPacketRouteReplanResult.INSTALLED
    }

    private fun refreshReplannedPacketAttempt(
        target: LivingEntity,
        outboundSteps: Int,
        hitTicks: Int,
        terminalAuthorizationRequired: Boolean,
    ) {
        val previousAttempt = attemptTracker.current
        beginSpearKillAttempt(
            target = target,
            routeMode = previousAttempt?.plannedRouteMode ?: packetRoutingMode.tag,
            outboundSteps = outboundSteps,
            hitTicks = hitTicks,
            terminalAuthorizationRequired = terminalAuthorizationRequired,
            targetSourceOverride = previousAttempt?.targetSource,
        )
    }

    private fun terminatePacketFollow(target: LivingEntity?, termination: PacketFollowTermination) {
        when (termination) {
            PacketFollowTermination.DEFEATED -> attemptTracker.markDefeated()
            PacketFollowTermination.BLOCKED -> attemptTracker.markBlocked()
            PacketFollowTermination.UNREACHABLE -> Unit
        }
        if (termination.rejectTarget && target != null) {
            rejectSpearKillTarget(target)
        } else if (target != null) {
            rejectedTargets.allow(target)
        }
        if (termination == PacketFollowTermination.DEFEATED && target != null) {
            when (tryStartPacketChain(target)) {
                PacketChainStartResult.STARTED -> return
                PacketChainStartResult.FAILED -> Unit
            }
        }
        plannedAStarApproach = null
        plannedAStarTargetPosition = null
        plannedAStarTargetVelocity = Vec3.ZERO
        clearAStarRenderPath()
        beginSafeExactReturn()
        applyConfirmedPhysicalReturnPosition()
        synchronizeSpearKillServerSneak()
        val notificationKey = termination.notificationKey ?: return
        if (!failureNotificationGate.shouldNotify(player.tickCount)) return
        notification(
            name,
            message(notificationKey),
            NotificationEvent.Severity.ERROR,
        )
    }

    /**
     * Rebuilds and round-trip validates direct Packet movement from the current confirmed position.
     * The validator stays anchored to the original session box so virtual movement never inherits a
     * displaced client-side collision shape.
     */
    private fun createDirectPacketRouteForMovedTarget(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ): DirectPacketRoutePlan? {
        val rawDistance = routeOrigin.distanceTo(target.position())
        if (rawDistance !in 3.0..maxTargetDistance.toDouble()) return null

        val settings = packetSessionSettings ?: return null
        return calculateDirectPacketRoute(
            target = target,
            routeOrigin = routeOrigin,
            travel = calculateSpearKillTravel(rawDistance),
            settings = settings,
            sessionOrigin = sessionOrigin,
        )
    }

    private fun calculateDirectPacketRoute(
        target: LivingEntity,
        routeOrigin: Vec3,
        travel: Double,
        settings: SpearKillPacketSessionSettings,
        sessionOrigin: Vec3,
    ): DirectPacketRoutePlan? {
        val playerSnapshot = captureSpearKillPlayerRouteSnapshot(sessionOrigin, settings.transport.stepLimit)
        if (!travel.isFinite() || travel <= 0.0) return null
        val estimatedStepCount = buildSpearKillProfiledMovements(
            direction = Vec3(1.0, 0.0, 0.0),
            distance = travel,
            profile = playerSnapshot.speedProfile,
        ).size
        val targetSnapshot = captureSpearKillRouteTargetSnapshot(
            target = target,
            predictionTicks = spearKillDirectRouteHitTicks(
                routingMode = settings.routingMode,
                outboundTickCount = estimatedStepCount,
                stepWaitTicks = settings.stepWaitTicks,
                strikeHoldTicks = settings.strikeHoldTicks,
            ),
        )
        val snapshotBuilder = SpearKillCollisionSnapshotBuilder.corridor(
            points = listOf(routeOrigin) + targetSnapshot.collisionCorridorPositions(),
            horizontalMargin = SPEAR_KILL_DIRECT_SNAPSHOT_HORIZONTAL_MARGIN,
            verticalMargin = SPEAR_KILL_DIRECT_SNAPSHOT_VERTICAL_MARGIN,
            maxCells = SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS,
        )
        return calculateRouteSynchronously(snapshotBuilder) { collisionSnapshot ->
            createDirectPacketRoute(
                target = targetSnapshot,
                routeOrigin = routeOrigin,
                travel = travel,
                settings = settings,
                sessionOrigin = sessionOrigin,
                playerSnapshot = playerSnapshot,
                collisionSnapshot = collisionSnapshot,
            )
        }
    }

    private fun calculateAStarAttackPlan(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
        settings: SpearKillPacketSessionSettings,
    ): AStarAttackPlan? {
        val playerSnapshot = captureSpearKillPlayerRouteSnapshot(sessionOrigin, settings.transport.stepLimit)
        val estimatedHitTicks = spearKillPacketTravelTicks(
            stepCount = settings.aStar.maxCost.coerceAtLeast(1),
            stepWaitTicks = settings.stepWaitTicks,
        ) + SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS + settings.strikeHoldTicks
        val targetSnapshot = captureSpearKillRouteTargetSnapshot(target, estimatedHitTicks)
        val snapshotBuilder = SpearKillCollisionSnapshotBuilder.corridor(
            points = listOf(routeOrigin) + targetSnapshot.collisionCorridorPositions(),
            horizontalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN,
            verticalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN,
            maxCells = SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS,
        )
        return calculateRouteSynchronously(snapshotBuilder) { collisionSnapshot ->
            createAStarAttackPlan(
                target = targetSnapshot,
                routeOrigin = routeOrigin,
                sessionOrigin = sessionOrigin,
                stepWaitTicks = settings.stepWaitTicks,
                strikeHoldTicks = settings.strikeHoldTicks,
                aStar = settings.aStar,
                playerSnapshot = playerSnapshot,
                collisionSnapshot = collisionSnapshot,
            )
        }
    }

    private fun <T> calculateRouteSynchronously(
        snapshotBuilder: SpearKillCollisionSnapshotBuilder,
        calculation: (SpearKillCollisionSnapshot) -> T,
    ): T = withVanillaSpearKillBlockShapes {
        calculateSpearKillRouteSynchronously(
            snapshotBuilder = snapshotBuilder,
            collisionBoxesAt = ::spearKillCollisionBoxesAt,
            calculation = calculation,
        )
    }

    private fun spearKillCollisionBoxesAt(position: BlockPos.MutableBlockPos): List<AABB> =
        if (!world.hasChunkAt(position)) {
            listOf(AABB(
                position.x.toDouble(),
                position.y.toDouble(),
                position.z.toDouble(),
                position.x + 1.0,
                position.y + 1.0,
                position.z + 1.0,
            ))
        } else {
            world.getBlockState(position)
                .getCollisionShape(world, position)
                .toAabbs()
                .map { box -> box.move(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()) }
        }

    private fun captureSpearKillPlayerRouteSnapshot(
        sessionOrigin: Vec3,
        stepDistance: Double,
    ) = SpearKillPlayerRouteSnapshot(
        eyeOffset = player.eyePosition.subtract(player.position()),
        lookAngle = player.lookAngle,
        sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin),
        speedProfile = currentSpeedProfile(stepDistance),
        safeVerticalStep = safeVirtualFallStep,
        maximumTargetDistance = maxTargetDistance.toDouble(),
    )

    @Suppress("ReturnCount")
    private fun createDirectPacketRoute(
        target: SpearKillRouteTargetSnapshot,
        routeOrigin: Vec3,
        travel: Double,
        settings: SpearKillPacketSessionSettings,
        sessionOrigin: Vec3,
        playerSnapshot: SpearKillPlayerRouteSnapshot,
        collisionSnapshot: SpearKillCollisionSnapshot,
    ): DirectPacketRoutePlan? {
        if (routeOrigin.distanceTo(target.observedPosition) !in 3.0..playerSnapshot.maximumTargetDistance) return null

        val eyeOffset = playerSnapshot.eyeOffset
        val routeEye = routeOrigin.add(eyeOffset)
        if (!hasVisibleSpearKillAttackRay(
                eye = routeEye,
                direction = target.observedPosition.add(target.eyeOffset).subtract(routeEye),
                targetBox = target.boundingBox,
                range = playerSnapshot.maximumTargetDistance,
                lineOfSight = collisionSnapshot::isRayClear,
            )
        ) {
            return null
        }

        val profile = playerSnapshot.speedProfile
        val maxVerticalStep = spearKillDirectRouteMaxVerticalStep(
            routingMode = settings.routingMode,
            maximumStepLimit = profile.maximumStepLimit,
            safeVerticalStep = playerSnapshot.safeVerticalStep,
        )
        if (!travel.isFinite() || travel <= 0.0) return null
        val stepCount = buildSpearKillProfiledMovements(Vec3(1.0, 0.0, 0.0), travel, profile).size
        var predictedHitTicks = spearKillDirectRouteHitTicks(
            routingMode = settings.routingMode,
            outboundTickCount = stepCount,
            stepWaitTicks = settings.stepWaitTicks,
            strikeHoldTicks = settings.strikeHoldTicks,
        )
        val segmentValidator = collisionSnapshot.createSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = playerSnapshot.sessionBoundingBox,
        )
        repeat(SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT) {
            val rawRoute = buildPredictedDirectPacketRoute(
                target = target,
                routeOrigin = routeOrigin,
                routeEye = routeEye,
                profile = profile,
                maxVerticalStep = maxVerticalStep,
                segmentValidator = segmentValidator,
                predictedHitTicks = predictedHitTicks,
                fallbackDirection = playerSnapshot.lookAngle,
                hasAttackRay = collisionSnapshot::isRayClear,
            ) ?: return null
            val route = if (settings.allowTerminalBurst) {
                rawRoute
            } else {
                paceSpearKillNetworkRoute(rawRoute)
            }
            val actualHitTicks = spearKillDirectRouteHitTicks(
                routingMode = settings.routingMode,
                outboundTickCount = route.outboundTickCount,
                stepWaitTicks = settings.stepWaitTicks,
                strikeHoldTicks = settings.strikeHoldTicks,
            )
            if (actualHitTicks == predictedHitTicks) return DirectPacketRoutePlan(route, target)
            predictedHitTicks = actualHitTicks
        }
        return null
    }

    @Suppress("LongParameterList")
    private fun buildPredictedDirectPacketRoute(
        target: SpearKillRouteTargetSnapshot,
        routeOrigin: Vec3,
        routeEye: Vec3,
        profile: SpearKillSpeedProfile,
        maxVerticalStep: Double,
        segmentValidator: SpearKillAStarSegmentValidator,
        predictedHitTicks: Int,
        fallbackDirection: Vec3,
        hasAttackRay: (Vec3, Vec3) -> Boolean,
    ): SpearKillAStarPacketRoute? {
        val prediction = target.predict(predictedHitTicks)
        val predictedTargetPosition = prediction.position
        val predictedTargetBox = prediction.boundingBox
        val predictedTargetEyePosition = prediction.eyePosition
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = routeEye,
            predictedTargetPosition = predictedTargetPosition,
            targetEyeOffset = target.eyeOffset,
            fallbackDirection = fallbackDirection,
        )
        val attackRoute = buildSpearKillProfiledDirectAttackRoute(
            origin = routeOrigin,
            targetBox = predictedTargetBox,
            targetEyePosition = predictedTargetEyePosition,
            playerEyeOffset = routeEye.subtract(routeOrigin),
            preferredDirection = direction,
            profile = profile,
            segmentValidator = segmentValidator,
            maxVerticalStep = maxVerticalStep,
        ) ?: return null
        if (!hasValidAStarTerminalAttackRay(
                targetBox = predictedTargetBox,
                eyeOffset = routeEye.subtract(routeOrigin),
                approach = attackRoute.approach,
                lineOfSight = hasAttackRay,
            )
        ) {
            return null
        }
        return attackRoute.packetRoute
    }

    private fun refreshSpearKillServerUse() {
        val hand = player.usedItemHand
        interaction.releaseUsingItem(player)
        interaction.useItem(player, hand)
    }

    private fun createCollisionSafeSetbackRecovery(
        @Suppress("UNUSED_PARAMETER") sessionOrigin: Vec3,
        authoritativeOffset: Vec3,
    ): List<Vec3>? = packetBootSession.exactRecoveryMovementsFrom(authoritativeOffset)

    private fun startPacketFirstReturnRecovery(
        authoritativePosition: Vec3,
        targetPlayer: Player = player,
        preferredFirstLeg: List<Vec3>? = null,
        initialFallDistance: Double = player.fallDistance.toDouble(),
    ): Boolean {
        var preferredLeg = preferredFirstLeg
        while (true) {
            when (val action = returnRecoveryTracker.nextAction(authoritativePosition)) {
                is SpearKillReturnRecoveryAction.PacketAttempt -> {
                    val movements = calculatePacketFirstReturnMovements(action, preferredLeg)
                    preferredLeg = null
                    if (movements == null) continue
                    if (beginPacketFirstReturnAttempt(action, movements, initialFallDistance)) return true
                }
                is SpearKillReturnRecoveryAction.PhysicalReset -> {
                    applyPhysicalReturnFallback(action.position, targetPlayer)
                    return false
                }
            }
        }
    }

    private fun calculatePacketFirstReturnMovements(
        attempt: SpearKillReturnRecoveryAction.PacketAttempt,
        preferredFirstLeg: List<Vec3>?,
    ): List<Vec3>? {
        val aStar = packetSessionSettings?.aStar
            ?: resolveSpearKillAStarSessionSettings(packetRoutingMode)
        val stepLimit = recoveryPlanningStepLimit
        val verticalStep = safeVirtualFallStep
        val playerBoundingBox = spearKillServerCollisionBoxAt(attempt.destination)
        val points = listOf(attempt.authoritativePosition) + attempt.checkpoints
        val snapshotBuilder = SpearKillCollisionSnapshotBuilder.corridor(
            points = points,
            horizontalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN,
            verticalMargin = SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN,
            maxCells = SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS,
        )
        return calculateRouteSynchronously(snapshotBuilder) { collisionSnapshot ->
            createPacketFirstReturnMovements(
                attempt = attempt,
                preferredFirstLeg = preferredFirstLeg,
                segmentValidator = collisionSnapshot.createSegmentValidator(
                    origin = attempt.destination,
                    playerBoundingBox = playerBoundingBox,
                ),
                collisionSnapshot = collisionSnapshot,
                aStar = aStar,
                stepLimit = stepLimit,
                verticalStep = verticalStep,
            )
        }
    }

    private fun createPacketFirstReturnMovements(
        attempt: SpearKillReturnRecoveryAction.PacketAttempt,
        preferredFirstLeg: List<Vec3>?,
        segmentValidator: SpearKillAStarSegmentValidator,
        collisionSnapshot: SpearKillCollisionSnapshot,
        aStar: SpearKillAStarSessionSettings,
        stepLimit: Double,
        verticalStep: Double,
    ): List<Vec3>? {
        var preferredLeg = preferredFirstLeg
        return buildSpearKillReturnRecoveryMovements(
            authoritativePosition = attempt.authoritativePosition,
            checkpoints = attempt.checkpoints,
        ) { from, to ->
            val candidate = preferredLeg
            preferredLeg = null
            validatedPreferredReturnLeg(from, to, candidate, segmentValidator)
                ?: planPacketReturnLeg(
                    from = from,
                    to = to,
                    segmentValidator = segmentValidator,
                    collisionSnapshot = collisionSnapshot,
                    aStar = aStar,
                    stepLimit = stepLimit,
                    verticalStep = verticalStep,
                )
        }
    }

    private fun validatedPreferredReturnLeg(
        from: Vec3,
        to: Vec3,
        movements: List<Vec3>?,
        segmentValidator: SpearKillAStarSegmentValidator,
    ): List<Vec3>? {
        movements ?: return null
        if (!isSpearKillPacketMovementSequenceServerAccepted(from, movements, segmentValidator)) return null
        return movements.takeIf {
            it.fold(from, Vec3::add).distanceToSqr(to) <= SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED
        }
    }

    private fun planPacketReturnLeg(
        from: Vec3,
        to: Vec3,
        segmentValidator: SpearKillAStarSegmentValidator,
        collisionSnapshot: SpearKillCollisionSnapshot,
        aStar: SpearKillAStarSessionSettings,
        stepLimit: Double,
        verticalStep: Double,
    ): List<Vec3>? {
        buildSpearKillAStarOutboundMovements(
            origin = from,
            waypoints = listOf(to),
            maxSpeed = stepLimit,
            segmentValidator = segmentValidator,
            maxVerticalStep = verticalStep,
        )?.let { return it }

        val route = SpearKillAStarRoutePlanner(
            allowDiagonal = aStar.diagonal,
            maxCost = aStar.maxCost,
            isPassable = collisionSnapshot::isPassable,
            canTraverse = segmentValidator::isClear,
        ).plan(from, to) ?: return null
        val compactedRoute = compactSpearKillAStarWaypoints(
            origin = from,
            waypoints = route,
            maxSpeed = stepLimit,
            segmentValidator = segmentValidator,
            lineOfSightShortcuts = aStar.lineOfSightShortcuts,
        )
        return buildSpearKillAStarOutboundMovements(
            origin = from,
            waypoints = compactedRoute + to,
            maxSpeed = stepLimit,
            segmentValidator = segmentValidator,
            maxVerticalStep = verticalStep,
        )
    }

    private fun beginPacketFirstReturnAttempt(
        attempt: SpearKillReturnRecoveryAction.PacketAttempt,
        movements: List<Vec3>,
        initialFallDistance: Double,
    ): Boolean {
        if (movements.isEmpty()) {
            if (attempt.authoritativeOffset.lengthSqr() >= SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED) {
                return false
            }
            clearVirtualMovementState()
            sendReturnArrivalConfirmations(attempt.authoritativePosition)
            finishPacketFirstReturnAttempt()
            return true
        }
        val fallSafetyPlan = createFutureFallSafetyPlan(
            routeOrigin = attempt.authoritativePosition,
            movements = movements,
            outboundStepCount = 0,
            expectedNetMovement = attempt.authoritativeOffset.scale(-1.0),
            initialFallDistance = initialFallDistance,
        ) ?: return false
        clearVirtualMovementState()
        packetSessionOrigin = attempt.destination
        physicalReturnPositioner.clear()
        packetRecoveryStallTicks = 0
        packetSetbackRecoveryAttempted = true
        attemptTracker.markRecovery()
        beginVirtualFallSafety(fallSafetyPlan)
        packetBootSession.beginPacketExactRecoveryFrom(
            attempt.authoritativeOffset,
            movements,
            activePacketStepWaitTicks,
        )
        sendReturnArrivalConfirmations(attempt.authoritativePosition)
        synchronizeSpearKillServerSneak()
        return true
    }

    private fun sendReturnArrivalConfirmations(position: Vec3) {
        while (returnRecoveryTracker.consumeArrivalConfirmation(position) != null) {
            network.send(MovePacketType.FULL.generatePacket())
        }
    }

    private fun finishPacketFirstReturnAttempt() {
        finishSpearKillFallSafety(player.position(), allowPacket = true)
        packetSessionOrigin = null
        packetSessionSettings = null
        activeMovementTransport = null
        physicalReturnPositioner.clear()
        resetSpearKillSpeedSession()
        requestSpearKillAttemptCompletion()
    }

    private fun applyPhysicalReturnFallback(position: Vec3, targetPlayer: Player) {
        clearAttack("recovery-exhausted", finishFallSafety = false)
        targetPlayer.setPos(position)
        targetPlayer.deltaMovement = Vec3.ZERO
        finishSpearKillFallSafety(position, allowPacket = true, targetPlayer = targetPlayer)
    }

    private fun resegmentPendingMotionRoute(
        pendingMovement: Vec3,
        attempt: SpearKillAttemptSnapshot,
    ): Boolean {
        val target = lockedAStarTarget ?: return false
        val remainingOutboundSteps = attempt.plannedOutboundStepCount - attempt.outboundStepCount
        val origin = player.position()
        val result = resegmentSpearKillUnconfirmedMotionRoute(
            origin = origin,
            pendingOutboundMovement = pendingMovement,
            queuedMovements = attackMovements.toList(),
            remainingOutboundSteps = remainingOutboundSteps,
            profile = currentSpeedProfile(activeSpeedStepDistance),
            segmentValidator = createFastSpearKillSegmentValidator(
                origin = origin,
                playerBoundingBox = spearKillServerCollisionBoxAt(origin),
            ),
        ) ?: return false

        attackMovements.clear()
        attackMovements.addAll(result.movements)
        beginSpearKillAttempt(
            target = target,
            routeMode = attempt.plannedRouteMode,
            outboundSteps = result.outboundStepCount,
            hitTicks = result.outboundStepCount,
            terminalAuthorizationRequired = false,
            targetSourceOverride = attempt.targetSource,
        )
        return true
    }

    private fun beginBlockedMotionRecovery(attempt: SpearKillAttemptSnapshot): Boolean {
        val remainingOutboundSteps = attempt.plannedOutboundStepCount - attempt.outboundStepCount
        val recovery = spearKillConfirmedMotionRecoveryTail(
            queuedMovements = attackMovements.toList(),
            remainingOutboundSteps = remainingOutboundSteps,
        ) ?: return false
        lockedAStarTarget?.let(::rejectSpearKillTarget)
        attemptTracker.markBlocked()
        attemptTracker.complete()
        damageEvidenceTracker.clear()
        attemptRouteCompleted = false
        attackMovements.clear()
        attackMovements.addAll(recovery)
        clearAStarTargetLock()
        movementAssistPreparationActive = false
        motionPacketHeading = null
        player.deltaMovement = Vec3.ZERO
        return true
    }

    private fun isServerAcceptedSpearKillRoute(
        sessionOrigin: Vec3,
        routeOrigin: Vec3,
        route: SpearKillAStarPacketRoute,
        routingAttempt: SpearKillRoutingAttempt,
    ): Boolean {
        val playerBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
        val segmentValidator = when (routingAttempt) {
            SpearKillRoutingAttempt.DIRECT -> createServerValidatedSpearKillDirectPacketSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = playerBoundingBox,
            )
            SpearKillRoutingAttempt.A_STAR -> createServerMovementSpearKillSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = playerBoundingBox,
            )
        }
        return isSpearKillPacketRouteServerAccepted(
            origin = routeOrigin,
            route = route,
            segmentValidator = segmentValidator,
        )
    }

    @Suppress("unused")
    private val routeRotationHandler = handler<RotationUpdateEvent>(
        priority = OBJECTION_AGAINST_EVERYTHING,
    ) {
        val heading = activeRouteHeading ?: return@handler
        RotationManager.setRotationTarget(
            plan = spearKillRouteRotationTarget(heading),
            priority = Priority.IMPORTANT_FOR_USER_SAFETY,
            provider = ModuleSpearKill,
        )
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(priority = SAFETY_FEATURE) { event ->
        val applied = applySpearKillMovementInputLease(
            physical = SpearKillMovementInput(event.jump, event.sneak),
            lease = movementAssistLease,
        )
        event.jump = applied.jump
        event.sneak = applied.sneak
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (player.isDeadOrDying) {
            clearAttack("death", allowFallSafetyPacket = false)
            return@handler
        }
        updateSpearKillAttemptEvidence()
        ownedMovementPacketsThisTick = 0
        if (!hasActiveAttackPath && attemptTracker.current != null) {
            requestSpearKillAttemptCompletion()
        }
        setbackGuard.tick(pathActive = packetBootSession.active)
        if (!packetBootSession.active && !setbackGuard.armed && packetSessionOrigin == null) {
            packetSetbackRecoveryAttempted = false
            returnRecoveryTracker.clear()
        }
        if (packetBootSession.active && !physicalReturnPositioner.followingReturn) {
            returnRecoveryTracker.observeCombatPosition(player.position())
        }

        if (enabled && !killAuraReturnActive) {
            followLockedMotionTarget()
            followLockedPacketTarget()
        }
        synchronizeSpearKillServerSneak()

        if (packetBootSession.recovering) {
            packetRecoveryStallTicks = nextSpearKillRecoveryStallTicks(
                currentTicks = packetRecoveryStallTicks,
                madeProgress = false,
            )
            if (packetRecoveryStallTicks >= SPEAR_KILL_MAX_RECOVERY_STALL_TICKS) {
                val sessionOrigin = packetPositionOrigin()
                val authoritativeOffset = packetBootSession.committedOffset
                val preferredReturn = packetBootSession.exactRecoveryMovementsFrom(authoritativeOffset)
                startPacketFirstReturnRecovery(
                    authoritativePosition = sessionOrigin.add(authoritativeOffset),
                    preferredFirstLeg = preferredReturn,
                )
            }
            return@handler
        }
        packetRecoveryStallTicks = 0
        if (killAuraReturnActive) {
            if (attackMovements.isNotEmpty()) {
                applyNextKillAuraMotionReturnStep()
            }
            killAuraReturnActive = hasSpearKillReturnWork
            synchronizeSpearKillServerSneak()
            return@handler
        }
        if (!enabled) {
            manualAttackRequestLatched = false
            movementAssistPreparationActive = false
            synchronizeSpearKillServerSneak()
            return@handler
        }

        updateKillAuraSpearUseRequest()
        updateManualAttackRequestLatch()
        updateHoldUseLaunchCycle()

        val activationRequested = hasActivationRequest
        if (!activationRequested) {
            movementAssistPreparationActive = false
            synchronizeSpearKillServerSneak()
            if (!packetBootSession.active && !fightBotSpearState.retainsRejectedTarget) {
                clearAStarTargetLock()
            }
            if (shouldClearSpearKillAStarRenderPath(
                    attackKeyDown = false,
                    packetSessionActive = packetBootSession.active,
                )
            ) {
                clearAStarRenderPath()
            }
        }

        if (packetBootSession.active && player.isPassenger) {
            abortSpearKillAttempt("passenger")
            clearVirtualAttack()
            synchronizeSpearKillServerSneak()
            return@handler
        }

        if (!holdingSpear || !isUsingSpear) {
            movementAssistPreparationActive = false
            // Only tear down an in-flight path here. Idle release used to call resetAttack every
            // tick (beginExactReturn / setback flags), which could leave the next charge unable
            // to start even though Preview still highlighted a target.
            if (hasActiveAttackPath) {
                resetAttack()
            } else if (!packetBootSession.active) {
                previewTarget = null
                if (!fightBotSpearState.retainsRejectedTarget) {
                    clearAStarTargetLock()
                }
                if (!setbackGuard.armed) {
                    packetSetbackRecoveryAttempted = false
                    returnRecoveryTracker.clear()
                }
            }
            return@handler
        }

        val attackActive = hasActiveAttackPath
        if (!attackActive && !packetRoutePreparationActive && lockedAStarTarget != null) {
            clearAStarTargetLock()
        }
        val shouldFindTarget = Preview.enabled || (!attackActive && activationRequested)
        val lockedCandidate = lockedAStarTargetCandidate()
        if (packetRoutePreparationActive && lockedCandidate == null) {
            clearAStarTargetLock()
        }
        val lockedTarget = activeSpearKillTargetLock(
            lockedTarget = lockedCandidate,
            routeActive = hasActiveAttackPath,
            routePreparationActive = packetRoutePreparationActive,
        )
        val selectedTarget = if (lockedTarget == null && shouldFindTarget) findSelectedTarget() else null
        val configuredTarget = preferLockedSpearKillTarget(lockedTarget, selectedTarget)
        val cursorTarget = if (lockedTarget == null && shouldFindTarget &&
            isSpearKillHoldUseCursorRetargetRequested(
                activationMode,
                isUseInputHeld,
                hasAutomaticSpearRequest,
                holdUseLaunchTarget,
            )
        ) {
            findLookRayTarget()
        } else {
            null
        }
        val launchTarget = if (lockedTarget != null) {
            lockedTarget
        } else {
            val selectedEntity = selectSpearKillHoldUseLaunchTarget(
                activationMode = activationMode,
                useInputHeld = isUseInputHeld,
                automaticRequest = hasAutomaticSpearRequest,
                previousLaunchTarget = holdUseLaunchTarget,
                cursorTarget = cursorTarget?.first,
                configuredTarget = configuredTarget?.first,
            )
            when {
                selectedEntity == null -> null
                selectedEntity === cursorTarget?.first -> cursorTarget
                selectedEntity === configuredTarget?.first -> configuredTarget
                else -> null
            }
        }
        val attackRequested = isSpearKillLaunchActivationSatisfied(
            activationMode = activationMode,
            activationRequested = activationRequested,
            previousLaunchTarget = holdUseLaunchTarget,
            launchTarget = launchTarget?.first,
            automaticRequest = hasAutomaticSpearRequest,
        )
        val target = launchTarget
        previewTarget = target?.first ?: configuredTarget?.first
        if (!attackRequested && !attackActive) {
            movementAssistPreparationActive = false
            if (!packetRoutePreparationActive && !fightBotSpearState.retainsRejectedTarget) {
                clearAStarTargetLock()
            }
        }
        if (shouldAcquireSpearKillPreparationLock(
                packetMovementMode = usesPacketMovementMode,
                attackActive = attackActive,
                attackRequested = attackRequested,
                hasTarget = target != null,
                hasLockedTarget = lockedAStarTarget != null,
            )
        ) {
            lockedAStarTarget = requireNotNull(target).first
            packetRoutePreparationActive = true
        }
        movementAssistPreparationActive = !attackActive && attackRequested && target != null
        if (movementAssistPreparationActive) {
            requestSpearKillPacketFallFlight()
        }
        synchronizeSpearKillServerSneak()

        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: run {
            resetAttack()
            return@handler
        }
        val chargeDuration = kineticWeapon.computeDamageUseDuration()

        when (resolveSpearKillChargeDecision(
            ticksUsingItem = player.ticksUsingItem,
            delayTicks = kineticWeapon.delayTicks,
            isUsingSpear = isUsingSpear,
            useRequested = isSpearUseRequested,
        )) {
            SpearKillChargeDecision.WAIT_FOR_VANILLA -> return@handler
            SpearKillChargeDecision.RESET -> {
                resetAttack()
                return@handler
            }
            SpearKillChargeDecision.READY -> Unit
        }

        if (!attackActive) {
            val launchCandidateReady = attackRequested && target != null
            if (shouldRefreshSpearKillPrehold(
                    useRequested = isSpearUseRequested,
                    launchCandidateReady = launchCandidateReady,
                    routeCanRecoverCharge = usesPacketMovementMode,
                    ticksUsingItem = player.ticksUsingItem,
                    delayTicks = kineticWeapon.delayTicks,
                    damageUseDuration = chargeDuration,
                )
            ) {
                refreshSpearKillServerUse()
                return@handler
            }
            if (!shouldStartSpearKillAttempt(
                    attackActive = false,
                    activationSatisfied = attackRequested,
                    hasTarget = target != null,
                    ticksUsingItem = player.ticksUsingItem,
                    delayTicks = kineticWeapon.delayTicks,
                    damageUseDuration = chargeDuration,
                )
            ) {
                return@handler
            }
            if (packetSetbackRecoveryAttempted) return@handler
            if (usesNetworkOptimizedRouting && !networkOptimizer.canStartAttempt(player.tickCount)) {
                return@handler
            }
            val (entity, dist) = requireNotNull(target)
            if (usesPacketMovementMode && player.isPassenger) {
                if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
                return@handler
            }
            // Packet routing owns its full corridor preflight. Automatic sources may skip one
            // rejected candidate; no movement is emitted before the selected route fully validates.
            if (!usesPacketMovementMode && !isDirectSpearKillTargetEligible(entity, dist)) {
                rejectSpearKillTarget(entity)
                recordRejectedSpearKillAttempt(entity, "Direct")
                if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
                return@handler
            }
            val activeLockedTarget = lockedAStarTarget
            if ((activeLockedTarget != null && activeLockedTarget !== entity) ||
                isSpearKillTargetRejected(entity)
            ) {
                return@handler
            }
            lockedAStarTarget = entity
            val createAttackResult = createAttackMovement(entity, dist)
            packetRoutePreparationActive = createAttackResult.keepsRoutePreparation
            when (createAttackResult) {
                SpearKillAttackStartResult.STARTED -> {
                    manualAttackRequestLatched = false
                    updateHoldUseLaunchCycle(launchStarted = true, launchedTarget = entity)
                    movementAssistPreparationActive = false
                    if (fightBotSpearTarget === entity) {
                        fightBotSpearState = SpearKillFightBotState.RouteActive
                    }
                    synchronizeSpearKillServerSneak()
                }
                SpearKillAttackStartResult.RETRY_LATER -> if (
                    shouldRestartSpearKillCharge(createAttackResult)
                ) {
                    refreshSpearKillServerUse()
                }
                SpearKillAttackStartResult.BLOCKED -> {
                    movementAssistPreparationActive = false
                    terminatePacketFollow(entity, PacketFollowTermination.BLOCKED)
                    recordRejectedSpearKillAttempt(entity, packetRoutingMode.tag)
                    clearAStarTargetLock()
                    if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
                }
                SpearKillAttackStartResult.REJECTED -> if (usesPacketMovementMode) {
                    movementAssistPreparationActive = false
                    rejectSpearKillTarget(entity)
                    recordRejectedSpearKillAttempt(
                        entity,
                        packetRoutingMode.tag,
                    )
                    clearAStarTargetLock()
                    if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
                }
            }
            return@handler
        }

        if (packetBootSession.active) {
            return@handler
        }

        var movement = attackMovements.removeFirst()
        var attempt = attemptTracker.current
        val outboundStep = movement.lengthSqr() > 0.0 && attempt != null &&
            attempt.outboundStepCount < attempt.plannedOutboundStepCount
        if (outboundStep) {
            var speedStep = previewSpearKillOutboundStep()
            if (movement.length() > speedStep.stepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON) {
                if (!resegmentPendingMotionRoute(movement, attempt)) {
                    if (!beginBlockedMotionRecovery(attempt)) resetAttack()
                    return@handler
                }
                movement = attackMovements.removeFirst()
                attempt = attemptTracker.current
                speedStep = previewSpearKillOutboundStep()
                if (attempt == null || movement.length() >
                    speedStep.stepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON
                ) {
                    resetAttack()
                    return@handler
                }
            }
            confirmSpearKillOutboundStep()
            attemptTracker.recordOutboundStep()
            lastDeliveredOutboundMovement = movement
        }
        motionPacketHeading = spearKillKineticHeading(movement)
        player.deltaMovement = movement
        lastDeliveredMovement = movement
        if (attackMovements.isEmpty()) resetSpearKillSpeedSession()
    }

    private fun applyNextKillAuraMotionReturnStep() {
        val movement = attackMovements.removeFirst()
        motionPacketHeading = spearKillKineticHeading(movement)
        player.deltaMovement = movement
        lastDeliveredMovement = movement
        if (attackMovements.isEmpty()) resetSpearKillSpeedSession()
    }

    @Suppress("unused")
    private val networkMovementHandler = handler<PlayerNetworkMovementTickEvent>(priority = SAFETY_FEATURE) { event ->
        if (event.state == EventState.POST) {
            if (awaitingVanillaMovementPacket && packetBootSession.requiresDelivery && plannedPacket == null) {
                sendFallbackMovementPacket()
            }
            awaitingVanillaMovementPacket = false
            return@handler
        }
        terminalBurstDeliveredMovementThisTick = Vec3.ZERO
        if (!packetBootSession.active) {
            if (!event.isCancelled && fallSafetyLifecycle.active && !setbackRollback.confirming) {
                finishSpearKillFallSafety(player.position(), allowPacket = true)
            }
            return@handler
        }
        if (event.isCancelled || plannedPacket != null || setbackRollback.confirming) {
            return@handler
        }

        if (activePacketRoutingMode == SpearKillRoutingMode.INSTANT) {
            deliverSpearKillInstantRoundTrip()
            return@handler
        }

        if (packetBootSession.prepareNextStep() == null) {
            if (packetBootSession.holdingPreStrike) {
                // A dedicated, position-stable packet makes the terminal heading reach the server
                // one full tick before authorization can release the first lunge movement.
                sendFallbackMovementPacket()
            }
            return@handler
        }
        val terminalBurstValidation = validatePendingSpearKillTerminalBurst()
        if (terminalBurstValidation != SpearKillPendingPacketStepValidation.CLEAR) {
            rejectPendingSpearKillPacketStep(terminalBurstValidation)
            return@handler
        }
        if (!deliverSpearKillTerminalBurstPrefix()) return@handler
        val movement = packetBootSession.pendingMovement
        if (movement != null && packetBootSession.pendingOutboundStep) {
            previewSpearKillOutboundStep()
        }
        if (movement != null && !gatePendingSpearKillFallSafety(movement)) return@handler
        val pendingOffset = packetBootSession.virtualOffset
        val position = packetPositionOrigin().add(pendingOffset)
        event.x = position.x
        event.y = position.y
        event.z = position.z
        event.ground = isSpearKillPositionNearGround(position)
        awaitingVanillaMovementPacket = true
    }

    /**
     * Flushes the current Instant phase through the normal packet pipeline. Outbound stops at the
     * terminal strike hold so the server can evaluate kinetic damage before the next tick flushes
     * the exact inverse return. Every packet remains synchronously validated and confirmed.
     */
    private fun deliverSpearKillInstantRoundTrip() {
        val maxPackets = packetSessionSettings?.instantMaxPackets ?: return
        var sentPackets = 0
        var continueBurst = true

        while (packetBootSession.active && sentPackets < maxPackets && continueBurst) {
            val delivery = deliverNextSpearKillInstantStep()
            if (delivery.packetSent) sentPackets++
            continueBurst = delivery.continueBurst
        }

        if (packetBootSession.active && sentPackets >= maxPackets && !packetBootSession.recovering) {
            terminatePacketFollow(lockedAStarTarget, PacketFollowTermination.BLOCKED)
        }
        finishInactiveSpearKillInstantSession()
    }

    private fun deliverNextSpearKillInstantStep(): InstantStepDelivery {
        val movement = packetBootSession.prepareNextStep()
            ?.let { packetBootSession.pendingMovement }
            ?: return InstantStepDelivery(false, false)

        val outboundStep = packetBootSession.pendingOutboundStep
        if (validatePendingSpearKillPacketStep() != SpearKillPendingPacketStepValidation.CLEAR) {
            packetBootSession.confirmStep(delivered = false)
            plannedPacket = null
            awaitingVanillaMovementPacket = false
            return recoverRejectedSpearKillInstantStep(outboundStep, packetSent = false)
        }

        if (fallSafetyLifecycle.shouldStabilizePendingMovement(movement, shouldProtectFallDamage)) {
            val delivered = sendSpearKillFallStabilizationPacket()
            if (!delivered) {
                packetBootSession.confirmStep(delivered = false)
                awaitingVanillaMovementPacket = false
            }
            return InstantStepDelivery(packetSent = true, continueBurst = delivered)
        }
        if (!gatePendingSpearKillFallSafety(movement)) {
            return recoverRejectedSpearKillInstantStep(outboundStep, packetSent = false)
        }

        val committedBeforeSend = packetBootSession.committedOffset
        awaitingVanillaMovementPacket = true
        sendFallbackMovementPacket()
        val madeProgress = packetBootSession.committedOffset.distanceToSqr(committedBeforeSend) >=
            SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
        return if (madeProgress) {
            InstantStepDelivery(packetSent = true, continueBurst = true)
        } else {
            recoverRejectedSpearKillInstantStep(outboundStep, packetSent = true)
        }
    }

    private fun recoverRejectedSpearKillInstantStep(
        outboundStep: Boolean,
        packetSent: Boolean,
    ): InstantStepDelivery {
        if (outboundStep && !packetBootSession.recovering) {
            terminatePacketFollow(lockedAStarTarget, PacketFollowTermination.BLOCKED)
        }
        return InstantStepDelivery(
            packetSent = packetSent,
            continueBurst = outboundStep && packetBootSession.active,
        )
    }

    /** Covers a zero-confirmation abort, where no delivery event owns the final session cleanup. */
    private fun finishInactiveSpearKillInstantSession() {
        if (packetBootSession.active || packetSessionOrigin == null) return

        finishSpearKillFallSafety(player.position(), allowPacket = true)
        packetSessionOrigin = null
        packetSessionSettings = null
        requestSpearKillAttemptCompletion()
        synchronizeSpearKillServerSneak()
    }

    /** Sends every non-final terminal segment now; the normal movement event carries the final one. */
    private fun deliverSpearKillTerminalBurstPrefix(): Boolean {
        while (packetBootSession.pendingTerminalBurstMovement != null &&
            !packetBootSession.pendingLogicalOutboundCompletion
        ) {
            val movement = packetBootSession.pendingMovement ?: return false
            if (!gatePendingSpearKillFallSafety(movement)) return false

            val expectedOffset = packetBootSession.virtualOffset
            awaitingVanillaMovementPacket = true
            sendFallbackMovementPacket()
            if (packetBootSession.committedOffset.distanceToSqr(expectedOffset) >
                SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED
            ) {
                return false
            }
            if (packetBootSession.prepareNextStep() == null) return false
        }
        return true
    }

    private fun gatePendingSpearKillFallSafety(movement: Vec3): Boolean = when (
        fallSafetyLifecycle.gatePendingMovement(
            movement,
            physicallyNearGround = isSpearKillPositionNearGround(
                packetPositionOrigin().add(packetBootSession.virtualOffset),
            ),
        )
    ) {
        SpearKillFallSafetyPendingStepGate.CLEAR -> true
        SpearKillFallSafetyPendingStepGate.BLOCKED -> {
            packetBootSession.confirmStep(delivered = false)
            awaitingVanillaMovementPacket = false
            beginSafeExactReturn()
            applyConfirmedPhysicalReturnPosition()
            false
        }
    }

    /** Sends Direct's mid-route NoFall spoof at the last delivery-confirmed virtual position. */
    private fun sendSpearKillFallStabilizationPacket(): Boolean {
        val position = packetPositionOrigin().add(packetBootSession.committedOffset)
        val heading = packetBootSession.pathHeading
        val packet = ServerboundMovePlayerPacket.PosRot(
            position.x,
            position.y,
            position.z,
            heading?.yaw ?: player.yRot,
            heading?.pitch ?: player.xRot,
            true,
            player.horizontalCollision,
        )
        virtualFallStabilizationDelivered = false
        virtualFallStabilizationPackets += packet
        network.send(packet)
        virtualFallStabilizationPackets.remove(packet)
        return virtualFallStabilizationDelivered
    }

    private fun sendFallbackMovementPacket() {
        val position = packetPositionOrigin().add(packetBootSession.virtualOffset)
        val heading = packetBootSession.pathHeading
        network.send(ServerboundMovePlayerPacket.PosRot(
            position.x,
            position.y,
            position.z,
            heading?.yaw ?: player.yRot,
            heading?.pitch ?: player.xRot,
            isSpearKillPositionNearGround(position),
            player.horizontalCollision,
        ))
    }

    /**
     * Revalidates the one pending virtual movement against the live world immediately before it
     * reaches the packet pipeline. Planning validates the same edge up front, but a chunk or
     * collision can change while the session is in progress.
     */
    private fun validatePendingSpearKillPacketStep(): SpearKillPendingPacketStepValidation {
        if (!packetBootSession.requiresDelivery) return SpearKillPendingPacketStepValidation.BLOCKED

        val sessionOrigin = packetSessionOrigin ?: return SpearKillPendingPacketStepValidation.BLOCKED
        val movement = packetBootSession.pendingMovement ?: return SpearKillPendingPacketStepValidation.BLOCKED
        val outboundStepLimit = if (packetBootSession.pendingOutboundStep) {
            previewSpearKillOutboundStep().stepLimit
        } else {
            activeMovementTransport?.stepLimit ?: SPEAR_KILL_EXPERIMENTAL_MAX_SPEED.toDouble()
        }
        if (packetBootSession.pendingOutboundStep &&
            movement.length() > outboundStepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON
        ) {
            return SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED
        }
        val sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
        val segmentValidator = if (packetAStarAttackActive) {
            createServerMovementSpearKillSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = sessionBoundingBox,
            )
        } else {
            createServerValidatedSpearKillDirectPacketSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = sessionBoundingBox,
            )
        }
        return if (isSpearKillPacketStepClear(
            sessionOrigin = sessionOrigin,
            committedOffset = packetBootSession.committedOffset,
            candidateOffset = packetBootSession.virtualOffset,
            maxStepLength = outboundStepLimit,
            segmentValidator = segmentValidator,
        )) {
            SpearKillPendingPacketStepValidation.CLEAR
        } else {
            SpearKillPendingPacketStepValidation.BLOCKED
        }
    }

    /** Revalidates the complete same-tick displacement, not merely each fall-safe wire segment. */
    private fun validatePendingSpearKillTerminalBurst(): SpearKillPendingPacketStepValidation {
        val movement = packetBootSession.pendingTerminalBurstMovement
            ?: return SpearKillPendingPacketStepValidation.CLEAR
        val sessionOrigin = packetSessionOrigin ?: return SpearKillPendingPacketStepValidation.BLOCKED
        val outboundStepLimit = previewSpearKillOutboundStep().stepLimit
        if (movement.length() > outboundStepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON) {
            return SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED
        }
        val sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
        val segmentValidator = if (packetAStarAttackActive) {
            createServerMovementSpearKillSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = sessionBoundingBox,
            )
        } else {
            createServerValidatedSpearKillDirectPacketSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = sessionBoundingBox,
            )
        }
        return if (isSpearKillPacketStepClear(
                sessionOrigin = sessionOrigin,
                committedOffset = packetBootSession.committedOffset,
                candidateOffset = packetBootSession.committedOffset.add(movement),
                maxStepLength = outboundStepLimit,
                segmentValidator = segmentValidator,
            )
        ) {
            SpearKillPendingPacketStepValidation.CLEAR
        } else {
            SpearKillPendingPacketStepValidation.BLOCKED
        }
    }

    private fun rejectPendingSpearKillPacketStep(validation: SpearKillPendingPacketStepValidation) {
        val outboundStep = packetBootSession.pendingOutboundStep
        packetBootSession.confirmStep(delivered = false)
        plannedPacket = null
        awaitingVanillaMovementPacket = false
        if (validation == SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED && outboundStep) {
            replanPacketRouteForCurrentBudget()
        } else if (validation == SpearKillPendingPacketStepValidation.BLOCKED && outboundStep) {
            terminatePacketFollow(lockedAStarTarget, PacketFollowTermination.BLOCKED)
        }
    }

    private fun replanPacketRouteForCurrentBudget() {
        val target = lockedAStarTarget
        val sessionOrigin = packetSessionOrigin ?: run {
            clearAttack("budget-replan-without-origin")
            return
        }
        if (target == null) {
            beginSafeExactReturn()
            applyConfirmedPhysicalReturnPosition()
            return
        }
        val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
        if (packetAStarAttackActive) {
            when (replanLockedAStarTarget(target, routeOrigin, sessionOrigin)) {
                SpearKillPacketRouteReplanResult.INSTALLED -> Unit
                SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE,
                SpearKillPacketRouteReplanResult.BLOCKED,
                -> {
                    beginSafeExactReturn()
                    applyConfirmedPhysicalReturnPosition()
                }
            }
        } else {
            when (installReplannedDirectPacketRoute(target, routeOrigin, sessionOrigin)) {
                SpearKillPacketRouteReplanResult.INSTALLED,
                SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE,
                -> Unit
                SpearKillPacketRouteReplanResult.BLOCKED -> {
                    beginSafeExactReturn()
                    applyConfirmedPhysicalReturnPosition()
                }
            }
        }
        synchronizeSpearKillServerSneak()
    }

    /** Keeps the server-side crouch bit on any normal input packet emitted during the route. */
    @Suppress("unused")
    private val serverSneakPacketHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        if (event.origin != TransferOrigin.OUTGOING || !serverSneaking) return@handler

        val packet = event.packet as? ServerboundPlayerInputPacket ?: return@handler
        packet.forceSneak = true
    }

    @Suppress("unused")
    private val packetSafetyHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        if (event.origin != TransferOrigin.OUTGOING) return@handler

        if (shouldSuppressSpearKillKineticResetPacket(
                holdingStrike = packetBootSession.holdingStrike,
                clientTickEndPacket = event.packet is ServerboundClientTickEndPacket,
            )
        ) {
            event.cancelEvent()
            return@handler
        }

        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        if (setbackRollback.confirming) {
            packet.onGround = isSpearKillPositionNearGround(spearKillPacketPosition(packet))
            return@handler
        }
        if (!packetBootSession.active) {
            applySpearKillPathHeading(packet, motionPacketHeading)
            return@handler
        }

        val carriesPendingStep = awaitingVanillaMovementPacket && plannedPacket == null &&
            packetBootSession.requiresDelivery
        val pendingValidation = if (carriesPendingStep) {
            validatePendingSpearKillPacketStep()
        } else {
            SpearKillPendingPacketStepValidation.CLEAR
        }
        val pendingRejection = resolveSpearKillPendingPacketStepRejection(
            packetAlreadyCancelled = event.isCancelled,
            validation = pendingValidation,
        )
        if (carriesPendingStep && pendingRejection != null) {
            if (!event.isCancelled) {
                event.cancelEvent()
            }
            rejectPendingSpearKillPacketStep(pendingRejection)
            return@handler
        }

        if (shouldSuppressSpearKillStrikeHoldPacket(packetBootSession.holdingStrike)) {
            event.cancelEvent()
            return@handler
        }

        if (carriesPendingStep) {
            plannedPacket = packet
        }
        val virtualOffset = spearKillPacketVirtualOffset(
            carriesPendingStep = packet === plannedPacket,
            committedOffset = packetBootSession.committedOffset,
            pendingOffset = packetBootSession.virtualOffset,
        )
        val virtualPosition = packetPositionOrigin().add(virtualOffset)
        applySpearKillVirtualPosition(
            packet = packet,
            playerPosition = packetPositionOrigin(),
            virtualOffset = virtualOffset,
            grounded = isSpearKillPositionNearGround(virtualPosition),
            heading = packetBootSession.pathHeading,
        )
        virtualSessionPackets += packet
    }

    @Suppress("unused")
    private val fallDamagePacketHandler = handler<PacketEvent>(priority = (SAFETY_FEATURE - 1).toShort()) { event ->
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        if (
            event.origin != TransferOrigin.OUTGOING ||
            setbackRollback.confirming
        ) {
            return@handler
        }

        if (packet in virtualFallStabilizationPackets) {
            // packetSafetyHandler preserves the committed virtual position but recomputes ground
            // from collision. Restore Direct's intentional spoof and track its exact final state.
            fallDamageDeliveryTracker.protect(packet)
            return@handler
        }
        if (packet in virtualFallGroundingPackets) {
            return@handler
        }
        if (fallSafetyLifecycle.active) {
            val pendingMovement = packetBootSession.pendingMovement ?: return@handler
            if (packet === plannedPacket &&
                fallSafetyLifecycle.shouldGroundPendingMovement(pendingMovement) &&
                isSpearKillPositionNearGround(spearKillPacketPosition(packet))
            ) {
                fallDamageDeliveryTracker.protect(packet)
            } else if (packet === plannedPacket) {
                packet.onGround = false
            }
            return@handler
        }
        if (!hasActiveAttackPath || !shouldProtectFallDamage) return@handler
        if (packetBootSession.active && (packet !== plannedPacket || packetBootSession.virtualOffset.y != 0.0)) {
            return@handler
        }

        if (isSpearKillPositionNearGround(spearKillPacketPosition(packet))) {
            fallDamageDeliveryTracker.protect(packet)
        } else {
            packet.onGround = false
        }
    }

    @Suppress("unused")
    private val packetDeliveryHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        if (event.origin == TransferOrigin.INCOMING) {
            when (val packet = event.packet) {
                is ClientboundDamageEventPacket -> if (!event.isCancelled &&
                    damageEvidenceTracker.observe(packet.entityId, player.tickCount) != null
                ) {
                    attemptTracker.markDamageEvidence()
                    if (attemptRouteCompleted) {
                        attemptTracker.complete()
                        attemptRouteCompleted = false
                    }
                }
                is ClientboundPlayerPositionPacket -> if (!event.isCancelled) {
                    lastServerCorrectionTick = player.tickCount
                    if (setbackGuard.armed) {
                        speedController.rejectOutboundProgress()
                        attemptTracker.markSetback()
                        setbackRollback.mark(packet)
                    }
                }
            }
            return@handler
        }

        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        val virtualFallGroundingPacket = virtualFallGroundingPackets.remove(packet)
        val virtualFallStabilizationPacket = virtualFallStabilizationPackets.remove(packet)
        val virtualPacket = virtualSessionPackets.remove(packet)
        val plannedPathPacket = packet === plannedPacket
        val pathPacket = virtualFallGroundingPacket || virtualFallStabilizationPacket ||
            virtualPacket || plannedPathPacket

        val queuedByBlink = pathPacket && BlinkManager.packetQueue.any { it.packet === packet }
        if (queuedByBlink) {
            BlinkManager.packetQueue.removeIf { it.packet === packet }
        }

        val delivered = spearKillPacketDeliveryConfirmed(
            packetCancelled = event.isCancelled,
            queuedByBlink = queuedByBlink,
        )
        if (pathPacket && delivered && packet.hasPosition()) ownedMovementPacketsThisTick++
        val exactGroundPacketDelivered = fallDamageDeliveryTracker.confirmFinalState(
            packet,
            cancelled = !delivered,
        )
        if (exactGroundPacketDelivered) {
            player.resetFallDistance()
        }

        val groundingConfirmed = if (virtualFallGroundingPacket) {
            fallSafetyLifecycle.confirmGrounding(delivered)
        } else {
            false
        }
        if (virtualFallGroundingPacket) {
            if (groundingConfirmed) {
                finishSpearKillFallSafety(player.position(), allowPacket = true)
            }
        }
        if (virtualFallStabilizationPacket) {
            virtualFallStabilizationDelivered = fallSafetyLifecycle.confirmStabilization(
                delivered = exactGroundPacketDelivered,
            )
        }

        if (!pathPacket) return@handler

        if (virtualPacket && delivered && packet.hasPosition()) {
            setbackGuard.record(
                Vec3(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0)),
                player.position(),
            )
        }

        if (!plannedPathPacket) return@handler

        val deliveredMovement = packetBootSession.pendingMovement
        val terminalBurstStep = packetBootSession.pendingTerminalBurstMovement != null
        val logicalOutboundCompletion = packetBootSession.pendingLogicalOutboundCompletion
        val deliveredOutboundStep = delivered && packetBootSession.pendingOutboundStep
        val fallMovementConfirmed = deliveredMovement?.let { movement ->
            fallSafetyLifecycle.confirmMovement(
                movement = movement,
                delivered = delivered,
                exactPacketGrounded = exactGroundPacketDelivered,
            )
        } ?: false
        packetBootSession.confirmStep(delivered)
        if (delivered) {
            packetRecoveryStallTicks = nextSpearKillRecoveryStallTicks(
                currentTicks = packetRecoveryStallTicks,
                madeProgress = true,
            )
        }
        if (deliveredOutboundStep) {
            attemptTracker.recordOutboundStep()
            deliveredMovement?.let { movement ->
                if (terminalBurstStep) {
                    terminalBurstDeliveredMovementThisTick =
                        terminalBurstDeliveredMovementThisTick.add(movement)
                }
                if (logicalOutboundCompletion) {
                    confirmSpearKillOutboundStep()
                    lastDeliveredOutboundMovement = if (terminalBurstStep) {
                        terminalBurstDeliveredMovementThisTick
                    } else {
                        movement
                    }
                }
            }
        }
        if (delivered) {
            deliveredMovement?.let { movement ->
                lastDeliveredMovement = if (terminalBurstStep && logicalOutboundCompletion) {
                    terminalBurstDeliveredMovementThisTick
                } else {
                    movement
                }
            }
            if (!fallMovementConfirmed) {
                stopFailClosedPacketRoute()
            }
            if (fallMovementConfirmed) {
                sendReturnArrivalConfirmations(packetPositionOrigin().add(packetBootSession.committedOffset))
            }
        }
        val completedCleanNetworkRoundTrip = !packetBootSession.active &&
            packetSessionSettings?.networkOptimized == true &&
            !packetSetbackRecoveryAttempted &&
            attemptTracker.current?.setback == false
        applyConfirmedPhysicalReturnPosition()
        if (!packetBootSession.active) {
            finishSpearKillFallSafety(player.position(), allowPacket = true)
            if (completedCleanNetworkRoundTrip) {
                networkOptimizer.recordSuccessfulRoundTrip()
            }
            packetSessionOrigin = null
            packetSessionSettings = null
            requestSpearKillAttemptCompletion()
            synchronizeSpearKillServerSneak()
        }
        plannedPacket = null
        awaitingVanillaMovementPacket = false
    }

    private fun packetPositionOrigin(): Vec3 = packetSessionOrigin ?: player.position()

    private fun applyConfirmedPhysicalReturnPosition(
        targetPlayer: Player = player,
    ) {
        val origin = packetSessionOrigin ?: return
        packetBootSession.consumePhysicalPositionOffset()?.let { offset ->
            val physicalPosition = physicalReturnPositioner.resolve(origin, targetPlayer.position(), offset)
                ?: return@let
            targetPlayer.setPos(physicalPosition)
            targetPlayer.deltaMovement = Vec3.ZERO
        }
        if (!packetBootSession.active) {
            finishSpearKillFallSafety(targetPlayer.position(), allowPacket = true, targetPlayer = targetPlayer)
            packetSessionOrigin = null
            packetSessionSettings = null
            activeMovementTransport = null
            physicalReturnPositioner.clear()
            resetSpearKillSpeedSession()
        }
    }

    internal fun preparePacketSetback(packet: ClientboundPlayerPositionPacket, player: Player) {
        if (!setbackRollback.isMarked(packet)) return
        attemptTracker.markSetback()
        val rejectedRouteTarget = lockedAStarTarget
        if (player.isPassenger) {
            clearAttack("setback-passenger")
            rejectedRouteTarget?.let(::rejectSpearKillTarget)
            return
        }

        val localState = SpearKillLocalPlayerState.capture(player)
        val sessionOrigin = packetSessionOrigin ?: returnRecoveryTracker.recoveryOrigin ?: player.position()
        if (returnRecoveryTracker.recoveryOrigin == null) {
            returnRecoveryTracker.begin(sessionOrigin)
        }
        if (!physicalReturnPositioner.followingReturn) {
            returnRecoveryTracker.observeCombatPosition(localState.movement.position)
        }
        val preparedSetback = setbackRollback.prepare(
            packet,
            localState,
            setbackGuard,
            physicalReturn = packetBootSession.physicalReturnConfigured,
            sessionOrigin = sessionOrigin,
            exactRecoveryMovementsFor = { offset ->
                createCollisionSafeSetbackRecovery(sessionOrigin, offset)
            },
        )
        if (preparedSetback == null) {
            pendingSetbackFallDistance = null
            pendingSetbackConfirmedOffset = null
            clearAttack("setback-unrecoverable")
            rejectedRouteTarget?.let(::rejectSpearKillTarget)
            return
        }

        val recoverySettings = packetSessionSettings
        if (recoverySettings?.networkOptimized == true) {
            networkOptimizer.recordSetback(
                currentTick = player.tickCount,
                backoffTicks = recoverySettings.setbackBackoffTicks,
            )
        }
        pendingSetbackFallDistance = maxOf(
            player.fallDistance.toDouble(),
            fallSafetyLifecycle.confirmedFallDistance.takeIf { fallSafetyLifecycle.active } ?: 0.0,
        )
        pendingSetbackConfirmedOffset = packetBootSession.committedOffset
        clearVirtualMovementState()
        physicalReturnPositioner.clear()
        packetRecoveryStallTicks = 0
        rejectedRouteTarget?.let(::rejectSpearKillTarget)
        synchronizeSpearKillServerSneak()
        packetSessionSettings = recoverySettings
        activeMovementTransport = recoverySettings?.transport
        packetSetbackRecoveryAttempted = true
    }

    internal fun finishPacketSetback(packet: ClientboundPlayerPositionPacket, player: Player) {
        val setback = setbackRollback.finish(packet)
        if (setback == null) {
            if (player.isPassenger && setbackRollback.isMarked(packet)) clearAttack()
            return
        }

        setback.localState.restore(player)
        setbackGuard.clear()
        val authoritativePosition = setback.sessionOrigin.add(setback.authoritativeOffset)
        val correctionDescent = pendingSetbackConfirmedOffset
            ?.let { confirmed -> (confirmed.y - setback.authoritativeOffset.y).coerceAtLeast(0.0) }
            ?: 0.0
        val recoveryFallDistance = maxOf(
            pendingSetbackFallDistance ?: 0.0,
            player.fallDistance.toDouble(),
        ) + correctionDescent
        pendingSetbackFallDistance = null
        pendingSetbackConfirmedOffset = null
        startPacketFirstReturnRecovery(
            authoritativePosition = authoritativePosition,
            targetPlayer = player,
            preferredFirstLeg = setback.exactRecoveryMovements,
            initialFallDistance = recoveryFallDistance,
        )
        synchronizeSpearKillServerSneak()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        failureNotificationGate.clear()
        networkOptimizer.reset()
        holdUseLaunchTarget = null
        clearAttack("world-change", allowFallSafetyPacket = false)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        // The connection is already closing; clear our local ownership without enqueueing a packet.
        serverSneaking = false
        failureNotificationGate.clear()
        networkOptimizer.reset()
        holdUseLaunchTarget = null
        clearAttack("disconnect", allowFallSafetyPacket = false)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (shouldRenderSpearKillAStarPath(
                previewEnabled = Preview.enabled,
                packetAStarEnabled = packetAStarAttackActive,
                renderPathEnabled = Preview.renderPath,
                renderPath = plannedAStarRenderPath,
            )
        ) {
            renderSpearKillAStarPath(
                event,
                plannedAStarRenderPath,
                SpearKillAStarPathAppearance(Preview.Glow.glowColor, Preview.Glow.glowStyle.style),
            )
        }

        if (!Preview.enabled || Preview.mode.activeMode !== Preview.Box || !isUsingSpear) return@handler
        previewTarget?.let { target ->
            event.renderEnvironment {
                withPositionRelativeToCamera {
                    if (target is EnderDragon) {
                        target.subEntities.forEach {
                            drawBox(it.boundingBox, Preview.Box.fillColor, Preview.Box.outlineColor)
                        }
                    } else {
                        drawBox(target.boundingBox, Preview.Box.fillColor, Preview.Box.outlineColor)
                    }
                }
            }
        }
    }

    override val running: Boolean
        get() = super.running || packetBootSession.active || fallSafetyLifecycle.active ||
            setbackGuard.armed || setbackRollback.confirming || killAuraReturnActive

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacySpearKillConfig(jsonObject)
    }

    override fun onDisabled() {
        failureNotificationGate.clear()
        networkOptimizer.reset()
        holdUseLaunchTarget = null
        clearAttack("disabled")
        super.onDisabled()
    }
}

/** Extra distance around an entity's vanilla/Hitbox pick box that still counts as a crosshair selection. */
private const val SPEAR_KILL_TARGET_SELECTION_MARGIN = 0.75
private const val KILL_AURA_INHERITED_TARGET_SOURCE = "KillAura"
private const val KILL_AURA_DISABLED_REASON = "killaura-disabled"
private const val SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED = 1e-9
private const val SPEAR_KILL_MIN_ATTACK_RAY_RANGE = 2.0
private const val SPEAR_KILL_ATTACK_RAY_RANGE = 4.5
private const val SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS = 250L
private const val SPEAR_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS = 200
private const val SPEAR_KILL_REJECTED_TARGET_RETRY_TICKS = 20
private const val SPEAR_KILL_MAX_RECOVERY_STALL_TICKS = 40
private const val SPEAR_KILL_RECOVERY_STEP_EPSILON = 1.0E-6
private const val SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED = 1.0E-6
private const val SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED = 1.0E-12
private const val SPEAR_KILL_NEAR_GROUND_PROBE_DEPTH = 0.08
private const val SPEAR_KILL_NEAR_GROUND_HORIZONTAL_INSET = 0.001
private const val SPEAR_KILL_HITBOX_RAYCAST_MAX_SPAN_LENGTH = 4.0
private const val SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT = 4
private const val SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS = 16_384
private const val SPEAR_KILL_DIRECT_SNAPSHOT_HORIZONTAL_MARGIN = 0
private const val SPEAR_KILL_DIRECT_SNAPSHOT_VERTICAL_MARGIN = 0
private const val SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN = 10
private const val SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN = 6

internal data class SpearKillLookRayPriority(
    val directlyHovered: Boolean,
    val angularErrorSquared: Double,
    val distanceAlongRaySquared: Double,
) : Comparable<SpearKillLookRayPriority> {

    override operator fun compareTo(other: SpearKillLookRayPriority): Int {
        if (directlyHovered != other.directlyHovered) {
            return if (directlyHovered) -1 else 1
        }
        val angularComparison = angularErrorSquared.compareTo(other.angularErrorSquared)
        return if (angularComparison != 0) {
            angularComparison
        } else {
            distanceAlongRaySquared.compareTo(other.distanceAlongRaySquared)
        }
    }
}

/** Fixed look-ray pad around the entity hitbox. Intentionally not distance-scaled. */
internal fun spearKillTargetSelectionMargin(): Double = SPEAR_KILL_TARGET_SELECTION_MARGIN

internal fun spearKillLookRayPriority(
    entityBox: AABB,
    eye: Vec3,
    lookEnd: Vec3,
    hitboxMargin: Double = SPEAR_KILL_TARGET_SELECTION_MARGIN,
): SpearKillLookRayPriority? {
    if (lookEnd.distanceToSqr(eye) <= SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED) return null
    if (!hitboxMargin.isFinite() || hitboxMargin < 0.0) return null

    val lookRay = LineSegment(eye, lookEnd)
    lookRay.firstIntersectionWith(entityBox)?.let { hitPoint ->
        return SpearKillLookRayPriority(
            directlyHovered = true,
            angularErrorSquared = 0.0,
            distanceAlongRaySquared = eye.distanceToSqr(hitPoint),
        )
    }

    val expandedHit = lookRay.firstIntersectionWith(entityBox.inflate(hitboxMargin)) ?: return null
    val nearest = lookRay.getNearestPointTo(entityBox)
    val distanceAlongRaySquared = eye.distanceToSqr(expandedHit)
    val angularErrorSquared = nearest.distanceSquared /
        maxOf(eye.distanceToSqr(nearest.point), SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED)

    return SpearKillLookRayPriority(
        directlyHovered = false,
        angularErrorSquared = angularErrorSquared,
        distanceAlongRaySquared = distanceAlongRaySquared,
    )
}

internal fun isNearSpearKillLookRay(entityBox: AABB, eye: Vec3, lookEnd: Vec3): Boolean =
    spearKillLookRayPriority(entityBox, eye, lookEnd) != null

internal fun findSpearKillAttackHitPoint(
    eye: Vec3,
    direction: Vec3,
    targetBox: AABB,
    range: Double,
): Vec3? {
    if (!range.isFinite() || range <= 0.0) return null

    val normalizedDirection = direction.normalize()
    if (normalizedDirection.lengthSqr() <= SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED) return null

    return targetBox.clip(eye, eye.add(normalizedDirection.scale(range))).orElse(null)
}

internal fun findSpearKillTerminalAttackHitPoint(
    eye: Vec3,
    terminalMovement: Vec3,
    targetBox: AABB,
    range: Double,
): Vec3? = findSpearKillAttackHitPoint(
    eye = eye,
    direction = terminalMovement,
    targetBox = targetBox,
    range = range,
)

internal fun shouldClearSpearKillAStarRenderPath(
    attackKeyDown: Boolean,
    packetSessionActive: Boolean,
): Boolean = !attackKeyDown && !packetSessionActive

internal fun isSpearKillAttackRequested(
    attackKeyDown: Boolean,
    attackPressedRecently: Boolean,
): Boolean = attackKeyDown || attackPressedRecently

internal fun calculateSpearKillAttackDirection(
    playerEyePosition: Vec3,
    predictedTargetPosition: Vec3,
    targetEyeOffset: Vec3,
    fallbackDirection: Vec3,
): Vec3 {
    val targetDirection = predictedTargetPosition
        .add(targetEyeOffset)
        .subtract(playerEyePosition)

    return targetDirection.normalize().takeIf { it.lengthSqr() > 0 }
        ?: fallbackDirection.normalize()
}

internal fun migrateLegacySpearKillPreviewConfig(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val storedMode = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .firstOrNull { it["name"]?.asString == "Mode" }

    if (storedMode?.has("choices") == true) return

    val activeMode = when {
        storedMode?.get("value")?.asString.equals("Glow", ignoreCase = true) -> "Glow"
        else -> "Box"
    }
    val boxValues = JsonArray()
    val glowValues = JsonArray()
    val retainedValues = JsonArray()

    for (storedValue in storedValues) {
        if (!storedValue.isJsonObject) {
            retainedValues.add(storedValue.deepCopy())
            continue
        }

        val setting = storedValue.asJsonObject
        when (setting["name"]?.asString) {
            "Mode" -> Unit
            in BOX_PREVIEW_SETTING_NAMES -> boxValues.add(setting.deepCopy())
            in GLOW_PREVIEW_SETTING_NAMES -> glowValues.add(setting.deepCopy())
            else -> retainedValues.add(setting.deepCopy())
        }
    }

    retainedValues.add(spearKillPreviewModeValue(activeMode, boxValues, glowValues))
    jsonObject.add("value", retainedValues)
}

private fun spearKillPreviewModeValue(activeMode: String, boxValues: JsonArray, glowValues: JsonArray) =
    JsonObject().apply {
        addProperty("name", "Mode")
        addProperty("active", activeMode)
        add("value", JsonArray())
        add("choices", JsonObject().apply {
            add("Box", spearKillPreviewChoiceValue("Box", boxValues))
            add("Glow", spearKillPreviewChoiceValue("Glow", glowValues))
        })
    }

private fun spearKillPreviewChoiceValue(name: String, values: JsonArray) = JsonObject().apply {
    addProperty("name", name)
    add("value", values)
}

private val BOX_PREVIEW_SETTING_NAMES = setOf("FillColor", "OutlineColor")
private val GLOW_PREVIEW_SETTING_NAMES = setOf(
    "GlowColor",
    "Radius",
    "Softness",
    "Intensity",
    "CoreSize",
    "Opacity",
)
