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
import net.ccbluex.liquidbounce.utils.client.isNewerThanOrEquals1_21_6
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.entity.lastPos
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
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
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
    private val virtualFallState = SpearKillVirtualFallState()
    private val attemptTracker = SpearKillAttemptTracker()
    private val damageEvidenceTracker = SpearKillDamageEvidenceTracker()
    private val failureNotificationGate = SpearKillFailureNotificationGate(
        SPEAR_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS,
    )
    private val virtualSessionPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    private val virtualFallGroundingPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    private val rejectedTargets = Collections.newSetFromMap(
        IdentityHashMap<LivingEntity, Boolean>(),
    )
    private var previewTarget: LivingEntity? = null
    private var plannedAStarRenderPath: List<Vec3> = emptyList()
    private var packetAStarAttackActive = false
    private var plannedPacket: ServerboundMovePlayerPacket? = null
    private var awaitingVanillaMovementPacket = false
    private var packetSessionOrigin: Vec3? = null
    private var lockedAStarTarget: LivingEntity? = null
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
    private var virtualFallGroundingDelivered = false
    private var attemptRouteCompleted = false
    private var manualAttackRequestLatched = false
    private var serverSneaking = false
    private var fightBotSpearTarget: LivingEntity? = null
    private var fightBotSpearState = SpearKillFightBotState.Unavailable
    private var fightBotStartedUse = false
    private var fightBotSilentHotbarSlot: Int? = null
    private var fightBotUseHand: InteractionHand? = null
    private var pendingKillAuraTarget: LivingEntity? = null
    private var killAuraSpearTarget: LivingEntity? = null
    private var killAuraStartedSpearUse = false
    private var killAuraSpearUseHand: InteractionHand? = null

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

    private data class AStarTargetPrediction(
        val observedPosition: Vec3,
        val position: Vec3,
        val eyePosition: Vec3,
        val boundingBox: AABB,
    )

    private data class SpearKillPacketSessionSettings(
        val transport: SpearKillMovementTransport,
        val stepWaitTicks: Int,
    )

    private data class PacketChainPlan(
        val outboundMovements: List<Vec3>,
        val routeMode: String,
        val hitTicks: Int,
        val terminalBurstSteps: Int = 0,
        val aStarPlan: AStarAttackPlan? = null,
    )

    private enum class PacketFollowTermination(
        val rejectTarget: Boolean,
        val notificationKey: String?,
    ) {
        DEFEATED(rejectTarget = false, notificationKey = null),
        UNREACHABLE(rejectTarget = true, notificationKey = "targetUnreachable"),
        BLOCKED(rejectTarget = true, notificationKey = "pathBlocked"),
    }

    private enum class PendingPacketStepValidation {
        CLEAR,
        BUDGET_EXCEEDED,
        BLOCKED,
    }

    private enum class PacketRouteReplanResult {
        INSTALLED,
        TRANSIENT_FAILURE,
        BLOCKED,
    }

    internal val currentAttackVelocity get() = if (packetBootSession.active) 0.0 else currentMovement.length()
    internal val currentAttackDirection get() = currentMovement.normalize()
    internal val usesPacketMovement get() = packetBootSession.active
    private val currentMovement get() = attackMovements.firstOrNull() ?: Vec3.ZERO
    private val hasActiveAttackPath get() = attackMovements.isNotEmpty() || packetBootSession.active
    private val activeRouteHeading: Rotation?
        get() = when {
            packetBootSession.active -> packetBootSession.pathHeading
            attackMovements.isNotEmpty() -> spearKillKineticHeading(currentMovement)
            else -> null
        }
    internal val controlsSpearUse get() = hasActiveAttackPath
    internal val maximumTargetRange get() = maxTargetDistance
    internal val currentAttemptSnapshot get() = attemptTracker.current
    internal val lastAttemptSnapshot get() = attemptTracker.lastCompleted
    internal val fightBotRouteTarget: LivingEntity?
        get() = fightBotSpearTarget.takeIf {
            fightBotSpearState == SpearKillFightBotState.RouteActive && hasActiveAttackPath
        }
    internal val ownsKillAuraRoute
        get() = attemptTracker.current?.targetSource == KILL_AURA_INHERITED_TARGET_SOURCE &&
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
        target in rejectedTargets -> rejectFightBotSpearUse(target)
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
                isRejected = target in rejectedTargets,
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
            attackRequested = hasActivationRequest,
            isUsingSpear = isUsingSpear,
        )
    private val usesPacketMovementMode get() = movement.activeMode === movementConfiguration.packet
    private val packetRoutingMode
        get() = if (movementConfiguration.packet.routing.activeMode === movementConfiguration.packet.aStar) {
            SpearKillRoutingMode.A_STAR
        } else {
            SpearKillRoutingMode.DIRECT
        }
    private val packetRoutingSupportsAStar
        get() = usesPacketMovementMode && packetRoutingMode == SpearKillRoutingMode.A_STAR
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
        targetSpeed = movementConfiguration.targetSpeed.toDouble(),
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
            targetSpeed = movementConfiguration.targetSpeed.toDouble(),
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
        if (prepareElytra) {
            requestSpearKillPacketFallFlight()
        }
        return SpearKillPacketSessionSettings(
            transport = resolveSpearKillMovementTransport(
                configuredSpeed = movementConfiguration.targetSpeed.toDouble(),
                configuredStepLimit = packet.stepDistance.toDouble(),
                elytraActive = isSpearKillElytraActive,
            ),
            stepWaitTicks = packet.stepDelay,
        )
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
    private val isSpearUseRequested
        get() = isUseInputHeld || hasFightBotSpearRequest || hasKillAuraSpearRequest
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
        if (target == null) {
            clearKillAuraSpearUse()
            return
        }

        killAuraSpearTarget = target
        if (!maintainKillAuraSpearUse()) {
            clearKillAuraSpearUse()
        }
    }

    private fun currentKillAuraSpearUseTarget(): LivingEntity? = when {
        ownsKillAuraRoute && hasActiveAttackPath -> lockedAStarTarget
        acceptsKillAuraDelegation -> ModuleKillAura.targetForSpearKill()
        else -> null
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
        killAuraStartedSpearUse = false
        killAuraSpearUseHand = null
    }

    /** Collision pose the server will use after SpearKill's optional sneak input has arrived. */
    private fun spearKillServerCollisionBoxAt(position: Vec3): AABB = if (shouldMaintainSpearKillServerSneak) {
        player.getDimensions(Pose.CROUCHING).makeBoundingBox(position)
    } else {
        player.boundingBox.move(position.subtract(player.position()))
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
        damageEvidenceTracker.arm(target.id, predictedHitTick)
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
    }

    private fun currentSpearKillKineticEstimate(): SpearKillKineticSpeedEstimate {
        val targetMovement = lockedAStarTarget?.let { it.position().subtract(it.lastPos) } ?: Vec3.ZERO
        return estimateSpearKillKineticSpeed(lastDeliveredOutboundMovement, targetMovement, player.lookAngle)
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
        packetBootSession.beginExactReturn()
        applyConfirmedPhysicalReturnPosition()
        if (!packetBootSession.active) {
            packetSessionSettings = null
            activeMovementTransport = null
        }
        synchronizeSpearKillServerSneak()
    }

    private fun clearVirtualAttack() {
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
        resetSpearKillSpeedSession()
    }

    /** Discards the current virtual path while retaining the recovery origins and transport. */
    private fun clearVirtualMovementState() {
        previewTarget = null
        rejectedTargets.clear()
        packetAStarAttackActive = false
        clearAStarRenderPath()
        attackMovements.clear()
        motionPacketHeading = null
        BlinkManager.packetQueue.removeIf { snapshot ->
            val packet = snapshot.packet
            packet === plannedPacket || packet is ServerboundMovePlayerPacket &&
                (packet in virtualSessionPackets || packet in virtualFallGroundingPackets)
        }
        virtualSessionPackets.clear()
        fallDamageDeliveryTracker.clear()
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

    private fun beginVirtualFallSafety() {
        virtualFallGroundingPackets.clear()
        virtualFallState.begin(player.fallDistance.toDouble())
        virtualFallGroundingDelivered = false
    }

    private fun resetVirtualFallSafety() {
        virtualFallGroundingPackets.clear()
        virtualFallState.reset()
        virtualFallGroundingDelivered = false
    }

    private fun clearAStarTargetLock() {
        lockedAStarTarget = null
        plannedAStarApproach = null
        plannedAStarTargetPosition = null
        plannedAStarTargetVelocity = Vec3.ZERO
        aStarPlanTick = 0
    }

    private fun clearAttack(reason: String = "cleared") {
        abortSpearKillAttempt(reason)
        clearKillAuraSpearUse()
        clearVirtualAttack()
        setbackGuard.clear()
        setbackRollback.clear()
        packetSetbackRecoveryAttempted = false
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

    private fun isTargetCandidateEligibleAt(entity: LivingEntity, referencePosition: Vec3): Boolean =
        isSpearKillTargetCandidateEligible(
            isCombatSafe = entity.shouldBeAttacked(),
            isAlive = entity.isAlive && !entity.isRemoved,
            isInCurrentWorld = entity.level() === world,
            isWithinRange = referencePosition.distanceTo(entity.position()) in
                3.0..maxTargetDistance.toDouble(),
            isRejected = entity in rejectedTargets,
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
        if (!isTargetCandidateEligibleAt(target, routePosition)) return null

        val distance = routePosition.distanceTo(target.position())
        if (distance !in 3.0..maxTargetDistance.toDouble()) return null
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
    ): Boolean {
        val hitPoint = findSpearKillAttackHitPoint(eye, direction, targetBox, range) ?: return false
        return hasLineOfSight(eye, hitPoint, player)
    }

    private fun createAttackMovement(target: LivingEntity, distance: Double): SpearKillAttackStartResult {
        beginSpearKillSpeedSession()
        if (!usesPacketMovementMode) {
            return startSpearKillMotionAttack(target, distance)
        }

        val settings = resolveSpearKillPacketSettings(prepareElytra = true)
        activeMovementTransport = settings.transport
        val startResult = startSpearKillPacketRoute(
            mode = packetRoutingMode,
            startDirect = {
                startDirectPacketAttack(
                    target = target,
                    distance = distance,
                    settings = settings,
                    routeMode = if (packetRoutingMode == SpearKillRoutingMode.A_STAR) {
                        "AStar→Direct"
                    } else {
                        "Direct"
                    },
                )
            },
            startAStar = {
                motionPacketHeading = null
                packetSessionSettings = settings
                startAStarPacketAttack(
                    target = target,
                    settings = settings,
                    routeMode = "AStar",
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

    private fun startDirectPacketAttack(
        target: LivingEntity,
        distance: Double,
        settings: SpearKillPacketSessionSettings,
        routeMode: String,
    ): SpearKillAttackStartResult {
        val origin = player.position()
        val route = createDirectPacketRoute(
            target = target,
            routeOrigin = origin,
            travel = distance,
            settings = settings,
            sessionOrigin = origin,
        ) ?: return SpearKillAttackStartResult.BLOCKED
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
            ?: return SpearKillAttackStartResult.RETRY_LATER
        if (!hasSpearKillDirectPacketDamageWindow(
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = kineticWeapon.computeDamageUseDuration(),
                stepCount = route.outboundTickCount,
                stepWaitTicks = settings.stepWaitTicks,
            )
        ) {
            return SpearKillAttackStartResult.RETRY_LATER
        }

        motionPacketHeading = null
        packetSessionSettings = settings
        packetAStarAttackActive = false
        clearAStarRenderPath()
        plannedAStarApproach = null
        packetSessionOrigin = origin
        physicalReturnPositioner.clear()
        returnRecoveryTracker.begin(origin)
        beginVirtualFallSafety()
        startSpearKillDirectPacketSession(
            session = packetBootSession,
            route = route,
            stepWaitTicks = settings.stepWaitTicks,
        )
        lockedAStarTarget = target
        plannedAStarTargetPosition = target.position()
        plannedAStarTargetVelocity = target.position().subtract(target.lastPos)
        aStarPlanTick = player.tickCount
        beginSpearKillAttempt(
            target = target,
            routeMode = routeMode,
            outboundSteps = route.outboundMovements.size,
            hitTicks = spearKillDirectPacketHitTicks(route.outboundTickCount, settings.stepWaitTicks),
            terminalAuthorizationRequired = false,
        )
        return SpearKillAttackStartResult.STARTED
    }

    private fun startAStarPacketAttack(
        target: LivingEntity,
        settings: SpearKillPacketSessionSettings,
        routeMode: String,
    ): SpearKillAttackStartResult {
        clearAStarRenderPath()
        val origin = player.position()
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        val damageUseDuration = kineticWeapon?.computeDamageUseDuration()
        val plan = createAStarAttackPlan(
            target = target,
            routeOrigin = origin,
            sessionOrigin = origin,
            stepWaitTicks = settings.stepWaitTicks,
        )
        if (plan != null && !isServerAcceptedSpearKillRoute(origin, origin, plan.packetRoute)) {
            packetSessionSettings = null
            return SpearKillAttackStartResult.BLOCKED
        }
        val startResult = classifySpearKillAStarStartFailure(
            routeFound = plan != null && kineticWeapon != null,
            hasDamageWindow = plan != null &&
                damageUseDuration != null &&
                hasSpearKillScheduleDamageWindow(
                    ticksUsingItem = player.ticksUsingItem,
                    damageUseDuration = damageUseDuration,
                    hitTick = plan.schedule.hitTick,
                ),
        )
        if (startResult != SpearKillAttackStartResult.STARTED || plan == null) {
            packetSessionSettings = null
            return startResult
        }

        packetAStarAttackActive = true
        packetSessionOrigin = origin
        physicalReturnPositioner.clear()
        returnRecoveryTracker.begin(origin)
        beginVirtualFallSafety()
        packetBootSession.startPhysicalReturn(
            path = plan.packetRoute.roundTripMovements,
            outboundSteps = plan.packetRoute.outboundMovements.size,
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
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
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
        stepWaitTicks: Int,
    ): AStarAttackPlan? {
        val eyeOffset = player.eyePosition.subtract(player.position())
        val routeEyePosition = routeOrigin.add(eyeOffset)
        val aStar = movementConfiguration.packet.aStar
        val speedProfile = currentSpeedProfile(activeSpeedStepDistance)
        val effectiveMaxSpeed = speedProfile.maximumStepLimit
        val terminalLungeDistance = effectiveMaxSpeed
        val targetExtrapolation = PositionExtrapolation.getBestForEntity(target)
        val seedStepCount = calculateSpearKillProfiledTravel(
            distance = routeOrigin.distanceTo(target.position()),
            profile = speedProfile,
        ).stepCount
        val seedPrediction = predictAStarTarget(
            target = target,
            extrapolation = targetExtrapolation,
            ticks = spearKillPacketTravelTicks(seedStepCount, stepWaitTicks),
        )
        val sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
        val segmentValidator = createFastSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
        val routePlanner = SpearKillAStarRoutePlanner(
            allowDiagonal = aStar.diagonal,
            maxCost = aStar.maxCost,
            canTraverse = segmentValidator::isClear,
        )
        val preferredDirection = calculateSpearKillAttackDirection(
            playerEyePosition = routeEyePosition,
            predictedTargetPosition = seedPrediction.position,
            targetEyeOffset = seedPrediction.eyePosition.subtract(seedPrediction.position),
            fallbackDirection = player.lookAngle,
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
                strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
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
                targetExtrapolation = targetExtrapolation,
                stepWaitTicks = stepWaitTicks,
                terminalLungeDistance = terminalLungeDistance,
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
        seedPrediction: AStarTargetPrediction,
        eyeOffset: Vec3,
        lineOfSightShortcuts: Boolean,
        target: LivingEntity,
        targetExtrapolation: PositionExtrapolation,
        stepWaitTicks: Int,
        terminalLungeDistance: Double,
    ): AStarAttackPlan? {
        val seedSpatialPlan = buildSpatialAStarAttackPlanForApproach(
            approach = approach,
            routeOrigin = routeOrigin,
            routePlanner = routePlanner,
            segmentValidator = segmentValidator,
            effectiveMaxSpeed = effectiveMaxSpeed,
            speedProfile = speedProfile,
            lineOfSightShortcuts = lineOfSightShortcuts,
        ) ?: return null

        val seedSchedule = buildSpearKillAStarPathSchedule(
            outboundStepCount = seedSpatialPlan.packetRoute.outboundMovements.size,
            stepWaitTicks = stepWaitTicks,
            terminalSuffixCount = seedSpatialPlan.terminalSuffixCount,
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        ) ?: return null
        val hitPrediction = predictAStarTarget(target, targetExtrapolation, seedSchedule.hitTick)

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
            targetExtrapolation = targetExtrapolation,
            stepWaitTicks = stepWaitTicks,
        ) ?: refinedSpatialPlan?.let {
            timeSpatialAStarAttackPlan(
                spatialPlan = seedSpatialPlan,
                eyeOffset = eyeOffset,
                target = target,
                targetExtrapolation = targetExtrapolation,
                stepWaitTicks = stepWaitTicks,
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
            maxVerticalStep = safeVirtualFallStep,
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
        target: LivingEntity,
        targetExtrapolation: PositionExtrapolation,
        stepWaitTicks: Int,
    ): AStarAttackPlan? {
        val schedule = buildSpearKillAStarPathSchedule(
            outboundStepCount = spatialPlan.packetRoute.outboundMovements.size,
            stepWaitTicks = stepWaitTicks,
            terminalSuffixCount = spatialPlan.terminalSuffixCount,
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        ) ?: return null
        val hitPrediction = predictAStarTarget(target, targetExtrapolation, schedule.hitTick)
        if (!hasValidAStarTerminalAttackRay(
                targetBox = hitPrediction.boundingBox,
                eyeOffset = eyeOffset,
                approach = spatialPlan.approach,
            )
        ) {
            return null
        }
        return AStarAttackPlan(
            approach = spatialPlan.approach,
            packetRoute = spatialPlan.packetRoute,
            renderPath = spatialPlan.renderPath,
            targetPosition = hitPrediction.observedPosition,
            targetVelocity = target.position().subtract(target.lastPos),
            schedule = schedule,
            preStrikeHoldTicks = SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS,
            terminalSuffixCount = spatialPlan.terminalSuffixCount,
        )
    }

    private fun predictAStarTarget(
        target: LivingEntity,
        extrapolation: PositionExtrapolation,
        ticks: Int,
    ): AStarTargetPrediction {
        val observedPosition = target.position()
        val predictedPosition = extrapolation
            .getPositionInTicks(ticks.toDouble().coerceAtLeast(0.0))
            .takeIf { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
            ?: observedPosition
        val predictionOffset = predictedPosition.subtract(observedPosition)
        return AStarTargetPrediction(
            observedPosition = observedPosition,
            position = predictedPosition,
            eyePosition = target.eyePosition.add(predictionOffset),
            boundingBox = target.boundingBox.move(predictionOffset),
        )
    }

    private fun hasValidAStarTerminalAttackRay(
        targetBox: AABB,
        eyeOffset: Vec3,
        approach: SpearKillAStarAttackApproach,
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
            hasLineOfSight(virtualEyePosition, attackHitPoint, player)
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
        rejectedTargets -= target
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

    private fun tryStartPacketChain(defeatedTarget: LivingEntity): Boolean {
        if (!packetBootSession.canReplaceRemainingOutbound && !packetBootSession.canStartChainedOutbound) {
            return false
        }

        val sessionOrigin = packetSessionOrigin ?: return false
        val settings = packetSessionSettings ?: return false
        val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
        val chainAnchor = defeatedTarget.position()
        val inheritedTargetSource = attemptTracker.current?.targetSource
        val selection = selectNearestReachableSpearKillChainTarget(
            candidates = findSpearKillChainCandidates(defeatedTarget, chainAnchor),
            distanceSquared = { candidate -> chainAnchor.distanceToSqr(candidate.position()) },
            createRoute = { candidate ->
                createPacketChainPlan(candidate, routeOrigin, sessionOrigin, settings)
            },
        ) ?: return false
        val plan = selection.route
        val aStarPlan = plan.aStarPlan
        if (!installPacketChainPlan(plan)) return false

        packetAStarAttackActive = aStarPlan != null
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
            terminalAuthorizationRequired = aStarPlan != null,
            targetSourceOverride = inheritedTargetSource,
        )
        synchronizeSpearKillServerSneak()
        return true
    }

    private fun installPacketChainPlan(plan: PacketChainPlan): Boolean {
        val aStarPlan = plan.aStarPlan
        val terminalSuffixSteps = aStarPlan?.terminalSuffixCount
            ?: plan.terminalBurstSteps.coerceAtLeast(1)
        val install: (List<Vec3>, Int, Int, Int, Int, Boolean) -> Boolean =
            if (packetBootSession.canStartChainedOutbound) {
                packetBootSession::startChainedOutbound
            } else {
                packetBootSession::replaceRemainingOutbound
            }
        return install(
            plan.outboundMovements,
            SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
            aStarPlan?.preStrikeHoldTicks ?: 0,
            terminalSuffixSteps,
            plan.terminalBurstSteps,
            aStarPlan != null,
        )
    }

    private fun createPacketChainPlan(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
        settings: SpearKillPacketSessionSettings,
    ): PacketChainPlan? {
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return null
        val damageUseDuration = kineticWeapon.computeDamageUseDuration()
        val directRoute = createDirectPacketRouteForMovedTarget(target, routeOrigin, sessionOrigin)
        return when {
            directRoute != null -> createDirectPacketChainPlan(directRoute, settings, damageUseDuration)
            packetRoutingMode == SpearKillRoutingMode.A_STAR -> createAStarPacketChainPlan(
                target = target,
                routeOrigin = routeOrigin,
                sessionOrigin = sessionOrigin,
                settings = settings,
                damageUseDuration = damageUseDuration,
            )
            else -> null
        }
    }

    private fun createDirectPacketChainPlan(
        route: SpearKillAStarPacketRoute,
        settings: SpearKillPacketSessionSettings,
        damageUseDuration: Int,
    ): PacketChainPlan? {
        val hitTicks = spearKillDirectPacketHitTicks(route.outboundTickCount, settings.stepWaitTicks)
        return PacketChainPlan(
            outboundMovements = route.outboundMovements,
            routeMode = if (packetRoutingMode == SpearKillRoutingMode.A_STAR) "AStar→Direct" else "Direct",
            hitTicks = hitTicks,
            terminalBurstSteps = route.terminalBurstSteps,
        ).takeIf {
            hasSpearKillScheduleDamageWindow(
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = damageUseDuration,
                hitTick = hitTicks,
            )
        }
    }

    private fun createAStarPacketChainPlan(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
        settings: SpearKillPacketSessionSettings,
        damageUseDuration: Int,
    ): PacketChainPlan? {
        val aStarPlan = createAStarAttackPlan(
            target = target,
            routeOrigin = routeOrigin,
            sessionOrigin = sessionOrigin,
            stepWaitTicks = settings.stepWaitTicks,
        ) ?: return null
        if (!isServerAcceptedSpearKillRoute(sessionOrigin, routeOrigin, aStarPlan.packetRoute) ||
            !hasSpearKillScheduleDamageWindow(
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = damageUseDuration,
                hitTick = aStarPlan.schedule.hitTick,
            )
        ) {
            return null
        }
        return PacketChainPlan(
            outboundMovements = aStarPlan.packetRoute.outboundMovements,
            routeMode = "AStar",
            hitTicks = aStarPlan.schedule.hitTick,
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

        if (packetAStarAttackActive && packetBootSession.awaitingTerminalCommitAuthorization) {
            commitOrReplanAStarTerminal(target)
            return
        }

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
            replanLockedAStarTarget(target, routeOrigin, sessionOrigin)
        } else {
            replanLockedDirectPacketTarget(target, routeOrigin, sessionOrigin)
        }
    }

    private fun commitOrReplanAStarTerminal(target: LivingEntity) {
        if (!packetBootSession.terminalAimLockComplete ||
            plannedPacket != null ||
            awaitingVanillaMovementPacket
        ) {
            return
        }
        if (hasSafeLiveAStarTerminalCommit(target)) {
            if (!packetBootSession.authorizeTerminalCommit()) {
                terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            } else {
                attemptTracker.authorizeTerminal(player.tickCount)
            }
            return
        }

        val sessionOrigin = packetSessionOrigin
        if (sessionOrigin == null ||
            !replanLockedAStarTarget(
                target = target,
                routeOrigin = sessionOrigin.add(packetBootSession.committedOffset),
                sessionOrigin = sessionOrigin,
            )
        ) {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
        }
    }

    private fun hasSafeLiveAStarTerminalCommit(target: LivingEntity): Boolean {
        val approach = plannedAStarApproach ?: return false
        val settings = packetSessionSettings ?: return false
        val terminalSteps = packetBootSession.terminalSuffixSteps
        val remainingSchedule = buildSpearKillPathSchedule(
            outboundStepCount = terminalSteps,
            stepWaitTicks = settings.stepWaitTicks,
            terminalSuffixCount = terminalSteps,
            preStrikeHoldTicks = 0,
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        ) ?: return false
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return false
        val prediction = predictAStarTarget(
            target = target,
            extrapolation = PositionExtrapolation.getBestForEntity(target),
            ticks = remainingSchedule.hitTick,
        )
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

    private fun replanLockedAStarTarget(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ): Boolean {
        val settings = packetSessionSettings ?: return false
        val plan = createAStarAttackPlan(
            target = target,
            routeOrigin = routeOrigin,
            sessionOrigin = sessionOrigin,
            stepWaitTicks = settings.stepWaitTicks,
        )
        val damageUseDuration = player.useItem.get(DataComponents.KINETIC_WEAPON)?.computeDamageUseDuration()
        if (plan == null || damageUseDuration == null ||
            !hasSpearKillScheduleDamageWindow(player.ticksUsingItem, damageUseDuration, plan.schedule.hitTick)
        ) {
            // Transient prediction misses should not destroy an already safe route.
            aStarPlanTick = player.tickCount
            return false
        }
        if (!isServerAcceptedSpearKillRoute(sessionOrigin, routeOrigin, plan.packetRoute)) return false
        if (!packetBootSession.replaceRemainingOutbound(
                outboundMovements = plan.packetRoute.outboundMovements,
                strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
                preStrikeHoldTicks = plan.preStrikeHoldTicks,
                terminalSuffixSteps = plan.terminalSuffixCount,
                requireTerminalAuthorization = true,
            )
        ) {
            return false
        }
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
        return true
    }

    private fun replanLockedDirectPacketTarget(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ) {
        when (installReplannedDirectPacketRoute(target, routeOrigin, sessionOrigin)) {
            PacketRouteReplanResult.INSTALLED,
            PacketRouteReplanResult.TRANSIENT_FAILURE,
            -> Unit
            PacketRouteReplanResult.BLOCKED -> terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
        }
    }

    private fun installReplannedDirectPacketRoute(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ): PacketRouteReplanResult {
        val route = createDirectPacketRouteForMovedTarget(target, routeOrigin, sessionOrigin)
            ?: return PacketRouteReplanResult.BLOCKED
        val hitTicks = spearKillDirectPacketHitTicks(route.outboundTickCount, activePacketStepWaitTicks)
        val damageUseDuration = player.useItem.get(DataComponents.KINETIC_WEAPON)?.computeDamageUseDuration()
            ?: return PacketRouteReplanResult.TRANSIENT_FAILURE
        if (!hasSpearKillScheduleDamageWindow(player.ticksUsingItem, damageUseDuration, hitTicks) ||
            !packetBootSession.replaceRemainingOutbound(
                route.outboundMovements,
                strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
                terminalSuffixSteps = route.terminalBurstSteps.coerceAtLeast(1),
                terminalBurstSteps = route.terminalBurstSteps,
            )
        ) {
            return PacketRouteReplanResult.TRANSIENT_FAILURE
        }
        plannedAStarTargetPosition = target.position()
        plannedAStarTargetVelocity = target.position().subtract(target.lastPos)
        aStarPlanTick = player.tickCount
        refreshReplannedPacketAttempt(
            target = target,
            outboundSteps = route.outboundMovements.size,
            hitTicks = hitTicks,
            terminalAuthorizationRequired = false,
        )
        return PacketRouteReplanResult.INSTALLED
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
            rejectedTargets += target
        } else if (target != null) {
            rejectedTargets -= target
        }
        if (termination == PacketFollowTermination.DEFEATED && target != null && tryStartPacketChain(target)) {
            return
        }
        plannedAStarApproach = null
        plannedAStarTargetPosition = null
        plannedAStarTargetVelocity = Vec3.ZERO
        clearAStarRenderPath()
        packetBootSession.beginExactReturn()
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
    ): SpearKillAStarPacketRoute? {
        val rawDistance = routeOrigin.distanceTo(target.position())
        if (rawDistance !in 3.0..maxTargetDistance.toDouble()) return null

        val settings = packetSessionSettings ?: return null
        return createDirectPacketRoute(
            target = target,
            routeOrigin = routeOrigin,
            travel = calculateSpearKillTravel(rawDistance),
            settings = settings,
            sessionOrigin = sessionOrigin,
        )
    }

    @Suppress("ReturnCount")
    private fun createDirectPacketRoute(
        target: LivingEntity,
        routeOrigin: Vec3,
        travel: Double,
        settings: SpearKillPacketSessionSettings,
        sessionOrigin: Vec3,
    ): SpearKillAStarPacketRoute? {
        if (routeOrigin.distanceTo(target.position()) !in 3.0..maxTargetDistance.toDouble()) return null

        val eyeOffset = player.eyePosition.subtract(player.position())
        val routeEye = routeOrigin.add(eyeOffset)
        if (!hasVisibleSpearKillAttackRay(
                eye = routeEye,
                direction = target.eyePosition.subtract(routeEye),
                targetBox = target.boundingBox,
                range = maxTargetDistance.toDouble(),
            )
        ) {
            return null
        }

        val profile = currentSpeedProfile(settings.transport.stepLimit)
        if (!travel.isFinite() || travel <= 0.0) return null
        val stepCount = buildSpearKillProfiledMovements(Vec3(1.0, 0.0, 0.0), travel, profile).size
        var predictedHitTicks = spearKillDirectPacketHitTicks(stepCount, settings.stepWaitTicks)
        val sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
        val segmentValidator = createServerValidatedSpearKillDirectPacketSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
        repeat(SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT) {
            val route = buildPredictedDirectPacketRoute(
                target = target,
                routeOrigin = routeOrigin,
                routeEye = routeEye,
                profile = profile,
                segmentValidator = segmentValidator,
                predictedHitTicks = predictedHitTicks,
            ) ?: return null
            val actualHitTicks = spearKillDirectPacketHitTicks(
                route.outboundTickCount,
                settings.stepWaitTicks,
            )
            if (actualHitTicks == predictedHitTicks) return route
            predictedHitTicks = actualHitTicks
        }
        return null
    }

    @Suppress("LongParameterList")
    private fun buildPredictedDirectPacketRoute(
        target: LivingEntity,
        routeOrigin: Vec3,
        routeEye: Vec3,
        profile: SpearKillSpeedProfile,
        segmentValidator: SpearKillAStarSegmentValidator,
        predictedHitTicks: Int,
    ): SpearKillAStarPacketRoute? {
        val predictedTargetPosition = PositionExtrapolation.getBestForEntity(target)
            .getPositionInTicks(predictedHitTicks.toDouble())
        val predictionOffset = predictedTargetPosition.subtract(target.position())
        val predictedTargetBox = target.boundingBox.move(predictionOffset)
        val predictedTargetEyePosition = target.eyePosition.add(predictionOffset)
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = routeEye,
            predictedTargetPosition = predictedTargetPosition,
            targetEyeOffset = target.eyePosition.subtract(target.position()),
            fallbackDirection = player.lookAngle,
        )
        val attackRoute = buildSpearKillProfiledDirectAttackRoute(
            origin = routeOrigin,
            targetBox = predictedTargetBox,
            targetEyePosition = predictedTargetEyePosition,
            playerEyeOffset = routeEye.subtract(routeOrigin),
            preferredDirection = direction,
            profile = profile,
            segmentValidator = segmentValidator,
            maxVerticalStep = safeVirtualFallStep,
        ) ?: return null
        if (!hasValidAStarTerminalAttackRay(
                targetBox = predictedTargetBox,
                eyeOffset = routeEye.subtract(routeOrigin),
                approach = attackRoute.approach,
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
        sessionOrigin: Vec3,
        authoritativeOffset: Vec3,
    ): List<Vec3>? {
        packetBootSession.exactRecoveryMovementsFrom(authoritativeOffset)?.let { return it }
        if (!packetAStarAttackActive) return null

        val authoritativePosition = sessionOrigin.add(authoritativeOffset)
        val aStar = movementConfiguration.packet.aStar
        val sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
        val segmentValidator = createFastSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
        val recoveryMovements = SpearKillAStarRoutePlanner(
            allowDiagonal = aStar.diagonal,
            maxCost = aStar.maxCost,
            canTraverse = segmentValidator::isClear,
        ).plan(authoritativePosition, sessionOrigin)?.let { route ->
            val effectiveMaxSpeed = recoveryPlanningStepLimit
            val compactedRoute = compactSpearKillAStarWaypoints(
                origin = authoritativePosition,
                waypoints = route,
                maxSpeed = effectiveMaxSpeed,
                segmentValidator = segmentValidator,
                lineOfSightShortcuts = aStar.lineOfSightShortcuts,
            )
            buildSpearKillAStarOutboundMovements(
                origin = authoritativePosition,
                waypoints = compactedRoute + sessionOrigin,
                maxSpeed = effectiveMaxSpeed,
                segmentValidator = segmentValidator,
                maxVerticalStep = safeVirtualFallStep,
            )
        } ?: return null
        val serverValidator = createServerMovementSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
        return recoveryMovements.takeIf {
            isSpearKillPacketMovementSequenceServerAccepted(authoritativePosition, it, serverValidator)
        }
    }

    private fun startPacketFirstReturnRecovery(
        authoritativePosition: Vec3,
        targetPlayer: Player = player,
        preferredFirstLeg: List<Vec3>? = null,
    ): Boolean {
        var preferredLeg = preferredFirstLeg
        while (true) {
            when (val action = returnRecoveryTracker.nextAction(authoritativePosition)) {
                is SpearKillReturnRecoveryAction.PacketAttempt -> {
                    val movements = createPacketFirstReturnMovements(action, preferredLeg)
                    preferredLeg = null
                    if (movements == null) continue
                    beginPacketFirstReturnAttempt(action, movements)
                    return true
                }
                is SpearKillReturnRecoveryAction.PhysicalReset -> {
                    applyPhysicalReturnFallback(action.position, targetPlayer)
                    return false
                }
            }
        }
    }

    private fun createPacketFirstReturnMovements(
        attempt: SpearKillReturnRecoveryAction.PacketAttempt,
        preferredFirstLeg: List<Vec3>?,
    ): List<Vec3>? {
        val playerBoundingBox = spearKillServerCollisionBoxAt(attempt.destination)
        val segmentValidator = createServerValidatedSpearKillDirectPacketSegmentValidator(
            origin = attempt.destination,
            playerBoundingBox = playerBoundingBox,
        )
        var preferredLeg = preferredFirstLeg
        return buildSpearKillReturnRecoveryMovements(
            authoritativePosition = attempt.authoritativePosition,
            checkpoints = attempt.checkpoints,
        ) { from, to ->
            val candidate = preferredLeg
            preferredLeg = null
            validatedPreferredReturnLeg(from, to, candidate, segmentValidator)
                ?: planPacketReturnLeg(from, to, segmentValidator)
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
    ): List<Vec3>? {
        buildSpearKillAStarOutboundMovements(
            origin = from,
            waypoints = listOf(to),
            maxSpeed = recoveryPlanningStepLimit,
            segmentValidator = segmentValidator,
            maxVerticalStep = safeVirtualFallStep,
        )?.let { return it }

        val aStar = movementConfiguration.packet.aStar
        val route = SpearKillAStarRoutePlanner(
            allowDiagonal = aStar.diagonal,
            maxCost = aStar.maxCost,
            canTraverse = segmentValidator::isClear,
        ).plan(from, to) ?: return null
        val compactedRoute = compactSpearKillAStarWaypoints(
            origin = from,
            waypoints = route,
            maxSpeed = recoveryPlanningStepLimit,
            segmentValidator = segmentValidator,
            lineOfSightShortcuts = aStar.lineOfSightShortcuts,
        )
        return buildSpearKillAStarOutboundMovements(
            origin = from,
            waypoints = compactedRoute + to,
            maxSpeed = recoveryPlanningStepLimit,
            segmentValidator = segmentValidator,
            maxVerticalStep = safeVirtualFallStep,
        )
    }

    private fun beginPacketFirstReturnAttempt(
        attempt: SpearKillReturnRecoveryAction.PacketAttempt,
        movements: List<Vec3>,
    ) {
        clearVirtualMovementState()
        packetSessionOrigin = attempt.destination
        physicalReturnPositioner.clear()
        packetRecoveryStallTicks = 0
        packetSetbackRecoveryAttempted = true
        attemptTracker.markRecovery()
        beginVirtualFallSafety()
        if (movements.isNotEmpty()) {
            packetBootSession.beginPacketExactRecoveryFrom(attempt.authoritativeOffset, movements)
        }
        sendReturnArrivalConfirmations(attempt.authoritativePosition)
        if (movements.isEmpty()) finishPacketFirstReturnAttempt()
        synchronizeSpearKillServerSneak()
    }

    private fun sendReturnArrivalConfirmations(position: Vec3) {
        while (returnRecoveryTracker.consumeArrivalConfirmation(position) != null) {
            network.send(MovePacketType.FULL.generatePacket())
        }
    }

    private fun finishPacketFirstReturnAttempt() {
        packetSessionOrigin = null
        packetSessionSettings = null
        activeMovementTransport = null
        physicalReturnPositioner.clear()
        resetSpearKillSpeedSession()
        requestSpearKillAttemptCompletion()
    }

    private fun applyPhysicalReturnFallback(position: Vec3, targetPlayer: Player) {
        clearAttack("recovery-exhausted")
        targetPlayer.setPos(position)
        targetPlayer.deltaMovement = Vec3.ZERO
        network.send(MovePacketType.FULL.generatePacket())
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
        lockedAStarTarget?.let(rejectedTargets::add)
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
    ): Boolean = isSpearKillPacketRouteServerAccepted(
        origin = routeOrigin,
        route = route,
        segmentValidator = createServerMovementSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin),
        ),
    )

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
            clearAttack("death")
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

        followLockedMotionTarget()
        followLockedPacketTarget()
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
        if (!enabled) {
            manualAttackRequestLatched = false
            movementAssistPreparationActive = false
            synchronizeSpearKillServerSneak()
            return@handler
        }

        updateKillAuraSpearUseRequest()
        updateManualAttackRequestLatch()

        val attackRequested = hasActivationRequest
        if (!attackRequested) {
            movementAssistPreparationActive = false
            synchronizeSpearKillServerSneak()
            if (!packetBootSession.active && !fightBotSpearState.retainsRejectedTarget) {
                rejectedTargets.clear()
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
                    rejectedTargets.clear()
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
        val shouldFindTarget = Preview.enabled || (!attackActive && attackRequested)
        val lockedTarget = lockedAStarTargetCandidate().takeIf { hasActiveAttackPath }
        val selectedTarget = if (lockedTarget == null && shouldFindTarget) findSelectedTarget() else null
        val target = preferLockedSpearKillTarget(lockedTarget, selectedTarget)
        previewTarget = target?.first
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
            val (entity, dist) = requireNotNull(target)
            if (usesPacketMovementMode && player.isPassenger) {
                if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
                return@handler
            }
            // Packet routing owns its full corridor preflight. Automatic sources may skip one
            // rejected candidate; no movement is emitted before the selected route fully validates.
            if (!usesPacketMovementMode && !isDirectSpearKillTargetEligible(entity, dist)) {
                rejectedTargets += entity
                recordRejectedSpearKillAttempt(entity, "Direct")
                if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
                return@handler
            }
            val activeLockedTarget = lockedAStarTarget
            if ((activeLockedTarget != null && activeLockedTarget !== entity) || entity in rejectedTargets) {
                return@handler
            }
            lockedAStarTarget = entity
            when (createAttackMovement(entity, dist)) {
                SpearKillAttackStartResult.STARTED -> {
                    manualAttackRequestLatched = false
                    movementAssistPreparationActive = false
                    if (fightBotSpearTarget === entity) {
                        fightBotSpearState = SpearKillFightBotState.RouteActive
                    }
                    synchronizeSpearKillServerSneak()
                }
                SpearKillAttackStartResult.RETRY_LATER -> refreshSpearKillServerUse()
                SpearKillAttackStartResult.BLOCKED -> {
                    movementAssistPreparationActive = false
                    terminatePacketFollow(entity, PacketFollowTermination.BLOCKED)
                    recordRejectedSpearKillAttempt(entity, packetRoutingMode.tag)
                    clearAStarTargetLock()
                    if (fightBotSpearTarget === entity) rejectFightBotSpearUse(entity)
                }
                SpearKillAttackStartResult.REJECTED -> if (usesPacketMovementMode) {
                    movementAssistPreparationActive = false
                    rejectedTargets += entity
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
        if (!packetBootSession.active || event.isCancelled || plannedPacket != null || setbackRollback.confirming) {
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
        if (terminalBurstValidation != PendingPacketStepValidation.CLEAR) {
            rejectPendingSpearKillPacketStep(terminalBurstValidation)
            return@handler
        }
        if (!deliverSpearKillTerminalBurstPrefix()) return@handler
        val movement = packetBootSession.pendingMovement
        if (movement != null && packetBootSession.pendingOutboundStep) {
            previewSpearKillOutboundStep()
        }
        if (movement != null && shouldStabilizeVirtualFall(movement)) {
            packetBootSession.confirmStep(delivered = false)
            awaitingVanillaMovementPacket = false
            sendVirtualFallGroundingPacket()
            return@handler
        }
        val pendingOffset = packetBootSession.virtualOffset
        val position = packetPositionOrigin().add(pendingOffset)
        event.x = position.x
        event.y = position.y
        event.z = position.z
        event.ground = isSpearKillGrounded(event.ground, pendingOffset)
        awaitingVanillaMovementPacket = true
    }

    /** Sends every non-final dive segment now; the normal movement event carries the final one. */
    private fun deliverSpearKillTerminalBurstPrefix(): Boolean {
        while (packetBootSession.pendingTerminalBurstMovement != null &&
            !packetBootSession.pendingLogicalOutboundCompletion
        ) {
            val movement = packetBootSession.pendingMovement ?: return false
            if (shouldStabilizeVirtualFall(movement) && !sendVirtualFallGroundingPacket()) {
                packetBootSession.confirmStep(delivered = false)
                return false
            }

            val expectedOffset = packetBootSession.virtualOffset
            awaitingVanillaMovementPacket = true
            sendFallbackMovementPacket()
            if (packetBootSession.committedOffset.distanceToSqr(expectedOffset) >
                SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED
            ) {
                return false
            }
            if (!sendVirtualFallGroundingPacket()) return false
            if (packetBootSession.prepareNextStep() == null) return false
        }
        return true
    }

    private fun shouldStabilizeVirtualFall(movement: Vec3): Boolean =
        shouldStabilizeSpearKillVirtualFall(
            groundingDelivered = virtualFallGroundingDelivered,
            physicalFallDanger = shouldProtectFallDamage,
            state = virtualFallState,
            nextMovement = movement,
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        )

    private fun sendVirtualFallGroundingPacket(): Boolean {
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
        virtualFallGroundingDelivered = false
        virtualFallGroundingPackets += packet
        network.send(packet)
        virtualFallGroundingPackets.remove(packet)
        return virtualFallGroundingDelivered
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
            isSpearKillGrounded(player.onGround(), packetBootSession.virtualOffset),
            player.horizontalCollision,
        ))
    }

    /**
     * Revalidates the one pending virtual movement against the live world immediately before it
     * reaches the packet pipeline. Planning validates the same edge up front, but a chunk or
     * collision can change while the session is in progress.
     */
    private fun validatePendingSpearKillPacketStep(): PendingPacketStepValidation {
        if (!packetBootSession.requiresDelivery) return PendingPacketStepValidation.BLOCKED

        val sessionOrigin = packetSessionOrigin ?: return PendingPacketStepValidation.BLOCKED
        val movement = packetBootSession.pendingMovement ?: return PendingPacketStepValidation.BLOCKED
        val outboundStepLimit = if (packetBootSession.pendingOutboundStep) {
            previewSpearKillOutboundStep().stepLimit
        } else {
            activeMovementTransport?.stepLimit ?: SPEAR_KILL_EXPERIMENTAL_MAX_SPEED.toDouble()
        }
        if (packetBootSession.pendingOutboundStep &&
            movement.length() > outboundStepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON
        ) {
            return PendingPacketStepValidation.BUDGET_EXCEEDED
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
            PendingPacketStepValidation.CLEAR
        } else {
            PendingPacketStepValidation.BLOCKED
        }
    }

    /** Revalidates the complete same-tick displacement, not merely each fall-safe wire segment. */
    private fun validatePendingSpearKillTerminalBurst(): PendingPacketStepValidation {
        val movement = packetBootSession.pendingTerminalBurstMovement
            ?: return PendingPacketStepValidation.CLEAR
        val sessionOrigin = packetSessionOrigin ?: return PendingPacketStepValidation.BLOCKED
        val outboundStepLimit = previewSpearKillOutboundStep().stepLimit
        if (movement.length() > outboundStepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON) {
            return PendingPacketStepValidation.BUDGET_EXCEEDED
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
            PendingPacketStepValidation.CLEAR
        } else {
            PendingPacketStepValidation.BLOCKED
        }
    }

    private fun rejectPendingSpearKillPacketStep(validation: PendingPacketStepValidation) {
        val outboundStep = packetBootSession.pendingOutboundStep
        packetBootSession.confirmStep(delivered = false)
        plannedPacket = null
        awaitingVanillaMovementPacket = false
        if (validation == PendingPacketStepValidation.BUDGET_EXCEEDED && outboundStep) {
            replanPacketRouteForCurrentBudget()
        } else if (validation == PendingPacketStepValidation.BLOCKED && outboundStep) {
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
            packetBootSession.beginExactReturn()
            applyConfirmedPhysicalReturnPosition()
            return
        }
        val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
        val replanned = if (packetAStarAttackActive) {
            replanLockedAStarTarget(target, routeOrigin, sessionOrigin)
        } else {
            installReplannedDirectPacketRoute(target, routeOrigin, sessionOrigin) ==
                PacketRouteReplanResult.INSTALLED
        }
        if (!replanned) {
            packetBootSession.beginExactReturn()
            applyConfirmedPhysicalReturnPosition()
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

        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        if (setbackRollback.confirming) {
            packet.onGround = true
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
            PendingPacketStepValidation.CLEAR
        }
        if (carriesPendingStep &&
            (event.isCancelled || pendingValidation != PendingPacketStepValidation.CLEAR)
        ) {
            if (!event.isCancelled) {
                event.cancelEvent()
            }
            rejectPendingSpearKillPacketStep(
                if (event.isCancelled) PendingPacketStepValidation.CLEAR else pendingValidation,
            )
            return@handler
        }

        if (shouldSuppressSpearKillStrikeHoldPacket(packetBootSession.holdingStrike)) {
            event.cancelEvent()
            return@handler
        }

        if (carriesPendingStep) {
            plannedPacket = packet
        }
        applySpearKillVirtualPosition(
            packet = packet,
            playerPosition = packetPositionOrigin(),
            virtualOffset = spearKillPacketVirtualOffset(
                carriesPendingStep = packet === plannedPacket,
                committedOffset = packetBootSession.committedOffset,
                pendingOffset = packetBootSession.virtualOffset,
            ),
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

        if (packet in virtualFallGroundingPackets) {
            fallDamageDeliveryTracker.protect(packet)
            return@handler
        }
        if (!hasActiveAttackPath || !shouldProtectFallDamage) return@handler
        if (packetBootSession.active && (packet !== plannedPacket || packetBootSession.virtualOffset.y != 0.0)) {
            return@handler
        }

        fallDamageDeliveryTracker.protect(packet)
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
        val virtualPacket = virtualSessionPackets.remove(packet)
        val plannedPathPacket = packet === plannedPacket
        val pathPacket = virtualFallGroundingPacket || virtualPacket || plannedPathPacket

        val queuedByBlink = pathPacket && BlinkManager.packetQueue.any { it.packet === packet }
        if (queuedByBlink) {
            BlinkManager.packetQueue.removeIf { it.packet === packet }
        }

        val delivered = !event.isCancelled && !queuedByBlink
        if (pathPacket && delivered && packet.hasPosition()) ownedMovementPacketsThisTick++
        if (fallDamageDeliveryTracker.confirmFinalState(packet, cancelled = !delivered)) {
            player.resetFallDistance()
        }

        if (virtualFallGroundingPacket && delivered) {
            virtualFallState.confirmGrounded()
            virtualFallGroundingDelivered = true
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
            deliveredMovement?.let(virtualFallState::confirmMovement)
            virtualFallGroundingDelivered = false
            sendReturnArrivalConfirmations(packetPositionOrigin().add(packetBootSession.committedOffset))
        }
        applyConfirmedPhysicalReturnPosition()
        if (!packetBootSession.active) {
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
            rejectedRouteTarget?.let(rejectedTargets::add)
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
            clearAttack("setback-unrecoverable")
            rejectedRouteTarget?.let(rejectedTargets::add)
            return
        }

        val recoverySettings = packetSessionSettings
        clearVirtualMovementState()
        physicalReturnPositioner.clear()
        packetRecoveryStallTicks = 0
        rejectedRouteTarget?.let(rejectedTargets::add)
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
        startPacketFirstReturnRecovery(
            authoritativePosition = setback.sessionOrigin.add(setback.authoritativeOffset),
            targetPlayer = player,
            preferredFirstLeg = setback.exactRecoveryMovements,
        )
        synchronizeSpearKillServerSneak()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        failureNotificationGate.clear()
        clearAttack("world-change")
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        // The connection is already closing; clear our local ownership without enqueueing a packet.
        serverSneaking = false
        failureNotificationGate.clear()
        clearAttack("disconnect")
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
        get() = super.running || packetBootSession.active || setbackGuard.armed || setbackRollback.confirming

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacySpearKillConfig(jsonObject)
    }

    override fun onDisabled() {
        failureNotificationGate.clear()
        clearAttack("disabled")
        super.onDisabled()
    }
}

/** Extra distance around an entity's vanilla/Hitbox pick box that still counts as a crosshair selection. */
private const val SPEAR_KILL_TARGET_SELECTION_MARGIN = 0.75
private const val KILL_AURA_INHERITED_TARGET_SOURCE = "KillAura"
private const val SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED = 1e-9
private const val SPEAR_KILL_MIN_ATTACK_RAY_RANGE = 2.0
private const val SPEAR_KILL_ATTACK_RAY_RANGE = 4.5
private const val SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS = 250L
private const val SPEAR_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS = 200
private const val SPEAR_KILL_MAX_RECOVERY_STALL_TICKS = 40
private const val SPEAR_KILL_RECOVERY_STEP_EPSILON = 1.0E-6
private const val SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED = 1.0E-6
private const val SPEAR_KILL_HITBOX_RAYCAST_MAX_SPAN_LENGTH = 4.0
private const val SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT = 4

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
