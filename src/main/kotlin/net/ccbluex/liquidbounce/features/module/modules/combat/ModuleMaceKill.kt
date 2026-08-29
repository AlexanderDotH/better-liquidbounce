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

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
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
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.combat.attackEntityWithResult
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.entity.warp
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Performs the established instant-mace fall spoof locally and delegates remote travel to the
 * shared remote-kill route engine.
 */
@Suppress("TooManyFunctions", "LargeClass")
object ModuleMaceKill : ClientModule("MaceKill", ModuleCategories.COMBAT, disableOnQuit = true) {

    // Keep this first: existing configurations and the public tag rely on the historical setting.
    private val fallHeight by int("FallHeight", 22, 1..170).apply { tagBy(this) }
    private val maxTargetDistance by float(
        "TargetDistance",
        500f,
        3f..500f,
        aliases = listOf("MaxTargetDistance"),
    )
    private val activationMode by enumChoice("Activation", DEFAULT_MACE_KILL_ACTIVATION_MODE)
    private val targetSource by enumChoice("TargetSource", DEFAULT_MACE_KILL_TARGET_SOURCE)
    private val movementConfiguration = MaceKillMovementConfiguration(this)
    private val movement = tree(movementConfiguration.choice)

    private object Preview : ToggleableValueGroup(this, "Preview", true) {
        val renderPath by boolean("RenderPath", false)
        val mode = choices("Mode", 0) { arrayOf<Mode>(Glow) }

        object Glow : Mode("Glow") {
            override val parent: ModeValueGroup<Mode>
                get() = mode

            val glowColor by color("GlowColor", Color4b.RED)
            val glowStyle = EspGlowStyleConfig(this)
        }
    }

    private var previewTarget: LivingEntity? = null
    private var activeRouteTarget: LivingEntity? = null
    private var activeRouteOwner = MaceKillRouteOwner.NONE
    private var remoteStrikeEndpoint: Vec3? = null
    private var remoteStrikeTarget: LivingEntity? = null
    private var remoteStrikeFallResetPlan: MacePostAttackFallResetPlan? = null
    private var remoteStrikeEarliestTick = 0
    private var fightBotMaceTarget: LivingEntity? = null
    private var fightBotMaceState = MaceKillFightBotState.Unavailable
    private var fightBotMaceSource: FightBotMaceUseSource? = null
    private var pendingFightBotTerminal: MaceKillFightBotTerminal? = null
    private val routeSession = SpearKillPacketBootSession()
    private val speedController = SpearKillSpeedController()
    private val fallSafetyLifecycle = SpearKillFallSafetyLifecycle()
    private val groundingPacketTracker = MaceKillGroundingPacketTracker()
    private val routeEngine = RemoteKillRouteEngine(
        routeSession,
        RemoteKillWeaponAdapter(::commitRemoteStrike),
        movementOwner = "MaceKill",
        retainMovementAfterCompletion = true,
    )
    private val routeAdmissionBackoff = MaceKillRouteAdmissionBackoff(MACE_KILL_ROUTE_ADMISSION_BACKOFF_TICKS)
    private val instantRouteBackoff = MaceKillRouteAdmissionBackoff(MACE_KILL_INSTANT_FAILURE_BACKOFF_TICKS)
    private val rejectedTargets = SpearKillTargetRejectionTracker<LivingEntity>(
        MACE_KILL_REJECTED_TARGET_RETRY_TICKS,
    )
    private val returnConfirmation = MaceKillReturnConfirmationWindow(MACE_KILL_RETURN_CONFIRMATION_TICKS)
    private val primingPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    private val researchPacketContexts = IdentityHashMap<ServerboundMovePlayerPacket, MaceKillResearchPacketContext>()
    private var plannedRoutePacket: ServerboundMovePlayerPacket? = null
    private var routeOrigin: Vec3? = null
    private var routeOriginBoundingBox: AABB? = null
    private var routeRenderPath = emptyList<Vec3>()
    private var routeStepWaitTicks = 0
    private var routeStallTicks = 0
    private var routeRejected = false
    private var routeResumeTick = 0
    private var localPacketRouteOrigin: Vec3? = null
    private var primingDeliveryFailed = false
    private var applyingStrikePackets = false
    private var motionRouteActive = false
    private var activeVanillaVClipSegments = emptySet<MaceKillVanillaVClipSegment>()
    private var activeClipReachSession: MaceClipReachSession? = null
    private var instantRecoveryPlan: MaceClipReachPlan? = null
    private var instantCorrectionRecoveryActive = false
    private var instantTerminalHandled = false
    private var lastInstantPlanBlockReason: MaceClipReachBlockReason? = null
    private var plannedTargetPosition: Vec3? = null
    private var routeChainCount = 0
    private var activeRouteConfiguration: MaceKillRouteExecutionConfiguration? = null
    private var routeDeadlineTick = 0
    private var holdAttackState = MaceKillHoldAttackState.IDLE
    private var evidenceTargetId: Int? = null
    private var evidenceDeadlineTick = 0
    private var correctionState: MaceKillLocalCorrectionState? = null
    private var correctionRecoveryAttempts = 0
    private var instantServerRejected = false
    private var researchExecution: MaceKillResearchExecution? = null
    private val researchRuntime by lazy {
        MaceClipResearchRuntime(
            ConfigSystem.rootFolder.toPath().resolve("maceclip-research"),
        )
    }
    private val researchControl = MaceClipResearchGuardedControl(
        hasActiveProbe = { researchRuntime.status() is MaceClipResearchStatus.Active },
        hasActiveRemoteKillSession = { routeEngine.ownsMovement },
        hasUnsafeMovementContext = ::hasUnsafeResearchMovementContext,
        startExecution = ::startResearchProbe,
        statusProvider = { researchRuntime.status() },
        abortExecution = ::abortResearchProbe,
    )
    private val setbackListener = object : RemoteKillSetbackListener {
        override fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
            prepareRemoteCorrection(player)
        }

        override fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
            finishRemoteCorrection(player)
        }
    }

    private object FightBotMaceUseRequester

    private val debugConsole = lazy(LazyThreadSafetyMode.NONE) {
        MaceKillDebugConsole(
            enabled = { ModuleDebug.running },
            sink = { message -> logger.info(message) },
        )
    }
    private val failureNotificationGate = SpearKillFailureNotificationGate(
        MACE_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS,
    )

    init {
        tree(Preview)
        TargetGlowSourceRegistry.register(::currentPreviewGlow)
        MaceKillAttackHook.install(::handleAcceptedAttack)
        RemoteKillSetbackRegistry.register(setbackListener)
        MaceClipResearchControlRegistry.install(researchControl)
    }

    internal val maximumTargetRange: Float
        get() = maceKillMaximumTargetRange(
            configuredTargetRange = maxTargetDistance.toDouble(),
            instantRouting = isInstantPacketRoutingConfigured(),
            instantMovementAllowance = movementConfiguration.packet.instant.clearanceHeight.toDouble(),
        ).toFloat()

    internal val ownsKillAuraRoute: Boolean
        get() = activeRouteOwner == MaceKillRouteOwner.KILL_AURA && activeRouteTarget != null

    /** True only while MaceKill still owns packets, not its post-return correction observation window. */
    internal val suppressesNoFallPackets: Boolean
        get() = routeEngine.ownsMovement && (
            routeSession.active || routeEngine.awaitingStrike || plannedRoutePacket != null ||
                primingPackets.isNotEmpty() || groundingPacketTracker.pendingCount > 0
            )

    private val acceptsKillAuraDelegation: Boolean
        get() = GlobalSettingsCombat.delegateKillAuraAttacks && ModuleKillAura.running

    internal val isKillAuraIntegrationAvailable: Boolean
        get() {
            val admissionFailure = evaluateMaceKillRouteAdmission(currentKillAuraAdmissionContext())
            val available = acceptsKillAuraDelegation && fightBotMaceTarget == null && admissionFailure == null
            if (available) {
                if (debugConsole.isInitialized()) debugConsole.value.clearTransition("kill-aura-admission")
            } else {
                debugMaceKillChanged(
                    channel = "kill-aura-admission",
                    event = "kill-aura-unavailable",
                    fingerprint = {
                        listOf(
                            acceptsKillAuraDelegation,
                            fightBotMaceTarget?.id,
                            admissionFailure,
                            RemoteKillMovementOwnership.currentOwner,
                        )
                    },
                ) {
                    listOf(
                        "delegation" to acceptsKillAuraDelegation,
                        "fightbot-target" to fightBotMaceTarget?.id,
                        "admission" to admissionFailure,
                        "movement-owner" to RemoteKillMovementOwnership.currentOwner,
                    )
                }
            }
            return available
        }

    private fun currentKillAuraAdmissionContext() = MaceKillRouteAdmissionContext(
        enabled = enabled && running,
        routeOwned = activeRouteTarget != null || routeEngine.ownsMovement,
        conflictingMovementOwned = RemoteKillMovementOwnership.active && !routeEngine.ownsMovement,
        blinkRunning = ModuleBlink.running,
        passenger = player.isPassenger,
        gliding = player.isFallFlying,
        backoffActive = routeAdmissionBackoff.isBlocked(player.tickCount) ||
            instantRouteBackoff.isBlocked(player.tickCount) ||
            shouldBlockMaceKillRouteAfterInstantCorrection(
                instantRouting = isInstantPacketRoutingConfigured(),
                instantServerRejected = instantServerRejected,
            ),
        holdingMace = hasServerHeldMace(),
    )

    internal val isKillAuraIntegrationArmed: Boolean
        get() = isKillAuraIntegrationAvailable && isAttackCooldownReady()

    internal val fightBotRouteTarget: LivingEntity?
        get() = fightBotMaceTarget.takeIf {
            fightBotMaceState == MaceKillFightBotState.RouteActive && activeRouteTarget === it
        }

    internal fun fightBotStateFor(target: LivingEntity): MaceKillFightBotState =
        fightBotMaceState.takeIf { fightBotMaceTarget === target } ?: MaceKillFightBotState.Unavailable

    internal fun reservesFightBotMaceUse(target: LivingEntity?): Boolean = target != null &&
        fightBotMaceTarget === target && fightBotMaceState.reservesKillAuraSubsystems

    @Suppress("CognitiveComplexMethod", "ReturnCount") // Ordered ownership guards must fail closed.
    internal fun requestFightBotMaceUse(target: LivingEntity): MaceKillFightBotState {
        if (!canPrepareFightBotMaceUse(target)) {
            if (fightBotMaceTarget === target) beginFightBotTerminal(MaceKillFightBotTerminal.TargetLoss)
            return MaceKillFightBotState.Unavailable
        }
        if (fightBotMaceTarget !== target) {
            if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
                beginFightBotTerminal(MaceKillFightBotTerminal.TargetLoss)
                return fightBotMaceState
            }
            clearFightBotMaceUse(MaceKillFightBotTerminal.TargetLoss)
        }

        fightBotMaceTarget = target
        val existingHotbar = fightBotMaceSource as? FightBotMaceUseSource.Hotbar
        if (existingHotbar != null) {
            if (!isMaceInHotbarSlot(existingHotbar.slot) ||
                !SilentHotbar.selectSlotSilently(FightBotMaceUseRequester, existingHotbar.slot, 2)
            ) {
                return rejectFightBotMaceUse(target)
            }
            fightBotMaceState = if (activeRouteTarget === target) {
                MaceKillFightBotState.RouteActive
            } else {
                MaceKillFightBotState.Ready
            }
            return fightBotMaceState
        }
        val source = resolveFightBotMaceUseSource() ?: return rejectFightBotMaceUse(target)
        if (source is FightBotMaceUseSource.Hotbar &&
            !SilentHotbar.selectSlotSilently(FightBotMaceUseRequester, source.slot, 2)
        ) {
            return rejectFightBotMaceUse(target)
        }
        fightBotMaceSource = source
        fightBotMaceState = if (activeRouteTarget === target) {
            MaceKillFightBotState.RouteActive
        } else {
            MaceKillFightBotState.Ready
        }
        return fightBotMaceState
    }

    internal fun releaseFightBotMaceUse(
        terminal: MaceKillFightBotTerminal = MaceKillFightBotTerminal.TargetLoss,
    ) {
        beginFightBotTerminal(terminal)
    }

    internal fun canAcceptKillAuraTarget(target: LivingEntity): Boolean =
        isKillAuraIntegrationAvailable && isMaceKillTargetEligible(target)

    internal fun shouldExcludeKillAuraTarget(target: LivingEntity): Boolean = shouldExcludeMaceKillWaterTarget(
        maceKillEnabled = enabled,
        mainHandMace = player.mainHandItem.item == Items.MACE,
        targetInWater = target.isInWater || target.isSwimming || target.isUnderWater,
    )

    /** KillAura explicitly transfers one selected target; no attack-key state participates. */
    internal fun requestKillAuraMaceKill(target: LivingEntity): Boolean {
        if (ownsKillAuraRoute) return activeRouteTarget === target
        if (!isKillAuraIntegrationArmed || ModuleKillAura.targetForMaceKill() !== target) return false
        return startRemoteRoute(target, MaceKillRouteOwner.KILL_AURA)
    }

    internal fun onKillAuraDisabled() {
        if (activeRouteOwner == MaceKillRouteOwner.KILL_AURA) abortRemoteRoute()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (player.isDeadOrDying) {
            clearRuntime(MaceKillFightBotTerminal.Death)
            return@handler
        }
        rejectedTargets.clearExpired(player.tickCount)
        updateResearchEvidence()
        if (researchExecution != null) {
            if (routeEngine.ownsMovement) {
                maintainPacketRouteOrigin()
                if (routeSession.active || routeEngine.awaitingStrike) {
                    tickActiveRemoteRoute()
                } else {
                    finishCompletedRouteSession()
                }
            } else {
                finishInactiveRouteOwnership()
            }
            return@handler
        }
        if (routeEngine.ownsMovement) {
            maintainPacketRouteOrigin()
            if (!routeSession.active && !routeEngine.awaitingStrike) {
                finishCompletedRouteSession()
                return@handler
            }
            if (ModuleBlink.running || player.isPassenger || player.isFallFlying) beginSafeRouteAbort()
            maintainFightBotMaceLease()
            tickActiveRemoteRoute()
            return@handler
        }
        finishInactiveRouteOwnership()
        if (!enabled) return@handler

        val selectedTarget = findSelectedTarget()
        previewTarget = selectedTarget
        val attackHeld = mc.options.keyAttack.isPressedOnAny
        val evidencePending = player.tickCount < evidenceDeadlineTick
        val decision = advanceMaceKillHoldAttack(
            state = holdAttackState,
            attackHeld = attackHeld,
            targetAvailable = selectedTarget != null,
            routeActive = false,
            evidencePending = evidencePending,
            cooldownReady = isAttackCooldownReady(),
        )
        holdAttackState = decision.state

        val target = selectedTarget ?: return@handler
        if (isOrdinaryMeleeAvailable(target)) return@handler
        val owner = routeOwnerFor(target, decision.launch) ?: return@handler
        if (startRemoteRoute(target, owner)) {
            holdAttackState = MaceKillHoldAttackState.ATTEMPTED
        } else if (owner == MaceKillRouteOwner.MANUAL) {
            holdAttackState = armMaceKillHoldAttackRetry(holdAttackState)
        }
    }

    @Suppress("unused")
    private val packetSafetyHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        if (event.origin != TransferOrigin.OUTGOING || !routeEngine.ownsMovement) return@handler
        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        if (applyingStrikePackets) return@handler
        if (groundingPacketTracker.reassertGround(packet)) return@handler
        if (!suppressesNoFallPackets) return@handler
        if (packet === plannedRoutePacket || packet in primingPackets) {
            if (packet.hasPosition()) {
                packet.onGround = maceKillRoutePacketGrounded(
                    position = routePacketPosition(packet),
                    identityOwnedByRoute = true,
                )
            }
            return@handler
        }
        val virtualOffset = maceKillPhysicalMovementVirtualOffset(
            routeOwned = routeEngine.ownsMovement,
            packetMovement = localPacketRouteOrigin != null,
            researchActive = researchExecution != null,
            committedOffset = routeSession.committedOffset,
        )
        if (virtualOffset != null) {
            val origin = routeOrigin ?: run {
                event.cancelEvent()
                return@handler
            }
            val position = origin.add(virtualOffset)
            applySpearKillVirtualPosition(
                packet = packet,
                playerPosition = origin,
                virtualOffset = virtualOffset,
                grounded = maceKillRoutePacketGrounded(position, identityOwnedByRoute = true),
            )
            return@handler
        }
        event.cancelEvent()
    }

    @Suppress("unused")
    private val packetDeliveryHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        if (event.origin == TransferOrigin.INCOMING) {
            val damage = event.packet as? ClientboundDamageEventPacket ?: return@handler
            if (!event.isCancelled && damage.entityId == evidenceTargetId) {
                debugMaceKill("damage-evidence") { listOf("target" to damage.entityId) }
                evidenceDeadlineTick = 0
            }
            researchExecution?.takeIf { it.target?.id == damage.entityId }?.let { execution ->
                researchRuntime.recordDamage(
                    execution.sessionId,
                    execution.target?.health?.toDouble() ?: 0.0,
                    null,
                )
            }
            return@handler
        }

        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        val groundingQueuedByBlink = BlinkManager.packetQueue.any { it.packet === packet }
        when (groundingPacketTracker.resolve(packet, event.isCancelled, groundingQueuedByBlink)) {
            MaceKillGroundingPacketResolution.UNRELATED -> Unit
            MaceKillGroundingPacketResolution.DELIVERED -> {
                if (groundingQueuedByBlink) BlinkManager.packetQueue.removeIf { it.packet === packet }
                if (fallSafetyLifecycle.confirmGrounding(delivered = true)) player.resetFallDistance()
                return@handler
            }
            MaceKillGroundingPacketResolution.REJECTED -> {
                if (groundingQueuedByBlink) BlinkManager.packetQueue.removeIf { it.packet === packet }
                fallSafetyLifecycle.confirmGrounding(delivered = false)
                return@handler
            }
        }
        if (packet in primingPackets) {
            confirmPrimingPacket(packet, event.isCancelled)
            return@handler
        }
        if (packet !== plannedRoutePacket) return@handler

        val queuedByBlink = BlinkManager.packetQueue.any { it.packet === packet }
        if (queuedByBlink) BlinkManager.packetQueue.removeIf { it.packet === packet }
        val delivered = !event.isCancelled && !queuedByBlink
        recordResearchPacketDelivery(packet, delivered, queuedByBlink)
        val confirmedOutbound = delivered && routeSession.pendingOutboundStep
        val pendingMovement = routeSession.pendingMovement
        if (confirmedOutbound) {
            remoteStrikeEarliestTick = maceKillRemoteStrikeEarliestTick(
                confirmedEndpointTick = player.tickCount,
                instantClip = activeClipReachSession != null,
            )
            activeClipReachSession?.recordOutboundMovementConfirmed()
        }
        val strikeResult = routeEngine.confirmStep(delivered)
        if (pendingMovement != null) {
            fallSafetyLifecycle.confirmMovement(pendingMovement, delivered, packet.onGround)
        }
        plannedRoutePacket = null
        if (delivered) {
            routeStallTicks = 0
            if (confirmedOutbound && activeRouteOwner != MaceKillRouteOwner.RESEARCH) {
                activeRouteConfiguration?.let { configuration ->
                    speedController.confirmOutbound(currentMaceKillSpeedLimits(configuration))
                }
            }
            applyMotionRoutePosition()
        } else {
            routeStallTicks++
        }
        handleRemoteStrikeResult(strikeResult)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (shouldRenderSpearKillAStarPath(
                previewEnabled = Preview.enabled,
                packetAStarEnabled = routeRenderPath.isNotEmpty(),
                renderPathEnabled = Preview.renderPath,
                renderPath = routeRenderPath,
            )
        ) {
            renderSpearKillAStarPath(
                event,
                routeRenderPath,
                SpearKillAStarPathAppearance(Preview.Glow.glowColor, Preview.Glow.glowStyle.style),
            )
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        clearRuntime(MaceKillFightBotTerminal.WorldChange)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        clearRuntime(MaceKillFightBotTerminal.Disconnect)
    }

    private fun handleAcceptedAttack(attackPlayer: Player, target: Entity): MaceKillAttackResult {
        val localPlayer = attackPlayer as? LocalPlayer ?: return MaceKillAttackResult.NOT_APPLIED
        if (!enabled || !running || localPlayer !== mc.player || !hasServerHeldMace()) {
            return MaceKillAttackResult.NOT_APPLIED
        }

        val remoteIntent = remoteStrikeTarget === target
        val endpoint = remoteStrikeEndpoint.takeIf { remoteIntent } ?: localPlayer.position()
        if (remoteIntent && !isRemoteEndpointReady(localPlayer, target, endpoint)) {
            return MaceKillAttackResult.REJECTED
        }
        if (remoteIntent && remoteStrikeFallResetPlan == null) return MaceKillAttackResult.REJECTED

        val result = MaceInstantStrikePlanner.plan(
            MaceInstantStrikeRequest(
                physicalPosition = localPlayer.position(),
                physicalBoundingBox = localPlayer.boundingBox,
                virtualEndpoint = endpoint,
                maximumFallHeight = fallHeight,
                endpointOnGround = !remoteIntent && localPlayer.onGround(),
            ),
        ) { box -> world.getBlockCollisions(localPlayer, box).allEmpty() }
        val plan = (result as? MaceInstantStrikePlanResult.Ready)?.plan
            ?: return if (remoteIntent) MaceKillAttackResult.REJECTED else MaceKillAttackResult.NOT_APPLIED

        applyMaceStrikePackets(localPlayer, plan.packets)
        return MaceKillAttackResult.APPLIED
    }

    private fun applyMaceStrikePackets(
        localPlayer: LocalPlayer,
        packets: List<MaceInstantStrikePacket>,
    ) {
        applyingStrikePackets = true
        try {
            packets.forEach { packet ->
                when (packet) {
                    is MaceInstantStrikePacket.StatusOnly -> localPlayer.warp(null, packet.onGround)
                    is MaceInstantStrikePacket.Position -> localPlayer.warp(packet.position, packet.onGround)
                }
            }
        } finally {
            applyingStrikePackets = false
        }
    }

    private fun routeOwnerFor(target: LivingEntity, manualLaunch: Boolean): MaceKillRouteOwner? = when {
        fightBotMaceTarget === target && fightBotMaceState == MaceKillFightBotState.Ready ->
            MaceKillRouteOwner.FIGHT_BOT
        manualLaunch && isMaceKillActivationSatisfied(
            activationMode = activationMode,
            attackHeld = mc.options.keyAttack.isPressedOnAny,
            manualAttackRequested = true,
        ) -> MaceKillRouteOwner.MANUAL
        else -> null
    }

    @Suppress("CognitiveComplexMethod", "LongMethod", "ReturnCount")
    // Route admission and terminal cleanup are intentionally co-located.
    private fun startRemoteRoute(target: LivingEntity, owner: MaceKillRouteOwner): Boolean {
        val admissionFailure = evaluateMaceKillRouteAdmission(
            MaceKillRouteAdmissionContext(
                enabled = enabled,
                routeOwned = routeEngine.ownsMovement,
                conflictingMovementOwned = RemoteKillMovementOwnership.active && !routeEngine.ownsMovement,
                blinkRunning = ModuleBlink.running,
                passenger = player.isPassenger,
                gliding = player.isFallFlying,
                backoffActive = routeAdmissionBackoff.isBlocked(player.tickCount) ||
                    instantRouteBackoff.isBlocked(player.tickCount) ||
                    shouldBlockMaceKillRouteAfterInstantCorrection(
                        instantRouting = isInstantPacketRoutingConfigured(),
                        instantServerRejected = instantServerRejected,
                    ),
                targetValid = isMaceKillTargetEligible(target),
                holdingMace = hasServerHeldMace(),
            ),
        )
        if (admissionFailure != null) {
            debugMaceKill("route-admission-rejected") {
                listOf("owner" to owner, "target" to target.id, "reason" to admissionFailure)
            }
            if (owner == MaceKillRouteOwner.FIGHT_BOT && !routeEngine.ownsMovement) rejectFightBotMaceUse(target)
            return false
        }
        val routeConfiguration = currentMaceKillRouteExecutionConfiguration(owner)
        val origin = player.position()
        val predicted = predictedMaceKillTarget(target, origin, routeConfiguration.timing)
        val endpoint = findMaceKillAttackEndpoint(
            target = target,
            origin = origin,
            targetPosition = predicted.position,
            targetEyePosition = predicted.eyePosition,
        ) ?: run {
            debugMaceKill("endpoint-rejected") {
                listOf("owner" to owner, "target" to target.id, "origin" to origin, "predicted" to predicted.position)
            }
            return rejectRemoteRouteAdmission(target, owner)
        }
        speedController.begin(player.deltaMovement.length(), routeConfiguration.targetSpeed)
        val planned = buildMaceKillRoute(origin, endpoint, routeConfiguration) ?: run {
            debugMaceKillChanged(
                channel = "route-plan-rejection",
                event = "route-plan-rejected",
                fingerprint = { owner to (routeConfiguration.routingMode to lastInstantPlanBlockReason) },
            ) {
                listOf(
                    "owner" to owner,
                    "target" to target.id,
                    "routing" to routeConfiguration.routingMode,
                    "instant-reason" to lastInstantPlanBlockReason,
                )
            }
            speedController.reset()
            lastInstantPlanBlockReason?.let { reason ->
                return rejectInstantPlan(target, owner, reason)
            }
            return rejectRemoteRouteAdmission(target, owner)
        }
        if (debugConsole.isInitialized()) debugConsole.value.clearTransition("route-plan-rejection")
        if (!beginMaceKillFallSafety(planned)) {
            debugMaceKill("fall-safety-rejected") {
                listOf("owner" to owner, "target" to target.id, "steps" to planned.request.outboundMovements.size)
            }
            speedController.reset()
            return rejectRemoteRouteAdmission(target, owner)
        }

        activeRouteTarget = target
        activeRouteOwner = owner
        routeOrigin = origin
        routeOriginBoundingBox = player.boundingBox
        routeRenderPath = planned.renderPath
        routeStepWaitTicks = planned.request.stepWaitTicks
        routeStallTicks = 0
        routeRejected = false
        motionRouteActive = planned.motion
        activeVanillaVClipSegments = planned.vanillaVClipSegments
        activeClipReachSession = planned.clipReachPlan?.let { MaceClipReachSession(it, player.tickCount.toLong()) }
        instantRecoveryPlan = planned.clipReachPlan
        instantTerminalHandled = false
        plannedTargetPosition = predicted.position
        routeChainCount = 0
        activeRouteConfiguration = if (planned.clipReachPlan == null) {
            routeConfiguration
        } else {
            routeConfiguration.copy(
                timing = routeConfiguration.timing.copy(
                    maxPacketsPerTick = maceKillInstantPacketsPerTick(
                        stepDelayTicks = routeConfiguration.timing.stepWaitTicks,
                        packetBudget = movementConfiguration.packet.instant.maxPackets,
                    ),
                ),
                routingMode = MaceKillRoutingMode.INSTANT,
            )
        }
        localPacketRouteOrigin = origin.takeUnless { planned.motion }
        routeDeadlineTick = if (planned.clipReachPlan == null) {
            maceKillRouteDeadlineTick(
                startTick = player.tickCount,
                oneWayTravelTicks = routeConfiguration.timing.travelTicksForSteps(
                    planned.request.outboundMovements.size,
                ),
            )
        } else {
            0
        }
        correctionRecoveryAttempts = 0
        returnConfirmation.clear()
        if (owner == MaceKillRouteOwner.FIGHT_BOT) fightBotMaceState = MaceKillFightBotState.RouteActive
        stopKillAuraBlockingBeforeRoute()

        return runCatching {
            routeEngine.start(target, planned.request)
            if (planned.primingPackets > 0 && !sendMaceKillPrimingPackets(origin, planned.primingPackets)) {
                activeClipReachSession?.recordReplanRejected()
                handleInstantSessionOutcome(MaceClipReachSessionOutcome.REPLAN_REJECTED)
                routeEngine.clear()
                clearRouteOwnership(rejected = true)
                if (owner == MaceKillRouteOwner.FIGHT_BOT) finalizeFightBotRejection()
                return@runCatching false
            }
            routeAdmissionBackoff.clear()
            if (debugConsole.isInitialized()) debugConsole.value.clearTransition("correction-recovery")
            debugMaceKill("route-start") {
                listOf("owner" to owner, "target" to target.id, "steps" to planned.request.outboundMovements.size)
            }
            true
        }.getOrElse { exception ->
            debugMaceKill("route-start-failed") {
                listOf(
                    "owner" to owner,
                    "target" to target.id,
                    "exception" to exception::class.simpleName,
                    "message" to exception.message,
                )
            }
            routeAdmissionBackoff.reject(player.tickCount)
            rejectedTargets.reject(target, player.tickCount)
            routeEngine.clear()
            clearRouteOwnership(rejected = true)
            if (owner == MaceKillRouteOwner.FIGHT_BOT) finalizeFightBotRejection()
            false
        }
    }

    private fun rejectRemoteRouteAdmission(target: LivingEntity, owner: MaceKillRouteOwner): Boolean {
        routeAdmissionBackoff.reject(player.tickCount)
        rejectedTargets.reject(target, player.tickCount)
        if (owner == MaceKillRouteOwner.FIGHT_BOT) {
            rejectFightBotMaceUse(target)
        } else {
            notifyMaceFailure("routeRejected")
        }
        return false
    }

    private fun rejectInstantPlan(
        target: LivingEntity,
        owner: MaceKillRouteOwner,
        reason: MaceClipReachBlockReason,
    ): Boolean {
        val decision = maceKillInstantPlanRejectionDecision(reason)
        if (decision.applyGlobalBackoff) instantRouteBackoff.reject(player.tickCount)
        rejectedTargets.reject(target, player.tickCount)
        if (owner == MaceKillRouteOwner.FIGHT_BOT) {
            rejectFightBotMaceUse(target)
        } else {
            notifyMaceFailure(decision.notificationKey)
        }
        return false
    }

    private fun currentMaceKillRouteExecutionConfiguration(
        owner: MaceKillRouteOwner,
    ): MaceKillRouteExecutionConfiguration {
        val targetSpeed = movementConfiguration.targetSpeed.toDouble()
        val acceleration = movementConfiguration.acceleration.toDouble()
        val deceleration = movementConfiguration.deceleration.toDouble()
        val transport = selectMaceKillRouteTransport(
            configuredMotion = movementConfiguration.choice.activeMode === movementConfiguration.motion,
            owner = owner,
        )
        if (transport == MaceKillRouteTransport.MOTION) {
            return MaceKillRouteExecutionConfiguration(
                timing = MaceKillRouteTiming(
                    transport = transport,
                    stepDistance = minOf(targetSpeed, movementConfiguration.motion.stepDistance.toDouble()),
                ),
                routingMode = MaceKillRoutingMode.DIRECT,
                targetSpeed = targetSpeed,
                acceleration = acceleration,
                deceleration = deceleration,
            )
        }

        return currentPacketMaceKillRouteExecutionConfiguration(
            targetSpeed = targetSpeed,
            acceleration = acceleration,
            deceleration = deceleration,
            transport = transport,
        )
    }

    private fun currentPacketMaceKillRouteExecutionConfiguration(
        targetSpeed: Double,
        acceleration: Double,
        deceleration: Double,
        transport: MaceKillRouteTransport,
    ): MaceKillRouteExecutionConfiguration {
        val packet = movementConfiguration.packet
        val packetStepDistance = minOf(targetSpeed, packet.stepDistance.toDouble())
        return when (packet.routing.activeMode) {
            packet.aStar -> MaceKillRouteExecutionConfiguration(
                timing = MaceKillRouteTiming(transport, packetStepDistance, packet.stepDelay),
                routingMode = MaceKillRoutingMode.A_STAR,
                targetSpeed = targetSpeed,
                acceleration = acceleration,
                deceleration = deceleration,
                maxCost = packet.aStar.maxCost,
                diagonal = packet.aStar.diagonal,
                lineOfSightShortcuts = packet.aStar.lineOfSightShortcuts,
            )
            packet.instant -> MaceKillRouteExecutionConfiguration(
                timing = MaceKillRouteTiming(
                    transport = transport,
                    stepDistance = packetStepDistance,
                    stepWaitTicks = packet.stepDelay,
                    maxPacketsPerTick = maceKillInstantPacketsPerTick(
                        stepDelayTicks = packet.stepDelay,
                        packetBudget = packet.instant.maxPackets,
                    ),
                ),
                routingMode = MaceKillRoutingMode.INSTANT,
                targetSpeed = targetSpeed,
                acceleration = acceleration,
                deceleration = deceleration,
            )
            else -> MaceKillRouteExecutionConfiguration(
                timing = MaceKillRouteTiming(transport, packetStepDistance, packet.stepDelay),
                routingMode = MaceKillRoutingMode.DIRECT,
                targetSpeed = targetSpeed,
                acceleration = acceleration,
                deceleration = deceleration,
            )
        }
    }

    private fun buildMaceKillRoute(
        origin: Vec3,
        endpoint: Vec3,
        configuration: MaceKillRouteExecutionConfiguration,
        originBoundingBox: AABB = player.boundingBox,
        allowVanillaVClip: Boolean = true,
    ): MaceKillPlannedRoute? {
        lastInstantPlanBlockReason = null
        if (configuration.timing.transport == MaceKillRouteTransport.MOTION) {
            return selectMaceKillMotionRoutePlan(
                collisionPlan = {
                    buildCollisionAwareRoute(origin, endpoint, configuration, originBoundingBox)
                        ?.toMaceKillPlan(origin, stepWaitTicks = 0, motion = true)
                },
                vanillaVClipPlan = {
                    if (allowVanillaVClip) {
                        buildMaceKillVanillaVClipRoute(
                            origin,
                            endpoint,
                            configuration,
                            originBoundingBox,
                            motion = true,
                        )
                    } else {
                        null
                    }
                },
            )
        }

        return selectMaceKillRoutePlan(
            routingMode = configuration.routingMode,
            directPlan = {
                buildCollisionAwareRoute(origin, endpoint, configuration, originBoundingBox)
                    ?.toMaceKillPlan(origin, configuration.timing.stepWaitTicks)
            },
            aStarPlan = {
                buildAStarRoute(origin, endpoint, configuration, originBoundingBox)
                    ?.toMaceKillPlan(origin, configuration.timing.stepWaitTicks)
            },
            vanillaVClipPlan = {
                if (allowVanillaVClip) {
                    buildMaceKillVanillaVClipRoute(
                        origin,
                        endpoint,
                        configuration,
                        originBoundingBox,
                    )
                } else {
                    null
                }
            },
            wallClipPlan = { buildInstantRoute(origin, endpoint, configuration) },
        )
    }

    private fun buildInstantRoute(
        origin: Vec3,
        endpoint: Vec3,
        configuration: MaceKillRouteExecutionConfiguration,
    ): MaceKillPlannedRoute? {
        val instant = movementConfiguration.packet.instant
        val profile = MaceClipReachProfile.experimental(
            MaceClipReachResearchParameters(
                primingPacketCount = instant.primingPackets,
                clearanceHeight = instant.clearanceHeight.toDouble(),
                maxTargetDistance = maximumTargetRange.toDouble(),
                maxMovementPackets = instant.maxPackets,
                timeoutTicks = MACE_KILL_INSTANT_TIMEOUT_TICKS,
            ),
        )
        return when (val result = MaceClipReachPlanner.plan(
            MaceClipReachPlanRequest(
                origin = origin,
                endpoint = endpoint,
                dimensionBounds = MaceClipReachDimensionBounds(world.minY.toDouble(), world.maxY.toDouble()),
                profile = profile,
                use = MaceClipReachUse.EXPERIMENTAL,
                anchorValidator = MaceClipReachAnchorValidator { _, position ->
                    isMaceKillAnchorValid(origin, position)
                },
            ),
        )) {
            is MaceClipReachPlanResult.Ready -> {
                if (maceKillInstantRoundTripPacketCount(result.plan) > instant.maxPackets) {
                    lastInstantPlanBlockReason = MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED
                    null
                } else {
                    maceKillInstantPlannedRoute(result.plan, configuration.timing.stepWaitTicks)
                }
            }
            is MaceClipReachPlanResult.Blocked -> {
                lastInstantPlanBlockReason = result.reason
                null
            }
        }
    }

    private fun buildCollisionAwareRoute(
        origin: Vec3,
        endpoint: Vec3,
        configuration: MaceKillRouteExecutionConfiguration,
        originBoundingBox: AABB = player.boundingBox,
    ): SpearKillAStarPacketRoute? = buildSpearKillProfiledAStarPacketRoute(
        origin = origin,
        outboundWaypoints = listOf(endpoint),
        profile = currentMaceKillSpeedProfile(configuration),
        segmentValidator = createMaceKillSegmentValidator(origin, originBoundingBox),
    )

    private fun buildAStarRoute(
        origin: Vec3,
        endpoint: Vec3,
        configuration: MaceKillRouteExecutionConfiguration,
        originBoundingBox: AABB = player.boundingBox,
    ): SpearKillAStarPacketRoute? {
        val validator = createMaceKillSegmentValidator(origin, originBoundingBox)
        val playerBox = originBoundingBox
        val startNode = BlockPos.containing(origin)
        val endNode = BlockPos.containing(endpoint)
        val planner = SpearKillAStarRoutePlanner(
            allowDiagonal = configuration.diagonal,
            maxCost = configuration.maxCost,
            maxIterations = maceKillAStarIterationBudget(configuration.maxCost),
            isPassable = { node ->
                val position = maceKillAStarNodePosition(node, startNode, endNode, origin, endpoint)
                withVanillaSpearKillBlockShapes {
                    world.noCollision(player, playerBox.move(position.subtract(origin)))
                }
            },
            canTraverse = validator::isClear,
        )
        val waypoints = resolveSpearKillAStarApproachRoute(
            origin = origin,
            plannerGoal = endpoint,
            segmentValidator = validator,
        ) { planner.plan(origin, endpoint) } ?: return null
        val compacted = compactSpearKillAStarWaypoints(
            origin = origin,
            waypoints = waypoints,
            maxSpeed = configuration.timing.stepDistance,
            segmentValidator = validator,
            lineOfSightShortcuts = configuration.lineOfSightShortcuts,
        )
        val completeWaypoints = if (compacted.isEmpty()) listOf(endpoint) else compacted
        return buildSpearKillProfiledAStarPacketRoute(
            origin = origin,
            outboundWaypoints = completeWaypoints,
            profile = currentMaceKillSpeedProfile(configuration),
            segmentValidator = validator,
        )
    }

    /**
     * Keeps one short, explicit vanilla VClip separate from the collision-validated route around it.
     * The route engine derives the inverse return, so the same edge can only be crossed back exactly.
     */
    private fun buildMaceKillVanillaVClipRoute(
        origin: Vec3,
        endpoint: Vec3,
        configuration: MaceKillRouteExecutionConfiguration,
        originBoundingBox: AABB,
        motion: Boolean = false,
    ): MaceKillPlannedRoute? {
        for (movement in maceKillVanillaVClipCandidates(origin, endpoint)) {
            val originSegment = MaceKillVanillaVClipSegment(origin, origin.add(movement))
            buildMaceKillVanillaVClipRoute(
                origin = origin,
                endpoint = endpoint,
                configuration = configuration,
                originBoundingBox = originBoundingBox,
                segment = originSegment,
                vClipBeforeCollisionRoute = true,
                motion = motion,
            )?.let { return it }

            val endpointSegment = MaceKillVanillaVClipSegment(endpoint.subtract(movement), endpoint)
            buildMaceKillVanillaVClipRoute(
                origin = origin,
                endpoint = endpoint,
                configuration = configuration,
                originBoundingBox = originBoundingBox,
                segment = endpointSegment,
                vClipBeforeCollisionRoute = false,
                motion = motion,
            )?.let { return it }
        }
        return null
    }

    @Suppress("LongParameterList")
    private fun buildMaceKillVanillaVClipRoute(
        origin: Vec3,
        endpoint: Vec3,
        configuration: MaceKillRouteExecutionConfiguration,
        originBoundingBox: AABB,
        segment: MaceKillVanillaVClipSegment,
        vClipBeforeCollisionRoute: Boolean,
        motion: Boolean,
    ): MaceKillPlannedRoute? {
        if (!isMaceKillAnchorValid(origin, segment.from, originBoundingBox) ||
            !isMaceKillAnchorValid(origin, segment.to, originBoundingBox)
        ) {
            return null
        }
        val collisionOrigin = if (vClipBeforeCollisionRoute) segment.to else origin
        val collisionEndpoint = if (vClipBeforeCollisionRoute) endpoint else segment.from
        val collisionBoundingBox = originBoundingBox.move(collisionOrigin.subtract(origin))
        val collisionRoute = when (configuration.routingMode) {
            MaceKillRoutingMode.A_STAR ->
                buildAStarRoute(collisionOrigin, collisionEndpoint, configuration, collisionBoundingBox)
            MaceKillRoutingMode.DIRECT,
            MaceKillRoutingMode.INSTANT,
            -> buildCollisionAwareRoute(collisionOrigin, collisionEndpoint, configuration, collisionBoundingBox)
        } ?: return null
        return collisionRoute.toMaceKillPlan(
            origin = origin,
            stepWaitTicks = configuration.timing.stepWaitTicks,
            motion = motion,
            prefixMovements = if (vClipBeforeCollisionRoute) listOf(segment.movement) else emptyList(),
            suffixMovements = if (vClipBeforeCollisionRoute) emptyList() else listOf(segment.movement),
            vanillaVClipSegments = setOf(segment),
        )
    }

    private fun SpearKillAStarPacketRoute.toMaceKillPlan(
        origin: Vec3,
        stepWaitTicks: Int,
        motion: Boolean = false,
        prefixMovements: List<Vec3> = emptyList(),
        suffixMovements: List<Vec3> = emptyList(),
        vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
    ): MaceKillPlannedRoute {
        val allOutboundMovements = prefixMovements + outboundMovements + suffixMovements
        val request = RemoteKillRouteRequest(
            origin = origin,
            outboundMovements = allOutboundMovements,
            strikeHoldTicks = MACE_KILL_CHAIN_EVIDENCE_HOLD_TICKS,
            stepWaitTicks = stepWaitTicks,
            physicalReturn = motion,
        )
        return MaceKillPlannedRoute(
            request = request,
            renderPath = routePositions(origin, allOutboundMovements),
            motion = motion,
            vanillaVClipSegments = vanillaVClipSegments,
        )
    }

    private fun createMaceKillSegmentValidator(
        origin: Vec3,
        originBoundingBox: AABB = player.boundingBox,
        allowedVanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
    ): SpearKillAStarSegmentValidator {
        val collisionValidator = createSpearKillServerPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = originBoundingBox,
            hasDestinationCollision = { box ->
                withVanillaSpearKillBlockShapes { !world.noCollision(player, box) }
            },
            resolveMovement = { box, movement ->
                withVanillaSpearKillBlockShapes { resolveSpearKillServerPacketMovement(player, box, movement) }
            },
        )
        return SpearKillAStarSegmentValidator { from, to ->
            allowedVanillaVClipSegments.any { segment ->
                segment.matches(from, to) &&
                    isMaceKillAnchorValid(origin, from, originBoundingBox) &&
                    isMaceKillAnchorValid(origin, to, originBoundingBox)
            } || collisionValidator.isClear(from, to)
        }
    }

    private fun predictedMaceKillTarget(
        target: LivingEntity,
        origin: Vec3,
        timing: MaceKillRouteTiming,
    ): SpearKillRouteTargetPrediction {
        val travelTicks = timing.predictedTravelTicks(origin.distanceTo(target.position()))
        return captureSpearKillRouteTargetSnapshot(target, travelTicks).predict(travelTicks)
    }

    private fun findMaceKillAttackEndpoint(
        target: LivingEntity,
        origin: Vec3,
        targetPosition: Vec3 = target.position(),
        targetEyePosition: Vec3 = target.eyePosition,
        requireAttackCooldown: Boolean = true,
    ): Vec3? {
        val clearance = (player.bbWidth + target.bbWidth).toDouble() / 2.0 + 0.2
        return MaceKillEndpointPlanner.find(
            MaceKillEndpointSearchRequest(
                origin = origin,
                targetPosition = targetPosition,
                minimumClearance = clearance,
                maximumRadius = MACE_KILL_ENDPOINT_MAX_SEARCH_RADIUS,
            ),
        ) { endpoint ->
            isRemoteEndpointReady(
                player,
                target,
                endpoint,
                targetEyePosition,
                requireAttackCooldown,
            )
        }
    }

    @Suppress("CognitiveComplexMethod") // One tick owns target, strike, timeout, and delivery transitions.
    private fun tickActiveRemoteRoute() {
        val target = routeEngine.activeTarget
        val targetAlive = target != null && target.isAlive && !target.isRemoved && target.level() === world
        val instantOutcome = activeClipReachSession?.evaluate(player.tickCount.toLong(), targetAlive)
        val instantFailed = instantOutcome != null &&
            instantOutcome != MaceClipReachSessionOutcome.ACTIVE &&
            instantOutcome != MaceClipReachSessionOutcome.COMPLETED
        if (instantFailed) {
            handleInstantSessionOutcome(requireNotNull(instantOutcome))
        } else if (!routeSession.recovering && routeDeadlineTick != 0 && player.tickCount >= routeDeadlineTick) {
            routeRejected = true
            beginSafeRouteAbort()
        }
        if (!instantFailed) {
            if (!targetAlive) {
                if (target == null || activeClipReachSession != null || !tryStartTargetChain(target)) {
                    beginSafeRouteAbort()
                }
            } else if (activeRouteOwner != MaceKillRouteOwner.RESEARCH) {
                replanMovingTargetBeforeStrike(requireNotNull(target))
            }
            if (routeEngine.awaitingStrike) {
                handleRemoteStrikeResult(routeEngine.retryStrike())
            }
        }
        if (routeStallTicks >= MACE_KILL_MAX_ROUTE_STALL_TICKS) {
            activeClipReachSession?.recordReplanRejected()?.let(::handleInstantSessionOutcome)
            beginSafeRouteAbort()
        }
        if (plannedRoutePacket != null || player.tickCount < routeResumeTick) return

        val timing = activeRouteConfiguration?.timing
        val correctionRecovery = activeClipReachSession?.outcome == MaceClipReachSessionOutcome.CORRECTED
        if (timing?.maxPacketsPerTick?.let { it > 1 } == true && !motionRouteActive && !correctionRecovery) {
            repeat(timing.maxPacketsPerTick) {
                if (!routeEngine.ownsMovement || plannedRoutePacket != null || !sendNextRoutePacket()) return@repeat
            }
        } else {
            sendNextRoutePacket()
        }
        if (!routeEngine.ownsMovement) finishInactiveRouteOwnership()
    }

    private fun sendNextRoutePacket(): Boolean {
        val origin = routeOrigin ?: return false
        val pendingOffset = routeEngine.prepareNextStep() ?: return false
        if (!routeEngine.ownsMovement) return false
        if (shouldValidateMaceKillRouteSegment(
                clipAnchorOwned = ownsClipReachAnchorPackets(),
                clipRecoveryOwned = ownsClipReachRecoveryPackets(),
                researchOwned = researchExecution != null,
            ) &&
            !validatePendingRouteStep(origin, pendingOffset)
        ) {
            beginSafeRouteAbort()
            return false
        }

        val position = origin.add(pendingOffset)
        val pendingMovement = routeSession.pendingMovement ?: return false
        val physicallyNearGround = isMaceKillPositionNearGround(position)
        val packetGrounded = maceKillRoutePacketGrounded(position, identityOwnedByRoute = true)
        val safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
        val projectedFallDistance = if (packetGrounded &&
            (ownsClipReachAnchorPackets() || ownsClipReachRecoveryPackets())
        ) {
            0.0
        } else {
            projectedMaceKillFallDistance(fallSafetyLifecycle.confirmedFallDistance, pendingMovement)
        }
        if (!projectedFallDistance.isFinite() || !safeFallDistance.isFinite() || safeFallDistance < 0.0 ||
            physicallyNearGround && projectedFallDistance > safeFallDistance
        ) {
            beginSafeRouteAbort()
            return false
        }
        if (fallSafetyLifecycle.gatePendingMovement(
                pendingMovement,
                packetGrounded,
            ) == SpearKillFallSafetyPendingStepGate.BLOCKED
        ) {
            beginSafeRouteAbort()
            return false
        }
        val packet = createMaceKillMovementPacket(position, packetGrounded)
        attachResearchPacketContext(packet, position)
        plannedRoutePacket = packet
        network.send(packet)
        if (plannedRoutePacket === packet) {
            val queuedByBlink = BlinkManager.packetQueue.any { it.packet === packet }
            if (queuedByBlink) BlinkManager.packetQueue.removeIf { it.packet === packet }
            recordResearchPacketDelivery(packet, delivered = false, queuedByBlink = queuedByBlink)
            plannedRoutePacket = null
            routeEngine.confirmStep(delivered = false)
            routeStallTicks++
        }
        return true
    }

    private fun validatePendingRouteStep(origin: Vec3, candidateOffset: Vec3): Boolean {
        val from = origin.add(routeSession.committedOffset)
        val to = origin.add(candidateOffset)
        return createMaceKillSegmentValidator(
            origin = origin,
            originBoundingBox = routeOriginBoundingBox ?: player.boundingBox,
            allowedVanillaVClipSegments = activeVanillaVClipSegments,
        ).isClear(from, to)
    }

    @Suppress("CognitiveComplexMethod", "LongMethod", "ReturnCount")
    private fun commitRemoteStrike(request: RemoteKillStrikeRequest<LivingEntity>): RemoteKillStrikeResult {
        if (shouldDeferMaceKillStrike(player.tickCount, remoteStrikeEarliestTick)) {
            return RemoteKillStrikeResult.Deferred
        }
        val target = request.target
        val research = researchExecution
        if (activeRouteOwner == MaceKillRouteOwner.RESEARCH && research != null) {
            researchRuntime.recordPhaseStarted(
                research.sessionId,
                MaceClipResearchPhase.STRIKE,
                player.tickCount,
                request.endpoint,
            )
            val result = if (research.descriptor.request is MaceClipResearchProbeRequest.Move) {
                RemoteKillStrikeResult.Committed
            } else {
                commitMaceAttackAtEndpoint(target, request.endpoint).also { strikeResult ->
                    researchRuntime.recordStrikeAttempt(
                        research.sessionId,
                        strikeResult == RemoteKillStrikeResult.Committed,
                    )
                }
            }
            researchRuntime.recordPhaseCompleted(
                research.sessionId,
                MaceClipResearchPhase.STRIKE,
                player.tickCount,
                request.endpoint,
            )
            return result
        }
        val clipSession = activeClipReachSession
        if (clipSession != null) {
            val outcome = clipSession.evaluate(
                player.tickCount.toLong(),
                target.isAlive && !target.isRemoved && target.level() === world,
            )
            if (outcome != MaceClipReachSessionOutcome.ACTIVE) {
                handleInstantSessionOutcome(outcome)
                return RemoteKillStrikeResult.Rejected("instant-session-terminal")
            }
        }
        if (activeRouteTarget !== target || !isMaceKillTargetEligible(target) || !hasServerHeldMace()) {
            return RemoteKillStrikeResult.Rejected("target-or-weapon-invalid")
        }
        val result = commitMaceAttackAtEndpoint(target, request.endpoint)
        if (result == RemoteKillStrikeResult.Committed && clipSession != null &&
            activeRouteOwner != MaceKillRouteOwner.RESEARCH
        ) {
            val returnPrimingPackets = clipSession.plan.profile.parameters.primingPacketCount
            if (!sendMaceKillPrimingPackets(request.endpoint, returnPrimingPackets)) {
                clipSession.recordReplanRejected()
                handleInstantSessionOutcome(MaceClipReachSessionOutcome.REPLAN_REJECTED)
                return RemoteKillStrikeResult.Rejected("return-priming-rejected")
            }
            debugMaceKill("return-prime") { listOf("packets" to returnPrimingPackets) }
        }
        if (result == RemoteKillStrikeResult.Committed && clipSession != null &&
            !clipSession.commitStrike(player.tickCount.toLong(), targetAlive = true)
        ) {
            clipSession.recordReplanRejected()
            handleInstantSessionOutcome(MaceClipReachSessionOutcome.REPLAN_REJECTED)
        }
        return result
    }

    private fun commitMaceAttackAtEndpoint(target: LivingEntity, endpoint: Vec3): RemoteKillStrikeResult {
        if (!target.isAlive || target.isRemoved || target.level() !== world || !hasServerHeldMace()) {
            return RemoteKillStrikeResult.Rejected("target-or-weapon-invalid")
        }
        val endpointBoundingBox = player.boundingBox.move(endpoint.subtract(player.position()))
        val fallResetResult = MacePostAttackFallResetPlanner.plan(
            MacePostAttackFallResetRequest(endpoint, endpointBoundingBox),
        ) { box -> world.getBlockCollisions(player, box).allEmpty() }
        val fallResetPlan = (fallResetResult as? MacePostAttackFallResetPlanResult.Ready)?.plan
            ?: return RemoteKillStrikeResult.Rejected("post-attack-fall-reset-unavailable")
        remoteStrikeTarget = target
        remoteStrikeEndpoint = endpoint
        remoteStrikeFallResetPlan = fallResetPlan
        val result = try {
            attackEntityWithResult(target, SwingMode.DO_NOT_HIDE, keepSprint = true).also { attackResult ->
                if (attackResult == MaceKillAttackResult.APPLIED) {
                    applyMaceStrikePackets(player, fallResetPlan.packets)
                    debugMaceKill("post-strike-fall-reset") { listOf("rise" to fallResetPlan.rise) }
                }
            }
        } finally {
            remoteStrikeTarget = null
            remoteStrikeEndpoint = null
            remoteStrikeFallResetPlan = null
        }
        debugMaceKill("strike-result") {
            listOf("target" to target.id, "endpoint" to endpoint, "result" to result)
        }
        return when (result) {
            MaceKillAttackResult.APPLIED -> {
                rejectedTargets.allow(target)
                evidenceTargetId = target.id
                evidenceDeadlineTick = player.tickCount + MACE_KILL_DAMAGE_EVIDENCE_TICKS
                RemoteKillStrikeResult.Committed
            }
            MaceKillAttackResult.NOT_APPLIED -> RemoteKillStrikeResult.Rejected("mace-spoof-not-applied")
            MaceKillAttackResult.REJECTED -> RemoteKillStrikeResult.Rejected("accepted-attack-rejected")
        }
    }

    private fun handleRemoteStrikeResult(result: RemoteKillStrikeResult?) {
        if (result is RemoteKillStrikeResult.Rejected) {
            debugMaceKill("strike-rejected") { listOf("reason" to result.reason) }
            routeAdmissionBackoff.reject(player.tickCount)
            activeRouteTarget?.let { rejectedTargets.reject(it, player.tickCount) }
            routeRejected = true
            holdAttackState = armMaceKillHoldAttackRetry(holdAttackState)
            if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
                fightBotMaceState = MaceKillFightBotState.Rejected
            }
        }
    }

    private fun handleInstantSessionOutcome(
        outcome: MaceClipReachSessionOutcome,
        abortRoute: Boolean = true,
    ) {
        val session = activeClipReachSession ?: return
        val decision = maceKillInstantTerminalDecision(outcome, session.strikeCommitted)
        if (!decision.rejectAttempt || instantTerminalHandled) return

        instantTerminalHandled = true
        activeRouteTarget?.let { rejectedTargets.reject(it, player.tickCount) }
        routeRejected = true
        holdAttackState = armMaceKillHoldAttackRetry(holdAttackState)
        instantRouteBackoff.reject(player.tickCount)
        decision.notificationKey?.let(::notifyMaceFailure)
        if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
            fightBotMaceState = MaceKillFightBotState.Rejected
        }
        debugMaceKill("instant-terminal") {
            listOf("outcome" to outcome, "strike-committed" to session.strikeCommitted)
        }
        if (abortRoute && decision.abortRoute && routeEngine.ownsMovement) beginSafeRouteAbort()
    }

    private fun sendMaceKillPrimingPackets(position: Vec3, count: Int): Boolean {
        primingDeliveryFailed = false
        repeat(count) {
            val packet = createMaceKillMovementPacket(
                position,
                maceKillRoutePacketGrounded(position, identityOwnedByRoute = true),
            )
            researchExecution?.let { execution ->
                researchPacketContexts[packet] = MaceKillResearchPacketContext(
                    sequence = execution.nextPacketSequence++,
                    phase = MaceClipResearchPhase.PRIME,
                    position = position,
                    outbound = null,
                )
                researchRuntime.recordPhaseStarted(
                    execution.sessionId,
                    MaceClipResearchPhase.PRIME,
                    player.tickCount,
                    position,
                )
            }
            primingPackets += packet
            network.send(packet)
            if (packet in primingPackets) {
                confirmPrimingPacket(packet, cancelled = true)
            }
        }
        return !primingDeliveryFailed
    }

    private fun confirmPrimingPacket(packet: ServerboundMovePlayerPacket, cancelled: Boolean) {
        primingPackets -= packet
        val queuedByBlink = BlinkManager.packetQueue.any { it.packet === packet }
        if (queuedByBlink) BlinkManager.packetQueue.removeIf { it.packet === packet }
        val delivered = !cancelled && !queuedByBlink
        recordResearchPacketDelivery(packet, delivered, queuedByBlink)
        if (!delivered) primingDeliveryFailed = true
    }

    private fun applyMotionRoutePosition() {
        if (!motionRouteActive) return
        val origin = routeOrigin ?: return
        player.setPos(origin.add(routeSession.committedOffset))
    }

    private fun maintainPacketRouteOrigin() {
        val origin = localPacketRouteOrigin ?: return
        val preservePhysicalMovement = researchExecution == null
        requiredMaceKillLocalRestore(
            packetRouteOwned = routeEngine.ownsMovement,
            preservePhysicalMovement = preservePhysicalMovement,
            origin = origin,
            currentPosition = player.position(),
        )?.let(player::setPos)
        if (!preservePhysicalMovement) player.deltaMovement = Vec3.ZERO
    }

    private fun finishCompletedRouteSession() {
        check(routeEngine.ownsMovement && !routeSession.active && !routeEngine.awaitingStrike)
        maintainPacketRouteOrigin()
        activeClipReachSession?.complete()
        if (researchExecution != null || motionRouteActive) {
            if (!finishMaceKillFallSafety()) return
            routeEngine.releaseCompletedOwnership()
            finishInactiveRouteOwnership()
            return
        }

        returnConfirmation.onExactReturnDelivered(
            player.tickCount,
            maceKillReturnConfirmationTicks(activeRouteConfiguration?.routingMode),
        )
        if (!returnConfirmation.shouldRelease(player.tickCount)) return
        if (!finishMaceKillFallSafety()) return
        routeEngine.releaseCompletedOwnership()
        finishInactiveRouteOwnership()
    }

    private fun maintainFightBotMaceLease() {
        if (activeRouteOwner != MaceKillRouteOwner.FIGHT_BOT) return
        if (pendingFightBotTerminal != null) return
        val target = activeRouteTarget
        val source = fightBotMaceSource
        val valid = target != null && fightBotMaceTarget === target && target.isAlive && !target.isRemoved &&
            ModuleFightBot.configuredMaceAutomation != FightBotMaceAutomation.Off && when (source) {
                FightBotMaceUseSource.MainHand -> player.mainHandItem.item == Items.MACE
                is FightBotMaceUseSource.Hotbar -> isMaceInHotbarSlot(source.slot) &&
                    SilentHotbar.selectSlotSilently(FightBotMaceUseRequester, source.slot, 2)
                null -> false
            }
        if (valid) return

        fightBotMaceState = MaceKillFightBotState.Rejected
        beginFightBotTerminal(MaceKillFightBotTerminal.Rejection)
    }

    private fun finishInactiveRouteOwnership() {
        if (routeEngine.ownsMovement) return

        val research = researchExecution
        if (research != null) {
            finishResearchProbeWhenReady(research)
            return
        }
        if (activeRouteOwner == MaceKillRouteOwner.NONE) return

        val completedOwner = activeRouteOwner
        finishMaceKillFallSafety()
        val rejected = routeRejected
        val effectiveFightBotTerminal = pendingFightBotTerminal
            ?: if (rejected) MaceKillFightBotTerminal.Rejection else MaceKillFightBotTerminal.Completion
        clearRouteOwnership(rejected)
        if (completedOwner == MaceKillRouteOwner.FIGHT_BOT) {
            if (effectiveFightBotTerminal == MaceKillFightBotTerminal.Rejection) {
                finalizeFightBotRejection()
            } else {
                clearFightBotMaceUse(effectiveFightBotTerminal)
            }
        }
    }

    private fun clearRouteOwnership(rejected: Boolean = false) {
        val instantFailureHandled = instantTerminalHandled
        activeRouteTarget = null
        activeRouteOwner = MaceKillRouteOwner.NONE
        remoteStrikeEndpoint = null
        remoteStrikeTarget = null
        remoteStrikeFallResetPlan = null
        remoteStrikeEarliestTick = 0
        routeOrigin = null
        routeOriginBoundingBox = null
        routeRenderPath = emptyList()
        routeStepWaitTicks = 0
        routeStallTicks = 0
        routeResumeTick = 0
        plannedRoutePacket = null
        groundingPacketTracker.clear()
        primingPackets.clear()
        researchPacketContexts.clear()
        motionRouteActive = false
        activeVanillaVClipSegments = emptySet()
        activeClipReachSession = null
        instantRecoveryPlan = null
        instantCorrectionRecoveryActive = false
        lastInstantPlanBlockReason = null
        plannedTargetPosition = null
        routeChainCount = 0
        activeRouteConfiguration = null
        routeDeadlineTick = 0
        localPacketRouteOrigin = null
        returnConfirmation.clear()
        speedController.reset()
        fallSafetyLifecycle.invalidate()
        if (rejected && !instantFailureHandled) notifyMaceFailure("routeRejected")
        instantTerminalHandled = false
        routeRejected = false
    }

    private fun finalizeFightBotRejection() {
        if (fightBotMaceSource is FightBotMaceUseSource.Hotbar) {
            SilentHotbar.resetSlot(FightBotMaceUseRequester)
        }
        fightBotMaceSource = null
        pendingFightBotTerminal = null
        fightBotMaceState = MaceKillFightBotState.Rejected
    }

    @Suppress("LongMethod", "ReturnCount") // Every guard preserves the confirmed route prefix.
    private fun replanMovingTargetBeforeStrike(target: LivingEntity) {
        activeClipReachSession?.let { session ->
            replanInstantTargetBeforeStrike(target, session)
            return
        }
        if (routeSession.recovering || routeSession.requiresDelivery || !routeSession.canReplaceRemainingApproach) {
            return
        }
        val previous = plannedTargetPosition ?: return
        if (target.position().distanceToSqr(previous) < MACE_KILL_TARGET_REPLAN_DISTANCE_SQUARED) return

        val sessionOrigin = routeOrigin ?: return
        val configuration = activeRouteConfiguration ?: return
        val currentPosition = sessionOrigin.add(routeSession.committedOffset)
        val predicted = predictedMaceKillTarget(target, currentPosition, configuration.timing)
        val endpoint = findMaceKillAttackEndpoint(
            target,
            currentPosition,
            predicted.position,
            predicted.eyePosition,
        ) ?: run {
            beginSafeRouteAbort()
            return
        }
        val routeBoundingBox = routeOriginBoundingBox ?: player.boundingBox
        val replacement = buildMaceKillRoute(
            currentPosition,
            endpoint,
            configuration,
            routeBoundingBox.move(routeSession.committedOffset),
            allowVanillaVClip = activeVanillaVClipSegments.isEmpty(),
        ) ?: run {
            beginSafeRouteAbort()
            return
        }
        val committedRecovery = routeSession.exactRecoveryMovementsFrom(routeSession.committedOffset) ?: return
        if (replacement.clipReachPlan != null || replacement.primingPackets != 0 ||
            !routeSession.replaceRemainingOutbound(
                replacement.request.outboundMovements,
                replacement.request.strikeHoldTicks,
            )
        ) {
            return
        }
        val futureMovements = replacement.request.outboundMovements +
            replacement.request.returnMovements + committedRecovery
        val replacementVClipSegments = activeVanillaVClipSegments + replacement.vanillaVClipSegments
        if (!replanMaceKillFallSafety(
                currentPosition,
                futureMovements,
                replacement.request.outboundMovements.size,
                vanillaVClipSegments = replacementVClipSegments,
            )
        ) {
            beginSafeRouteAbort()
            return
        }
        activeVanillaVClipSegments = replacementVClipSegments
        val fullOutbound = buildList {
            if (routeSession.committedOffset.lengthSqr() >= MACE_KILL_MOVEMENT_EPSILON_SQUARED) {
                add(routeSession.committedOffset)
            }
            addAll(replacement.request.outboundMovements)
        }
        val request = RemoteKillRouteRequest(
            origin = sessionOrigin,
            outboundMovements = fullOutbound,
            strikeHoldTicks = replacement.request.strikeHoldTicks,
            stepWaitTicks = replacement.request.stepWaitTicks,
        )
        routeEngine.handoff(target, request)
        plannedTargetPosition = predicted.position
        routeRenderPath = replacement.renderPath
        debugMaceKill("target-replan") { listOf("target" to target.id, "steps" to fullOutbound.size) }
    }

    @Suppress("LongMethod", "ReturnCount") // Replan retains the delivery-confirmed prefix atomically.
    private fun replanInstantTargetBeforeStrike(target: LivingEntity, session: MaceClipReachSession) {
        if (session.strikeCommitted) return
        val previous = plannedTargetPosition ?: return
        if (target.position().distanceToSqr(previous) < MACE_KILL_TARGET_REPLAN_DISTANCE_SQUARED) return
        if (routeSession.recovering) {
            val endpointStillReady = routeEngine.activeRequest?.endpoint?.let { endpoint ->
                isRemoteEndpointReady(player, target, endpoint)
            } == true
            when (maceKillInstantTargetMovementAction(
                recovering = true,
                endpointStillReady = endpointStillReady,
            )) {
                MaceKillInstantTargetMovementAction.KEEP_CONFIRMED_ENDPOINT -> {
                    plannedTargetPosition = target.position()
                    return
                }
                MaceKillInstantTargetMovementAction.REJECT -> {
                    handleInstantSessionOutcome(session.recordReplanRejected())
                    return
                }
                MaceKillInstantTargetMovementAction.REPLAN_UNCONFIRMED -> Unit
            }
        }
        if (routeSession.requiresDelivery || !routeSession.canReplaceRemainingOutbound) return

        val sessionOrigin = routeOrigin ?: return
        val configuration = activeRouteConfiguration ?: return
        val currentPosition = sessionOrigin.add(routeSession.committedOffset)
        val predicted = predictedMaceKillTarget(target, currentPosition, configuration.timing)
        val endpoint = findMaceKillAttackEndpoint(
            target,
            currentPosition,
            predicted.position,
            predicted.eyePosition,
        ) ?: run {
            handleInstantSessionOutcome(session.recordReplanRejected())
            return
        }
        val replan = session.replanTerminal(
            endpoint,
            MaceClipReachAnchorValidator { _, position -> isMaceKillAnchorValid(sessionOrigin, position) },
        )
        val plan = (replan as? MaceClipReachReplanResult.Applied)?.plan ?: run {
            val rejected = replan as MaceClipReachReplanResult.Rejected
            if (rejected.reason != MaceClipReachReplanBlockReason.STRIKE_COMMITTED) {
                handleInstantSessionOutcome(session.outcome)
            }
            return
        }
        val confirmedCount = session.confirmedOutboundMovementCount
        val remainingOutbound = plan.outboundMovements.drop(confirmedCount)
        if (remainingOutbound.isEmpty()) {
            handleInstantSessionOutcome(session.recordReplanRejected())
            return
        }
        val futureMovements = remainingOutbound + plan.returnMovements
        if (!replanMaceKillFallSafety(
                currentPosition,
                futureMovements,
                remainingOutbound.size,
                MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF,
            ) || !routeSession.replaceRemainingOutbound(
                remainingOutbound,
                MACE_KILL_CHAIN_EVIDENCE_HOLD_TICKS,
                completeReturnMovements = plan.returnMovements,
            )
        ) {
            handleInstantSessionOutcome(session.recordReplanRejected())
            return
        }

        routeEngine.handoff(
            target,
            RemoteKillRouteRequest(
                origin = sessionOrigin,
                outboundMovements = plan.outboundMovements,
                strikeHoldTicks = MACE_KILL_CHAIN_EVIDENCE_HOLD_TICKS,
                stepWaitTicks = configuration.timing.stepWaitTicks,
                returnMovements = plan.returnMovements,
            ),
        )
        instantRecoveryPlan = plan
        plannedTargetPosition = predicted.position
        routeRenderPath = routePositions(sessionOrigin, plan.outboundMovements)
        debugMaceKill("instant-target-replan") {
            listOf("target" to target.id, "confirmed" to confirmedCount, "remaining" to remainingOutbound.size)
        }
    }

    @Suppress("LongMethod", "ReturnCount") // Chaining rejects at the first invalid ownership invariant.
    private fun tryStartTargetChain(defeatedTarget: LivingEntity): Boolean {
        if (!routeSession.canStartChainedOutbound || routeChainCount >= MACE_KILL_MAX_CHAIN_TARGETS ||
            !activeRouteOwner.allowsTargetChain
        ) {
            return false
        }
        if (!mc.options.keyAttack.isPressedOnAny) return false

        val sessionOrigin = routeOrigin ?: return false
        val configuration = activeRouteConfiguration ?: return false
        val chainOrigin = sessionOrigin.add(routeSession.committedOffset)
        val nextTarget = world.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(
                chainOrigin,
                maximumTargetRange.toDouble() * 2.0,
                maximumTargetRange.toDouble() * 2.0,
                maximumTargetRange.toDouble() * 2.0,
            ),
        ) { candidate -> candidate !== defeatedTarget && isMaceKillTargetEligible(candidate) }
            .minByOrNull { candidate -> chainOrigin.distanceToSqr(candidate.position()) } ?: return false
        val predicted = predictedMaceKillTarget(nextTarget, chainOrigin, configuration.timing)
        val endpoint = findMaceKillAttackEndpoint(
            nextTarget,
            chainOrigin,
            predicted.position,
            predicted.eyePosition,
        ) ?: return false
        val routeBoundingBox = routeOriginBoundingBox ?: player.boundingBox
        val route = buildMaceKillRoute(
            chainOrigin,
            endpoint,
            configuration,
            routeBoundingBox.move(routeSession.committedOffset),
            allowVanillaVClip = activeVanillaVClipSegments.isEmpty(),
        ) ?: return false
        val committedRecovery = routeSession.exactRecoveryMovementsFrom(routeSession.committedOffset) ?: return false
        if (route.clipReachPlan != null || route.primingPackets != 0 || !routeSession.startChainedOutbound(
                route.request.outboundMovements,
                route.request.strikeHoldTicks,
            )
        ) {
            return false
        }
        val futureMovements = route.request.outboundMovements + route.request.returnMovements + committedRecovery
        val chainedVClipSegments = activeVanillaVClipSegments + route.vanillaVClipSegments
        if (!replanMaceKillFallSafety(
                chainOrigin,
                futureMovements,
                route.request.outboundMovements.size,
                vanillaVClipSegments = chainedVClipSegments,
            )
        ) {
            beginSafeRouteAbort()
            return false
        }
        activeVanillaVClipSegments = chainedVClipSegments
        val fullOutbound = listOf(routeSession.committedOffset) + route.request.outboundMovements
        routeEngine.handoff(
            nextTarget,
            RemoteKillRouteRequest(
                origin = sessionOrigin,
                outboundMovements = fullOutbound,
                strikeHoldTicks = route.request.strikeHoldTicks,
                stepWaitTicks = route.request.stepWaitTicks,
            ),
        )
        activeRouteTarget = nextTarget
        plannedTargetPosition = predicted.position
        routeRenderPath = route.renderPath
        routeChainCount++
        evidenceTargetId = null
        evidenceDeadlineTick = 0
        debugMaceKill("target-chain") { listOf("target" to nextTarget.id, "chain" to routeChainCount) }
        return true
    }

    private fun prepareRemoteCorrection(correctionPlayer: Player) {
        if (correctionPlayer !== mc.player || !routeEngine.ownsMovement) return
        val origin = routeOrigin ?: return
        activeClipReachSession?.let { session ->
            if (!session.strikeCommitted) instantServerRejected = true
            handleInstantSessionOutcome(session.recordCorrection(), abortRoute = false)
        }
        returnConfirmation.onCorrection()
        val expected = origin.add(routeSession.virtualOffset)
        correctionState = MaceKillLocalCorrectionState(
            expectedPosition = expected,
            routeOrigin = origin,
            researchPhase = currentResearchPhase(),
        )
    }

    @Suppress("CognitiveComplexMethod", "LongMethod", "ReturnCount")
    // Correction evidence and exact recovery form one atomic transition.
    private fun finishRemoteCorrection(correctionPlayer: Player) {
        if (correctionPlayer !== mc.player) return
        val state = correctionState.also { correctionState = null } ?: return
        val authoritativePosition = correctionPlayer.position()
        researchExecution?.let { research ->
            researchRuntime.recordCorrection(
                research.sessionId,
                state.researchPhase ?: MaceClipResearchPhase.RETURN_DESCEND,
                correctionPlayer.tickCount,
                state.expectedPosition,
                authoritativePosition,
            )
            researchRuntime.recordCorrectionAuthoritativePosition(research.sessionId, authoritativePosition)
        }
        val authoritativeOffset = authoritativePosition.subtract(state.routeOrigin)
        localPacketRouteOrigin = state.routeOrigin
        if (authoritativeOffset.lengthSqr() < MACE_KILL_EXACT_RETURN_EPSILON_SQUARED) {
            correctionPlayer.setPos(state.routeOrigin)
            when (maceKillOriginCorrectionAction(routeSession.active)) {
                MaceKillOriginCorrectionAction.ABORT_ACTIVE_ROUTE -> {
                    routeRejected = true
                    routeEngine.clear()
                    finishInactiveRouteOwnership()
                }
                MaceKillOriginCorrectionAction.CONFIRM_COMPLETED_RETURN ->
                    returnConfirmation.onExactReturnDelivered(
                        correctionPlayer.tickCount,
                        maceKillReturnConfirmationTicks(activeRouteConfiguration?.routingMode),
                    )
            }
            return
        }
        val instantCorrection = activeRouteConfiguration?.routingMode == MaceKillRoutingMode.INSTANT
        val correctionAction = maceKillCorrectionRecoveryAction(correctionRecoveryAttempts)
        if (instantCorrection) correctionRecoveryAttempts++
        correctionPlayer.setPos(state.routeOrigin)
        correctionPlayer.deltaMovement = Vec3.ZERO
        val clipSession = activeClipReachSession
        val researchClipRecovery = clipSession != null && activeRouteOwner == MaceKillRouteOwner.RESEARCH
        val clipInverseRecovery = if (instantCorrection && !researchClipRecovery) {
            instantRecoveryPlan?.let { plan -> maceKillFullInverseRecovery(plan, authoritativePosition) }
        } else {
            null
        }
        val inverseRecovery = clipInverseRecovery
            ?: routeSession.exactRecoveryMovementsFrom(authoritativeOffset)
        val recoveryMovements = if (researchClipRecovery) {
            val preferredApexY = clipSession.plan.steps
                .asSequence()
                .filter { it.evidencePhase == MaceClipReachEvidencePhase.ASCEND ||
                    it.evidencePhase == MaceClipReachEvidencePhase.TRANSFER }
                .maxOfOrNull { it.position.y }
                ?: clipSession.plan.steps.maxOf { it.position.y }
            val result = MaceClipReachPlanner.planCorrectionRecovery(
                MaceClipReachRecoveryRequest(
                    authoritativePosition = authoritativePosition,
                    origin = state.routeOrigin,
                    preferredApexY = preferredApexY,
                    dimensionBounds = clipSession.plan.dimensionBounds,
                    maxMovementPackets = clipSession.plan.profile.parameters.maxMovementPackets,
                    anchorValidator = MaceClipReachAnchorValidator { _, position ->
                        isMaceKillAnchorValid(state.routeOrigin, position)
                    },
                ),
            )
            (result as? MaceClipReachRecoveryResult.Ready)?.movements
        } else {
            val configuration = activeRouteConfiguration
                ?: currentMaceKillRouteExecutionConfiguration(MaceKillRouteOwner.KILL_AURA)
            val recoveryConfiguration = if (instantCorrection) {
                maceKillInstantCorrectionRecoveryConfiguration(configuration)
            } else {
                configuration
            }
            val recoveryBoundingBox = maceKillBoundingBoxAtRouteOrigin(
                correctionPlayer.boundingBox,
                correctionPlayer.position(),
                authoritativePosition,
            )
            val collisionRecovery = if (inverseRecovery == null && (!instantCorrection ||
                correctionAction == MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED
                )
            ) {
                buildCollisionAwareRoute(
                    authoritativePosition,
                    state.routeOrigin,
                    recoveryConfiguration,
                    recoveryBoundingBox,
                ) ?: buildAStarRoute(
                    authoritativePosition,
                    state.routeOrigin,
                    recoveryConfiguration,
                    recoveryBoundingBox,
                )
            } else {
                null
            }
            val forcedRecovery = if (instantCorrection) {
                maceKillForcedOriginRecovery(authoritativePosition, state.routeOrigin)
            } else {
                null
            }
            selectMaceKillCorrectionRecoveryMovements(
                action = correctionAction,
                inverseRecovery = inverseRecovery,
                collisionRecovery = collisionRecovery?.outboundMovements,
                forcedRecovery = forcedRecovery,
            )
        }
        if (recoveryMovements.isNullOrEmpty()) {
            debugMaceKill("correction-recovery-rejected") {
                listOf(
                    "reason" to "no-collision-aware-route",
                    "authoritative" to authoritativePosition,
                    "origin" to state.routeOrigin,
                )
            }
            routeEngine.clear()
            routeRejected = true
            notifyMaceFailure("correctionRecoveryFailed")
            finishInactiveRouteOwnership()
            return
        }
        if (instantCorrection && !researchClipRecovery) {
            activeClipReachSession = null
            instantCorrectionRecoveryActive = true
            routeStepWaitTicks = 0
            activeRouteConfiguration = activeRouteConfiguration?.let(::maceKillInstantCorrectionRecoveryConfiguration)
        }
        if (!replanMaceKillFallSafety(
                authoritativePosition,
                recoveryMovements,
                outboundStepCount = 0,
                groundPolicy = if (researchClipRecovery || instantCorrectionRecoveryActive) {
                    MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF
                } else {
                    MaceKillGroundPolicy.COLLISION_DERIVED
                },
            )
        ) {
            debugMaceKill("correction-recovery-rejected") {
                listOf("reason" to "fall-safety", "steps" to recoveryMovements.size)
            }
            routeEngine.clear()
            routeRejected = true
            notifyMaceFailure("correctionRecoveryFailed")
            finishInactiveRouteOwnership()
            return
        }
        runCatching {
            routeEngine.beginPacketExactRecoveryFrom(
                authoritativeOffset,
                recoveryMovements,
                routeStepWaitTicks,
            )
        }.getOrElse { exception ->
            debugMaceKill("correction-recovery-rejected") {
                listOf("reason" to "engine", "exception" to exception::class.simpleName)
            }
            routeEngine.clear()
            routeRejected = true
            notifyMaceFailure("correctionRecoveryFailed")
            finishInactiveRouteOwnership()
            return
        }
        motionRouteActive = false
        routeRenderPath = routePositions(authoritativePosition, recoveryMovements)
        routeResumeTick = correctionPlayer.tickCount + networkSetbackBackoffTicks()
        debugMaceKillChanged(
            channel = "correction-recovery",
            event = "correction",
            fingerprint = { listOf(authoritativePosition, correctionAction, recoveryMovements.size) },
        ) {
            listOf(
                "distance" to state.expectedPosition.distanceTo(authoritativePosition),
                "action" to correctionAction,
                "steps" to recoveryMovements.size,
                "resume" to routeResumeTick,
            )
        }
    }

    private fun activeRouteStepDistance(): Double = activeRouteConfiguration?.timing?.stepDistance
        ?: minOf(movementConfiguration.targetSpeed, movementConfiguration.packet.stepDistance).toDouble()

    private fun currentMaceKillSpeedLimits(
        configuration: MaceKillRouteExecutionConfiguration,
    ): SpearKillSpeedLimits = SpearKillSpeedLimits(
        targetSpeed = minOf(configuration.targetSpeed, configuration.timing.stepDistance),
        acceleration = configuration.acceleration,
        deceleration = configuration.deceleration,
        stepDistance = configuration.timing.stepDistance,
        vanillaBudget = calculateSpearKillVanillaMovementBudget(player.deltaMovement, player.isFallFlying),
    )

    private fun currentMaceKillSpeedProfile(
        configuration: MaceKillRouteExecutionConfiguration,
    ): SpearKillSpeedProfile {
        val limits = currentMaceKillSpeedLimits(configuration)
        val initialSpeed = if (speedController.active) {
            speedController.currentSpeed
        } else {
            player.deltaMovement.length().takeIf(Double::isFinite)?.coerceIn(0.0, limits.targetSpeed) ?: 0.0
        }
        return SpearKillSpeedProfile(initialSpeed, limits)
    }

    private fun beginMaceKillFallSafety(planned: MaceKillPlannedRoute): Boolean = beginMaceKillFallSafety(
        request = planned.request,
        groundPolicy = if (planned.clipReachPlan == null) {
            MaceKillGroundPolicy.COLLISION_DERIVED
        } else {
            MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF
        },
        vanillaVClipSegments = planned.vanillaVClipSegments,
    )

    private fun beginMaceKillFallSafety(
        request: RemoteKillRouteRequest,
        groundPolicy: MaceKillGroundPolicy = MaceKillGroundPolicy.COLLISION_DERIVED,
        vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
    ): Boolean {
        val originGrounded = isMaceKillPositionNearGround(request.origin)
        val finalPosition = request.returnMovements.fold(request.endpoint, Vec3::add)
        val routeReturnsExactly = finalPosition.distanceToSqr(request.origin) < MACE_KILL_EXACT_RETURN_EPSILON_SQUARED
        if (!canBeginMaceKillFallSafetyAtOrigin(originGrounded, routeReturnsExactly)) {
            debugMaceKill("fall-safety-reject") {
                listOf(
                    "reason" to "airborne-origin-without-exact-return",
                    "origin" to request.origin,
                    "player" to player.position(),
                )
            }
            return false
        }
        val movements = request.outboundMovements + request.returnMovements
        val steps = maceKillFallSafetySteps(request.origin, movements, groundPolicy, vanillaVClipSegments)
        val initialFallDistance = player.fallDistance
        val safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
        val preflight = preflightMaceKillFallSafety(
                initialFallDistance,
                safeFallDistance,
                steps,
            )
        if (preflight != MaceKillFallSafetyPreflight.Safe) {
            debugMaceKill("fall-safety-reject") {
                listOf(
                    "reason" to preflight,
                    "origin-grounded" to originGrounded,
                    "initial-fall-distance" to initialFallDistance,
                    "safe-fall-distance" to safeFallDistance,
                    "grounded-steps" to steps.map(MaceKillFallSafetyStep::grounded),
                )
            }
            return false
        }
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = movements,
            outboundStepCount = request.outboundMovements.size,
            initialFallDistance = initialFallDistance,
            safeFallDistance = safeFallDistance,
            groundedSteps = steps.map(MaceKillFallSafetyStep::grounded),
            expectedNetMovement = Vec3.ZERO,
        )
        val ready = result as? SpearKillServerFallSafetyPlanResult.Ready
        if (ready == null) {
            debugMaceKill("fall-safety-reject") {
                listOf(
                    "reason" to result,
                    "initial-fall-distance" to initialFallDistance,
                    "safe-fall-distance" to safeFallDistance,
                )
            }
            return false
        }
        fallSafetyLifecycle.begin(ready.plan)
        return true
    }

    private fun replanMaceKillFallSafety(
        start: Vec3,
        movements: List<Vec3>,
        outboundStepCount: Int,
        groundPolicy: MaceKillGroundPolicy = MaceKillGroundPolicy.COLLISION_DERIVED,
        vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = activeVanillaVClipSegments,
    ): Boolean {
        if (movements.isEmpty()) {
            fallSafetyLifecycle.invalidate()
            return true
        }
        val initialFallDistance = fallSafetyLifecycle.confirmedFallDistance.takeIf { fallSafetyLifecycle.active }
            ?: player.fallDistance
        val safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
        val steps = maceKillFallSafetySteps(start, movements, groundPolicy, vanillaVClipSegments)
        if (preflightMaceKillFallSafety(
                initialFallDistance,
                safeFallDistance,
                steps,
            ) != MaceKillFallSafetyPreflight.Safe
        ) {
            return false
        }
        val result = SpearKillServerFallSafetyPlan.createForMovements(
            movements = movements,
            outboundStepCount = outboundStepCount,
            initialFallDistance = initialFallDistance,
            safeFallDistance = safeFallDistance,
            groundedSteps = steps.map(MaceKillFallSafetyStep::grounded),
            expectedNetMovement = movements.fold(Vec3.ZERO, Vec3::add),
        ) as? SpearKillServerFallSafetyPlanResult.Ready ?: return false
        fallSafetyLifecycle.replan(result.plan)
        return true
    }

    private fun maceKillFallSafetySteps(
        start: Vec3,
        movements: List<Vec3>,
        groundPolicy: MaceKillGroundPolicy,
        vanillaVClipSegments: Set<MaceKillVanillaVClipSegment>,
    ): List<MaceKillFallSafetyStep> {
        var position = start
        val clipReachSpoofed = groundPolicy.shouldSpoofOnGround(
            MaceKillGroundPacketContext(
                identityOwnedByRoute = true,
                kind = MaceKillMovementPacketKind.CLIP_REACH_ANCHOR,
            ),
        )
        return movements.map { movement ->
            val previousPosition = position
            position = position.add(movement)
            val vanillaVClipSpoofed = vanillaVClipSegments.any { it.matches(previousPosition, position) }
            MaceKillFallSafetyStep(
                movement = movement,
                grounded = clipReachSpoofed || vanillaVClipSpoofed || isMaceKillPositionNearGround(position),
                groundSpoofed = clipReachSpoofed || vanillaVClipSpoofed,
            )
        }
    }

    private fun beginSafeRouteAbort() {
        if (!routeEngine.ownsMovement) return
        if (!routeSession.recovering) {
            val origin = routeOrigin
            val committedOffset = routeSession.committedOffset
            val exactRecovery = routeSession.exactRecoveryMovementsFrom(committedOffset)
            val recovery = exactRecovery?.let { movements ->
                if (activeClipReachSession == null) movements else maceKillSafeClipRecoveryMovements(movements)
            }
            if (origin != null && recovery != null) {
                if (!replanMaceKillFallSafety(
                        origin.add(committedOffset),
                        recovery,
                        outboundStepCount = 0,
                        groundPolicy = activeMaceKillGroundPolicy(),
                    )
                ) {
                    routeRejected = true
                    routeEngine.clear()
                    finishInactiveRouteOwnership()
                    return
                }
                if (activeClipReachSession != null) {
                    routeEngine.beginPacketExactRecoveryFrom(
                        committedOffset,
                        recovery,
                        routeStepWaitTicks,
                    )
                    return
                }
            } else {
                fallSafetyLifecycle.invalidate()
            }
        }
        routeEngine.abort()
    }

    private fun finishMaceKillFallSafety(): Boolean {
        val origin = routeOrigin
        return when (decideMaceKillFallSafetyFinish(
            lifecycle = fallSafetyLifecycle,
            finalPositionKnown = origin != null,
            connectionOpen = mc.connection?.connection?.isConnected == true,
            nearGround = origin?.let(::isMaceKillPositionNearGround) == true,
        )) {
            MaceKillFallSafetyFinishDecision.COMPLETE -> true
            MaceKillFallSafetyFinishDecision.WAIT_FOR_ROUTE_DELIVERY -> false
            MaceKillFallSafetyFinishDecision.RESET_LOCAL_FALL_DISTANCE -> {
                player.resetFallDistance()
                true
            }
            MaceKillFallSafetyFinishDecision.SEND_GROUNDING -> {
                if (origin == null) {
                    fallSafetyLifecycle.confirmGrounding(delivered = false)
                } else {
                    sendMaceKillGroundingPacket(origin)
                }
                false
            }
        }
    }

    private fun sendMaceKillGroundingPacket(position: Vec3): Boolean {
        if (!isMaceKillPositionNearGround(position)) {
            fallSafetyLifecycle.confirmGrounding(delivered = false)
            return false
        }
        val packet = ServerboundMovePlayerPacket.Pos(
            position.x,
            position.y,
            position.z,
            true,
            player.horizontalCollision,
        )
        groundingPacketTracker.protect(packet)
        network.send(packet)
        if (groundingPacketTracker.discard(packet)) {
            fallSafetyLifecycle.confirmGrounding(delivered = false)
            return false
        }
        return true
    }

    private fun networkSetbackBackoffTicks(): Int = activeRouteConfiguration?.timing?.setbackBackoffTicks ?: 0

    private fun hasUnsafeResearchMovementContext(): Boolean = !enabled || !inGame || ModuleBlink.running ||
        player.isPassenger || player.isFallFlying || player.isDeadOrDying || RemoteKillMovementOwnership.active

    @Suppress("LongMethod", "ReturnCount") // Probe admission must record or reject each terminal boundary.
    private fun startResearchProbe(request: MaceClipResearchProbeRequest): MaceClipResearchProbeStartResult {
        if (!enabled || !inGame || routeEngine.ownsMovement) return MaceClipResearchProbeStartResult.INVALID_CONTEXT
        val origin = player.position()
        val routeConfiguration = currentMaceKillRouteExecutionConfiguration(MaceKillRouteOwner.RESEARCH)
        val target = when (request) {
            is MaceClipResearchProbeRequest.Attack -> findLookRayTarget(clipReachResearch = true)
                ?: return MaceClipResearchProbeStartResult.NO_TARGET
            is MaceClipResearchProbeRequest.Move -> null
        }
        if (request is MaceClipResearchProbeRequest.Attack && (!hasServerHeldMace() || !isAttackCooldownReady())) {
            return MaceClipResearchProbeStartResult.INVALID_CONTEXT
        }
        val endpoint = when (request) {
            is MaceClipResearchProbeRequest.Move -> origin.add(player.lookAngle.normalize().scale(request.distance))
            is MaceClipResearchProbeRequest.Attack -> {
                val attackTarget = requireNotNull(target)
                val predicted = predictedMaceKillTarget(attackTarget, origin, routeConfiguration.timing)
                findMaceKillAttackEndpoint(
                    attackTarget,
                    origin,
                    predicted.position,
                    predicted.eyePosition,
                ) ?: return MaceClipResearchProbeStartResult.ROUTE_REJECTED
            }
        }
        val routeResult = MaceClipResearchRouteAdapter.plan(
            MaceClipResearchRouteRequest(
                request = request,
                origin = origin,
                endpoint = endpoint,
                dimensionBounds = MaceClipReachDimensionBounds(world.minY.toDouble(), world.maxY.toDouble()),
                anchorValidator = MaceClipReachAnchorValidator { _, position ->
                    isMaceKillAnchorValid(origin, position)
                },
            ),
        ) as? MaceClipResearchRouteResult.Ready ?: return MaceClipResearchProbeStartResult.ROUTE_REJECTED
        val descriptor = routeResult.descriptor
        val apex = descriptor.steps.first { it.phase == MaceClipResearchPhase.ASCEND }.position
        val begin = researchRuntime.begin(
            MaceClipResearchStart(
                clientTick = player.tickCount,
                request = request,
                profile = MaceClipResearchProfiles.PAPER_26_2_BUILD_112,
                packetBudget = descriptor.packetBudget,
                origin = origin,
                targetPosition = target?.position(),
                attackEndpoint = endpoint,
                apex = apex,
                localPositionBefore = origin,
                target = target?.let {
                    MaceClipResearchTargetStart(it.id, it.name.string, it.health.toDouble())
                },
            ),
        )
        val sessionId = (begin as? MaceClipResearchBeginResult.Started)?.sessionId ?: return when (
            (begin as MaceClipResearchBeginResult.Rejected).reason
        ) {
            MaceClipResearchBeginRejection.LOGGING_UNAVAILABLE -> MaceClipResearchProbeStartResult.LOGGING_UNAVAILABLE
            MaceClipResearchBeginRejection.ACTIVE_PROBE -> MaceClipResearchProbeStartResult.ACTIVE_PROBE
            else -> MaceClipResearchProbeStartResult.ROUTE_REJECTED
        }
        val execution = MaceKillResearchExecution(
            sessionId = sessionId,
            descriptor = descriptor,
            target = target,
            startedTick = player.tickCount,
            deadlineTick = player.tickCount + descriptor.timeoutTicks,
            lastTargetHealth = target?.health?.toDouble(),
        )
        researchExecution = execution
        activeRouteOwner = MaceKillRouteOwner.RESEARCH
        activeRouteTarget = target ?: player
        routeOrigin = origin
        routeRenderPath = routePositions(origin, descriptor.outboundDeltas)
        routeStepWaitTicks = descriptor.phaseDelayTicks
        routeRejected = false
        activeRouteConfiguration = routeConfiguration.copy(
            timing = MaceKillRouteTiming(
                transport = MaceKillRouteTransport.PACKET,
                stepDistance = routeConfiguration.timing.stepDistance,
                stepWaitTicks = descriptor.phaseDelayTicks,
                maxPacketsPerTick = 1,
                setbackBackoffTicks = routeConfiguration.timing.setbackBackoffTicks,
            ),
        )
        localPacketRouteOrigin = origin
        routeDeadlineTick = execution.deadlineTick
        returnConfirmation.clear()
        speedController.reset()
        val researchRouteRequest = RemoteKillRouteRequest(
            origin = origin,
            outboundMovements = descriptor.outboundDeltas,
            strikeHoldTicks = descriptor.terminalHoldTicks,
            stepWaitTicks = descriptor.phaseDelayTicks,
        )
        if (!beginMaceKillFallSafety(researchRouteRequest)) {
            researchRuntime.complete(sessionId, player.tickCount, player.position(), exactReturnDelivered = false)
            researchExecution = null
            clearRouteOwnership(rejected = true)
            return MaceClipResearchProbeStartResult.ROUTE_REJECTED
        }
        return runCatching {
            routeEngine.start(
                target ?: player,
                researchRouteRequest,
            )
            researchRuntime.recordPhaseStarted(
                sessionId,
                MaceClipResearchPhase.PRIME,
                player.tickCount,
                origin,
            )
            if (descriptor.primingPackets == 0) {
                researchRuntime.recordPhaseCompleted(
                    sessionId,
                    MaceClipResearchPhase.PRIME,
                    player.tickCount,
                    origin,
                )
            }
            if (!sendMaceKillPrimingPackets(origin, descriptor.primingPackets)) {
                routeRejected = true
                beginSafeRouteAbort()
                MaceClipResearchProbeStartResult.ROUTE_REJECTED
            } else {
                MaceClipResearchProbeStartResult.STARTED
            }
        }.getOrElse {
            routeEngine.clear()
            researchRuntime.complete(sessionId, player.tickCount, player.position(), exactReturnDelivered = false)
            researchExecution = null
            clearRouteOwnership(rejected = true)
            MaceClipResearchProbeStartResult.ROUTE_REJECTED
        }
    }

    private fun abortResearchProbe(): MaceClipResearchAbortResult {
        val result = researchRuntime.requestAbort()
        if (result == MaceClipResearchAbortResult.ABORT_REQUESTED) {
            researchExecution?.abortRequested = true
            if (routeEngine.ownsMovement) beginSafeRouteAbort()
        }
        return result
    }

    private fun finishResearchProbeWhenReady(execution: MaceKillResearchExecution) {
        if (execution.completionDeadlineTick == null) {
            execution.exactReturnDelivered = execution.returnDelivered == execution.outboundDelivered &&
                player.position().distanceToSqr(requireNotNull(routeOrigin)) < MACE_KILL_EXACT_RETURN_EPSILON_SQUARED
            execution.completionDeadlineTick = player.tickCount + if (
                execution.descriptor.request is MaceClipResearchProbeRequest.Attack
            ) {
                MACE_KILL_RESEARCH_EVIDENCE_TICKS
            } else {
                0
            }
        }
        if (player.tickCount < requireNotNull(execution.completionDeadlineTick)) return
        researchRuntime.complete(
            execution.sessionId,
            player.tickCount,
            player.position(),
            execution.exactReturnDelivered,
        )
        researchExecution = null
        finishMaceKillFallSafety()
        clearRouteOwnership(rejected = routeRejected)
    }

    private fun updateResearchEvidence() {
        val execution = researchExecution ?: return
        val target = execution.target
        if (target != null) {
            val health = target.health.toDouble()
            val previous = execution.lastTargetHealth
            if (previous != null && health < previous) {
                researchRuntime.recordDamage(execution.sessionId, health, previous - health)
            }
            execution.lastTargetHealth = health
            if (!target.isAlive || target.isRemoved) researchRuntime.recordDeath(execution.sessionId)
        }
        if (player.tickCount >= execution.deadlineTick && routeEngine.ownsMovement) {
            execution.abortRequested = true
            researchRuntime.requestAbort()
            beginSafeRouteAbort()
        }
    }

    private fun ownsClipReachAnchorPackets(): Boolean = activeClipReachSession != null && routeEngine.ownsMovement

    private fun ownsClipReachRecoveryPackets(): Boolean = instantCorrectionRecoveryActive && routeEngine.ownsMovement

    private fun ownsVanillaVClipPendingStep(): Boolean {
        val origin = routeOrigin ?: return false
        if (routeSession.pendingMovement == null) return false
        val from = origin.add(routeSession.committedOffset)
        val to = origin.add(routeSession.virtualOffset)
        return activeVanillaVClipSegments.any { it.matches(from, to) }
    }

    private fun activeMaceKillGroundPolicy(): MaceKillGroundPolicy = if (
        !ownsClipReachAnchorPackets() && !ownsClipReachRecoveryPackets()
    ) {
        MaceKillGroundPolicy.COLLISION_DERIVED
    } else {
        MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF
    }

    private fun activeMaceKillPacketKind(): MaceKillMovementPacketKind = when {
        ownsVanillaVClipPendingStep() -> MaceKillMovementPacketKind.VANILLA_VCLIP
        ownsClipReachRecoveryPackets() -> MaceKillMovementPacketKind.CLIP_REACH_RECOVERY
        ownsClipReachAnchorPackets() -> MaceKillMovementPacketKind.CLIP_REACH_ANCHOR
        activeRouteConfiguration?.routingMode == MaceKillRoutingMode.A_STAR ->
            MaceKillMovementPacketKind.ASTAR_ROUTE
        else -> MaceKillMovementPacketKind.DIRECT_ROUTE
    }

    private fun maceKillRoutePacketGrounded(
        position: Vec3,
        identityOwnedByRoute: Boolean,
    ): Boolean {
        val packet = MaceKillGroundPacketContext(identityOwnedByRoute, activeMaceKillPacketKind())
        return shouldSpoofMaceKillVanillaVClipGround(packet) ||
            activeMaceKillGroundPolicy().shouldSpoofOnGround(packet) ||
            isMaceKillPositionNearGround(position)
    }

    private fun createMaceKillMovementPacket(
        position: Vec3,
        onGround: Boolean,
    ): ServerboundMovePlayerPacket {
        val shape = researchExecution?.descriptor?.packetShape
        return when (shape) {
            MaceClipResearchPacketShape.POSITION_ROTATION -> ServerboundMovePlayerPacket.PosRot(
                position.x,
                position.y,
                position.z,
                player.yRot,
                player.xRot,
                onGround,
                player.horizontalCollision,
            )
            else -> ServerboundMovePlayerPacket.Pos(
                position.x,
                position.y,
                position.z,
                onGround,
                player.horizontalCollision,
            )
        }
    }

    private fun attachResearchPacketContext(packet: ServerboundMovePlayerPacket, position: Vec3) {
        val execution = researchExecution ?: return
        val outbound = routeSession.pendingOutboundStep
        val movementIndex = if (outbound) execution.outboundDelivered else execution.returnDelivered
        val phase = execution.descriptor.phaseForMovement(outbound, movementIndex)
            ?: if (outbound) MaceClipResearchPhase.DESCEND else MaceClipResearchPhase.RETURN_DESCEND
        val context = MaceKillResearchPacketContext(
            sequence = execution.nextPacketSequence++,
            phase = phase,
            position = position,
            outbound = outbound,
        )
        researchPacketContexts[packet] = context
        val phaseStart = routeOrigin?.add(routeSession.committedOffset) ?: player.position()
        researchRuntime.recordPhaseStarted(execution.sessionId, phase, player.tickCount, phaseStart)
    }

    private fun recordResearchPacketDelivery(
        packet: ServerboundMovePlayerPacket,
        delivered: Boolean,
        queuedByBlink: Boolean,
    ) {
        val execution = researchExecution ?: return
        val context = researchPacketContexts.remove(packet) ?: return
        val delivery = when {
            queuedByBlink -> MaceClipResearchPacketDelivery.QUEUED
            delivered -> MaceClipResearchPacketDelivery.DELIVERED
            else -> MaceClipResearchPacketDelivery.CANCELLED
        }
        researchRuntime.recordPacket(
            execution.sessionId,
            context.phase,
            context.sequence,
            player.tickCount,
            context.position,
            packet.onGround,
            delivery,
        )
        if (context.phase == MaceClipResearchPhase.PRIME) {
            execution.primingResolved++
            if (execution.primingResolved == execution.descriptor.primingPackets) {
                researchRuntime.recordPhaseCompleted(
                    execution.sessionId,
                    context.phase,
                    player.tickCount,
                    context.position,
                )
            }
            return
        }
        if (!delivered) return
        researchRuntime.recordPhaseCompleted(
            execution.sessionId,
            context.phase,
            player.tickCount,
            context.position,
        )
        if (context.outbound == true) execution.outboundDelivered++
        if (context.outbound == false) execution.returnDelivered++
    }

    private fun currentResearchPhase(): MaceClipResearchPhase? = researchExecution?.let { execution ->
        when {
            routeSession.pendingOutboundStep -> execution.descriptor.phaseForMovement(
                outbound = true,
                index = execution.outboundDelivered,
            )
            routeSession.recovering -> execution.descriptor.phaseForMovement(
                outbound = false,
                index = execution.returnDelivered,
            )
            else -> MaceClipResearchPhase.PRIME
        }
    }

    private fun debugMaceKill(event: String, fields: () -> List<Pair<String, Any?>>) {
        if (!ModuleDebug.running) {
            if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()
            return
        }
        debugConsole.value.log(event, fields)
    }

    private fun debugMaceKillChanged(
        channel: String,
        event: String,
        fingerprint: () -> Any?,
        fields: () -> List<Pair<String, Any?>>,
    ) {
        if (!ModuleDebug.running) {
            if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()
            return
        }
        debugConsole.value.logChanged(channel, event, fingerprint, fields)
    }

    private fun notifyMaceFailure(key: String) {
        if (!failureNotificationGate.shouldNotify(player.tickCount)) return
        notification(name, message(key), NotificationEvent.Severity.ERROR)
    }

    private fun routePositions(origin: Vec3, movements: List<Vec3>): List<Vec3> = buildList {
        var position = origin
        add(position)
        movements.forEach { movement ->
            position = position.add(movement)
            add(position)
        }
    }

    private fun isOrdinaryMeleeAvailable(target: LivingEntity): Boolean =
        player.boundingBox.distanceToSqr(target.eyePosition) <= MACE_KILL_ATTACK_RANGE_SQUARED &&
            hasLineOfSight(player.eyePosition, target.eyePosition, player)

    private fun isMaceKillAnchorValid(
        origin: Vec3,
        position: Vec3,
        originBoundingBox: AABB = player.boundingBox,
    ): Boolean {
        val box = originBoundingBox.move(position.subtract(origin))
        return world.worldBorder.isWithinBounds(box) && withVanillaSpearKillBlockShapes {
            world.noCollision(player, box)
        }
    }

    private fun routePacketPosition(packet: ServerboundMovePlayerPacket): Vec3 = Vec3(
        packet.getX(player.x),
        packet.getY(player.y),
        packet.getZ(player.z),
    )

    private fun isMaceKillPositionNearGround(position: Vec3): Boolean {
        val box = player.boundingBox.move(position.subtract(player.position()))
        return withVanillaSpearKillBlockShapes {
            !world.noCollision(player, box.move(0.0, -MACE_KILL_GROUND_PROBE_DEPTH, 0.0))
        }
    }

    private fun stopKillAuraBlockingBeforeRoute() {
        if (player.isUsingItem && KillAuraAutoBlock.enforcedBlockingHand != null) {
            KillAuraAutoBlock.stopBlocking(pauses = true)
        }
    }

    private fun isRemoteEndpointReady(
        localPlayer: LocalPlayer,
        target: Entity,
        endpoint: Vec3,
        targetEyePosition: Vec3 = target.eyePosition,
        requireAttackCooldown: Boolean = true,
    ): Boolean {
        val livingTarget = target as? LivingEntity ?: return false
        val endpointBox = localPlayer.boundingBox.move(endpoint.subtract(localPlayer.position()))
        val endpointEyes = endpoint.add(0.0, localPlayer.eyeHeight.toDouble(), 0.0)
        return isMaceKillEndpointReady(
            holdingMace = hasServerHeldMace(),
            bodySpaceClear = world.getBlockCollisions(localPlayer, endpointBox).allEmpty(),
            attackRayClear = endpointBox.distanceToSqr(targetEyePosition) <= MACE_KILL_ATTACK_RANGE_SQUARED &&
                hasLineOfSight(endpointEyes, targetEyePosition, localPlayer),
            cooldownReady = !requireAttackCooldown || isAttackCooldownReady(),
            usableFallHeight = determineUsableFallHeight(endpointBox),
        )
    }

    private fun determineUsableFallHeight(endpointBox: AABB): Int = (fallHeight downTo 1).firstOrNull { height ->
        world.getBlockCollisions(player, endpointBox.move(0.0, height.toDouble(), 0.0)).allEmpty()
    } ?: 0

    private fun currentPreviewGlow(): TargetGlowSelection? {
        if (!enabled || !Preview.enabled || Preview.mode.activeMode !== Preview.Glow ||
            !hasServerHeldMace()
        ) {
            return null
        }
        val target = previewTarget ?: return null
        return TargetGlowSelection(target, Preview.Glow.glowColor, Preview.Glow.glowStyle.style)
    }

    @Suppress("ReturnCount") // Target ownership sources are checked in strict priority order.
    private fun findSelectedTarget(): LivingEntity? {
        activeRouteTarget?.takeIf(::isMaceKillTargetEligible)?.let { return it }
        fightBotMaceTarget?.takeIf(::isMaceKillTargetEligible)?.let { return it }
        val killAuraTarget = ModuleKillAura.targetForMaceKill()?.takeIf(::isMaceKillTargetEligible)

        return selectMaceKillDelegatedTarget(acceptsKillAuraDelegation, killAuraTarget) {
            if (!hasServerHeldMace()) return@selectMaceKillDelegatedTarget null
            selectMaceKillTargetForSource(
                targetSource = targetSource,
                lookRayTarget = ::findLookRayTarget,
                combatTarget = ::findCombatTarget,
            )
        }
    }

    private fun findLookRayTarget(clipReachResearch: Boolean = false): LivingEntity? {
        val eye = player.eyePosition
        val lookEnd = eye.add(player.lookAngle.normalize().scale(maximumTargetRange.toDouble()))
        val routing = movementConfiguration.packet.routing.activeMode
        val throughTerrain = shouldMaceKillLookRayIgnoreTerrain(
            packetMovement = movementConfiguration.choice.activeMode === movementConfiguration.packet,
            aStarRouting = routing === movementConfiguration.packet.aStar,
            instantRouting = routing === movementConfiguration.packet.instant,
            clipReachResearch = clipReachResearch,
        )
        var best: Pair<LivingEntity, SpearKillLookRayPriority>? = null

        for (entity in world.getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(maximumTargetRange.toDouble() + spearKillTargetSelectionMargin()),
            ::isMaceKillTargetEligible,
        )) {
            val priority = spearKillLookRayPriority(entity.box, eye, lookEnd) ?: continue
            if (!throughTerrain && !hasLineOfSight(eye, entity.eyePosition, player)) continue
            val previous = best?.second
            if (previous == null || compareSpearKillLookRayPriority(priority, previous, throughTerrain) < 0) {
                best = entity to priority
            }
        }
        return best?.first
    }

    private fun findCombatTarget(): LivingEntity? {
        val origin = player.position()
        val timing = currentMaceKillRouteExecutionConfiguration(MaceKillRouteOwner.MANUAL).timing
        val candidates = world.getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(maximumTargetRange.toDouble() + spearKillTargetSelectionMargin()),
            ::isMaceKillTargetEligible,
        ).map { target ->
            MaceKillCombatTargetCandidate(
                target = target,
                distance = player.distanceTo(target).toDouble(),
                crosshairAngle = RotationUtil.crosshairAngleToEntity(target),
            )
        }

        return selectMaceKillCombatTarget(
            candidates = candidates,
            retainedTarget = previewTarget,
            hasAttackEndpoint = { target ->
                val predicted = predictedMaceKillTarget(target, origin, timing)
                findMaceKillAttackEndpoint(
                    target = target,
                    origin = origin,
                    targetPosition = predicted.position,
                    targetEyePosition = predicted.eyePosition,
                    requireAttackCooldown = false,
                ) != null
            },
        )
    }

    private fun isMaceKillTargetEligible(target: LivingEntity): Boolean = isMaceKillTargetCandidateEligible(
        isCombatSafe = target.shouldBeAttacked(),
        isAlive = target.isAlive && !target.isRemoved,
        isInCurrentWorld = target.level() === world,
        isWithinRange = player.distanceTo(target) in MACE_KILL_MIN_TARGET_DISTANCE..maximumTargetRange,
        isRejected = rejectedTargets.isRejected(target, player.tickCount),
        isInWater = target.isInWater || target.isSwimming || target.isUnderWater,
    )

    private fun canPrepareFightBotMaceUse(target: LivingEntity): Boolean = enabled && running &&
        acceptsKillAuraDelegation && ModuleFightBot.configuredMaceAutomation != FightBotMaceAutomation.Off &&
        (activeRouteTarget == null || activeRouteTarget === target) && isMaceKillTargetEligible(target)

    private fun resolveFightBotMaceUseSource(): FightBotMaceUseSource? = selectFightBotMaceUseSource(
        automation = ModuleFightBot.configuredMaceAutomation,
        mainHandMace = player.mainHandItem.item == Items.MACE,
        selectedHotbarSlot = SilentHotbar.serversideSlot,
        hotbarMaceSlots = Slots.Hotbar.asSequence()
            .filter { it.itemStack.item == Items.MACE }
            .mapNotNull { it.hotbarIndex }
            .toList(),
    )

    private fun rejectFightBotMaceUse(target: LivingEntity): MaceKillFightBotState {
        if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
            fightBotMaceState = MaceKillFightBotState.Rejected
            beginFightBotTerminal(MaceKillFightBotTerminal.Rejection)
            return fightBotMaceState
        }
        clearFightBotMaceUse(MaceKillFightBotTerminal.Rejection)
        fightBotMaceTarget = target
        fightBotMaceState = MaceKillFightBotState.Rejected
        return fightBotMaceState
    }

    private fun clearFightBotMaceUse(terminal: MaceKillFightBotTerminal) {
        val cleanup = fightBotMaceCleanup(terminal, fightBotMaceSource)
        if (cleanup.resetSilentSlot) SilentHotbar.resetSlot(FightBotMaceUseRequester)
        fightBotMaceTarget = null
        fightBotMaceState = MaceKillFightBotState.Unavailable
        fightBotMaceSource = null
        pendingFightBotTerminal = null
    }

    private fun beginFightBotTerminal(terminal: MaceKillFightBotTerminal) {
        pendingFightBotTerminal = terminal
        if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT && routeEngine.ownsMovement) {
            beginSafeRouteAbort()
            return
        }
        clearFightBotMaceUse(terminal)
    }

    private fun hasServerHeldMace(): Boolean = player.mainHandItem.item == Items.MACE ||
        (fightBotMaceSource as? FightBotMaceUseSource.Hotbar)?.let { source ->
            SilentHotbar.serversideSlot == source.slot && isMaceInHotbarSlot(source.slot)
        } == true

    private fun isMaceInHotbarSlot(slot: Int): Boolean = Slots.Hotbar.asSequence()
        .firstOrNull { it.hotbarIndex == slot }
        ?.itemStack
        ?.item == Items.MACE

    private fun isAttackCooldownReady(): Boolean = player.getAttackStrengthScale(0.5f) >= MACE_KILL_MIN_ATTACK_STRENGTH

    private fun isInstantPacketRoutingConfigured(): Boolean =
        movementConfiguration.choice.activeMode === movementConfiguration.packet &&
            movementConfiguration.packet.routing.activeMode === movementConfiguration.packet.instant

    private fun abortRemoteRoute() {
        if (routeEngine.ownsMovement) {
            beginSafeRouteAbort()
        } else {
            finishInactiveRouteOwnership()
        }
    }

    private fun clearRuntime(terminal: MaceKillFightBotTerminal) {
        previewTarget = null
        evidenceTargetId = null
        evidenceDeadlineTick = 0
        holdAttackState = MaceKillHoldAttackState.IDLE
        correctionState = null
        correctionRecoveryAttempts = 0
        routeAdmissionBackoff.clear()
        instantRouteBackoff.clear()
        if (terminal != MaceKillFightBotTerminal.Death) instantServerRejected = false
        rejectedTargets.clear()
        if (terminal == MaceKillFightBotTerminal.Disable && routeEngine.ownsMovement) {
            when (maceKillDisableRouteAction(routeSession.active, routeEngine.awaitingStrike)) {
                MaceKillDisableRouteAction.RELEASE_COMPLETED -> routeEngine.releaseCompletedOwnership()
                MaceKillDisableRouteAction.BEGIN_SAFE_ABORT -> {
                    if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) pendingFightBotTerminal = terminal
                    beginSafeRouteAbort()
                    return
                }
            }
        }
        routeEngine.clear()
        researchExecution?.let { execution ->
            researchRuntime.complete(
                execution.sessionId,
                player.tickCount,
                player.position(),
                exactReturnDelivered = false,
            )
        }
        researchExecution = null
        clearRouteOwnership()
        clearFightBotMaceUse(terminal)
    }

    override val running: Boolean
        get() = super.running || routeEngine.ownsMovement || researchExecution != null

    override fun onDisabled() {
        clearRuntime(MaceKillFightBotTerminal.Disable)
        failureNotificationGate.clear()
        if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()
        super.onDisabled()
    }
}

internal enum class MaceKillRouteOwner {
    NONE,
    MANUAL,
    KILL_AURA,
    FIGHT_BOT,
    RESEARCH,
}

internal enum class MaceKillRouteTransport {
    MOTION,
    PACKET,
}

internal fun selectMaceKillRouteTransport(
    configuredMotion: Boolean,
    owner: MaceKillRouteOwner,
): MaceKillRouteTransport = if (
    configuredMotion && owner != MaceKillRouteOwner.KILL_AURA && owner != MaceKillRouteOwner.RESEARCH
) {
    MaceKillRouteTransport.MOTION
} else {
    MaceKillRouteTransport.PACKET
}

internal val MaceKillRouteOwner.allowsTargetChain: Boolean
    get() = this == MaceKillRouteOwner.MANUAL

internal fun shouldDeferMaceKillStrike(currentTick: Int, earliestStrikeTick: Int): Boolean =
    earliestStrikeTick != 0 && currentTick < earliestStrikeTick

/**
 * Separates the route endpoint from the height spoof by one complete 20 Hz server interval.
 * A single client tick can still share a Paper tick with the endpoint packet, which makes the
 * horizontal route delta consume part of the instant-mace priming budget.
 */
internal fun maceKillRemoteStrikeEarliestTick(
    confirmedEndpointTick: Int,
    instantClip: Boolean = false,
): Int = if (instantClip) {
    0
} else {
    (confirmedEndpointTick.toLong() + MACE_KILL_REMOTE_STRIKE_SERVER_TICK_GUARD)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private const val MACE_KILL_MIN_TARGET_DISTANCE = 3f
private const val MACE_KILL_ATTACK_RANGE_SQUARED = 4.5 * 4.5
private const val MACE_KILL_ENDPOINT_MAX_SEARCH_RADIUS = 3.6
private const val MACE_KILL_MIN_ATTACK_STRENGTH = 0.9f
private const val MACE_KILL_GROUND_PROBE_DEPTH = 0.05
private const val MACE_KILL_MAX_ROUTE_STALL_TICKS = 20
private const val MACE_KILL_DAMAGE_EVIDENCE_TICKS = 10
private const val MACE_KILL_RESEARCH_EVIDENCE_TICKS = 10
private const val MACE_KILL_CHAIN_EVIDENCE_HOLD_TICKS = 2
private const val MACE_KILL_REMOTE_STRIKE_SERVER_TICK_GUARD = 2
private const val MACE_KILL_MAX_CHAIN_TARGETS = 8
private const val MACE_KILL_TARGET_REPLAN_DISTANCE_SQUARED = 0.25
private const val MACE_KILL_MOVEMENT_EPSILON_SQUARED = 1.0E-12
private const val MACE_KILL_EXACT_RETURN_EPSILON_SQUARED = 1.0E-8
private const val MACE_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS = 40
private const val MACE_KILL_INSTANT_TIMEOUT_TICKS = 40
private const val MACE_KILL_INSTANT_FAILURE_BACKOFF_TICKS = 40
private const val MACE_KILL_REJECTED_TARGET_RETRY_TICKS = 40
private const val MACE_KILL_MAX_GROUND_SPOOF_DESCENT = 3.0

internal data class MaceKillPlannedRoute(
    val request: RemoteKillRouteRequest,
    val renderPath: List<Vec3>,
    val primingPackets: Int = 0,
    val returnPrimingPackets: Int = 0,
    val motion: Boolean = false,
    val vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
    val clipReachPlan: MaceClipReachPlan? = null,
)

internal fun maceKillInstantPlannedRoute(
    plan: MaceClipReachPlan,
    stepWaitTicks: Int,
): MaceKillPlannedRoute = MaceKillPlannedRoute(
    request = RemoteKillRouteRequest(
        origin = plan.origin,
        outboundMovements = plan.outboundMovements,
        strikeHoldTicks = 0,
        stepWaitTicks = stepWaitTicks,
        returnMovements = plan.returnMovements,
    ),
    renderPath = plan.outboundMovements.runningFold(plan.origin, Vec3::add),
    primingPackets = plan.profile.parameters.primingPacketCount,
    returnPrimingPackets = plan.profile.parameters.primingPacketCount,
    clipReachPlan = plan,
)

internal fun maceKillInstantRoundTripPacketCount(plan: MaceClipReachPlan): Int =
    plan.requiredMovementPackets + plan.profile.parameters.primingPacketCount

internal fun maceKillInstantPacketsPerTick(stepDelayTicks: Int, packetBudget: Int): Int {
    require(stepDelayTicks >= 0) { "Instant step delay must not be negative" }
    require(packetBudget > 0) { "Instant packet budget must be positive" }
    return if (stepDelayTicks == 0) packetBudget else 1
}

internal fun maceKillSafeClipRecoveryMovements(movements: List<Vec3>): List<Vec3> = movements.flatMap { movement ->
    if (movement.x != 0.0 || movement.z != 0.0 || movement.y >= -MACE_KILL_MAX_GROUND_SPOOF_DESCENT) {
        return@flatMap listOf(movement)
    }
    var remaining = -movement.y
    buildList {
        while (remaining > MACE_KILL_MOVEMENT_EPSILON_SQUARED) {
            val distance = minOf(MACE_KILL_MAX_GROUND_SPOOF_DESCENT, remaining)
            add(Vec3(0.0, -distance, 0.0))
            remaining -= distance
        }
    }
}

internal data class MaceKillInstantTerminalDecision(
    val abortRoute: Boolean,
    val rejectAttempt: Boolean,
    val backoffTicks: Int,
    val notificationKey: String?,
    val strikeCommitted: Boolean,
)

internal data class MaceKillInstantPlanRejectionDecision(
    val applyGlobalBackoff: Boolean,
    val notificationKey: String,
)

/** A geometry failure is target-local; global backoff is reserved for active-session failures. */
internal fun maceKillInstantPlanRejectionDecision(
    reason: MaceClipReachBlockReason,
): MaceKillInstantPlanRejectionDecision = MaceKillInstantPlanRejectionDecision(
    applyGlobalBackoff = false,
    notificationKey = if (reason == MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED) {
        "instantPacketBudgetExceeded"
    } else {
        "routeRejected"
    },
)

internal fun maceKillInstantTerminalDecision(
    outcome: MaceClipReachSessionOutcome,
    strikeCommitted: Boolean,
): MaceKillInstantTerminalDecision {
    val notificationKey = when (outcome) {
        MaceClipReachSessionOutcome.CORRECTED -> "instantCorrected"
        MaceClipReachSessionOutcome.TIMED_OUT -> "instantTimedOut"
        MaceClipReachSessionOutcome.TARGET_LOST -> "instantTargetLost"
        MaceClipReachSessionOutcome.REPLAN_REJECTED -> "instantReplanRejected"
        MaceClipReachSessionOutcome.ACTIVE,
        MaceClipReachSessionOutcome.COMPLETED,
        -> null
    }
    val rejected = notificationKey != null
    return MaceKillInstantTerminalDecision(
        abortRoute = rejected,
        rejectAttempt = rejected,
        backoffTicks = if (rejected) MACE_KILL_INSTANT_FAILURE_BACKOFF_TICKS else 0,
        notificationKey = notificationKey,
        strikeCommitted = strikeCommitted,
    )
}

private data class MaceKillLocalCorrectionState(
    val expectedPosition: Vec3,
    val routeOrigin: Vec3,
    val researchPhase: MaceClipResearchPhase?,
)

private data class MaceKillResearchPacketContext(
    val sequence: Int,
    val phase: MaceClipResearchPhase,
    val position: Vec3,
    val outbound: Boolean?,
)

private data class MaceKillResearchExecution(
    val sessionId: String,
    val descriptor: MaceClipResearchExecutionDescriptor,
    val target: LivingEntity?,
    val startedTick: Int,
    val deadlineTick: Int,
    var nextPacketSequence: Int = 0,
    var primingResolved: Int = 0,
    var outboundDelivered: Int = 0,
    var returnDelivered: Int = 0,
    var abortRequested: Boolean = false,
    var exactReturnDelivered: Boolean = false,
    var completionDeadlineTick: Int? = null,
    var lastTargetHealth: Double? = null,
)
