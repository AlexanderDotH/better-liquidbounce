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
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.OBJECTION_AGAINST_EVERYTHING
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
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
 */
@Suppress("TooManyFunctions", "LargeClass")
object ModuleSpearKill : ClientModule("SpearKill", ModuleCategories.COMBAT, aliases = listOf("AutoSpear")) {

    private val maxTargetDistance by float("MaxTargetDistance", 500f, 3f..500f)
    private val maxAllowedSpeedValue = float(
        "MaxSpeed",
        SPEAR_KILL_NORMAL_MAX_SPEED,
        SPEAR_KILL_MIN_SPEED..SPEAR_KILL_NORMAL_MAX_SPEED,
        "blocks/tick",
    ).onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED, SPEAR_KILL_NORMAL_MAX_SPEED) }
    private val maxAllowedSpeed by maxAllowedSpeedValue
    private val movementConfiguration = SpearKillMovementConfiguration(this)
    private val movement = tree(movementConfiguration.choice)

    private object Preview : ToggleableValueGroup(this, "Preview", true) {
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
    private val packetBootSession = SpearKillPacketBootSession()
    private val physicalReturnPositioner = SpearKillPhysicalReturnPositioner()
    private val setbackGuard = SpearKillSetbackGuard()
    private val setbackRollback = SpearKillSetbackRollback()
    private val fallDamageDeliveryTracker = SpearKillFallDamagePacketTracker()
    private val virtualSessionPackets = Collections.newSetFromMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Boolean>(),
    )
    private var previewTarget: LivingEntity? = null
    private var rejectedAStarTarget: LivingEntity? = null
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
    private var motionPacketHeading: Rotation? = null
    private var packetRecoveryStallTicks = 0

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
        val transport: SpearKillPacketTransport,
        val stepWaitTicks: Int,
    )

    private enum class PacketFollowTermination(
        val rejectTarget: Boolean,
        val notificationKey: String?,
    ) {
        DEFEATED(rejectTarget = false, notificationKey = null),
        UNREACHABLE(rejectTarget = true, notificationKey = "targetUnreachable"),
        BLOCKED(rejectTarget = true, notificationKey = "pathBlocked"),
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
            attackRequested = hasAttackRequest,
            isUsingSpear = isUsingSpear,
        )
    private val usesPacketMovementMode get() = movement.activeMode === movementConfiguration.packet
    private val usesPacketAStar get() = usesPacketMovementMode && movementConfiguration.packet.aStar.enabled
    private val effectiveStepLimit
        get() = packetSessionSettings?.transport?.stepLimit ?: configuredEffectiveStepLimit
    private val configuredEffectiveStepLimit
        get() = if (usesPacketMovementMode) {
            resolveSpearKillPacketSettings().transport.stepLimit
        } else {
            effectiveSpearKillStepLimit(
                maxSpeed = maxAllowedSpeed.toDouble(),
                stepLimit = activeStepLimit.toDouble(),
                packetMode = false,
            )
        }
    private val activePacketStepWaitTicks
        get() = packetSessionSettings?.stepWaitTicks ?: movementConfiguration.packet.waitTicks
    private val activeStepLimit
        get() = if (usesPacketMovementMode) {
            movementConfiguration.packet.stepLimit
        } else {
            movementConfiguration.motion.stepLimit
        }

    private fun resolveSpearKillPacketSettings(): SpearKillPacketSessionSettings {
        val packet = movementConfiguration.packet
        return SpearKillPacketSessionSettings(
            transport = resolveSpearKillPacketTransport(
                elytraEnabled = packet.elytra.enabled,
                elytraReady = canStartSpearKillElytraFlight(),
                normalMaxSpeed = maxAllowedSpeed.toDouble(),
                elytraMaxSpeed = packet.elytra.maxSpeed.toDouble(),
                configuredStepLimit = packet.stepLimit.toDouble(),
            ),
            stepWaitTicks = packet.waitTicks,
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

    private fun requestSpearKillElytraFlight(settings: SpearKillPacketSessionSettings) {
        if (!settings.transport.elytra || player.isFallFlying) return

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

    private val isUsingSpear get() = player.isUsingItem && player.useItem.isSpear
    private val holdingSpear get() = player.mainHandItem.isSpear || player.offhandItem.isSpear
    private val hasAttackRequest get() = isSpearKillAttackRequested(
        attackKeyDown = mc.options.keyAttack.isDown,
        attackPressedRecently = mc.options.keyAttack.wasPressedRecently(SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS),
    )

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

    private fun resetAttack() {
        val retainAStarRenderPath = packetAStarAttackActive && packetBootSession.active
        previewTarget = null
        if (!retainAStarRenderPath) {
            rejectedAStarTarget = null
            packetAStarAttackActive = false
            clearAStarRenderPath()
            clearAStarTargetLock()
        }
        if (attackMovements.isNotEmpty()) player.deltaMovement = Vec3.ZERO
        attackMovements.clear()
        motionPacketHeading = null
        fallDamageDeliveryTracker.clear()
        packetBootSession.beginExactReturn()
        applyConfirmedPhysicalReturnPosition()
        if (!packetBootSession.active) {
            packetSessionSettings = null
        }
    }

    private fun clearVirtualAttack() {
        val abortSnap = spearKillSessionAbortSnapPosition(
            sessionOrigin = packetSessionOrigin,
            committedOffset = packetBootSession.committedOffset,
            physicalReturnConfigured = packetBootSession.physicalReturnConfigured,
        )
        previewTarget = null
        rejectedAStarTarget = null
        packetAStarAttackActive = false
        clearAStarRenderPath()
        attackMovements.clear()
        motionPacketHeading = null
        BlinkManager.packetQueue.removeIf { snapshot ->
            val packet = snapshot.packet
            packet === plannedPacket || packet is ServerboundMovePlayerPacket && packet in virtualSessionPackets
        }
        virtualSessionPackets.clear()
        fallDamageDeliveryTracker.clear()
        plannedPacket = null
        awaitingVanillaMovementPacket = false
        packetSessionOrigin = null
        packetSessionSettings = null
        physicalReturnPositioner.clear()
        packetBootSession.clear()
        packetRecoveryStallTicks = 0
        clearAStarTargetLock()
        abortSnap?.let { origin ->
            player.setPos(origin)
            player.deltaMovement = Vec3.ZERO
        }
    }

    private fun clearAStarRenderPath() {
        plannedAStarRenderPath = emptyList()
    }

    private fun clearAStarTargetLock() {
        lockedAStarTarget = null
        plannedAStarApproach = null
        plannedAStarTargetPosition = null
        plannedAStarTargetVelocity = Vec3.ZERO
        aStarPlanTick = 0
    }

    private fun clearAttack() {
        clearVirtualAttack()
        setbackGuard.clear()
        setbackRollback.clear()
        packetSetbackRecoveryAttempted = false
    }

    private fun findTarget(): Pair<LivingEntity, Double>? {
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
        ) { it !== player && it.isAlive && !it.isRemoved }) {
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
                    throughTerrain = usesPacketAStar,
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

    private fun calculateSpearKillTravel(distance: Double): Double {
        val ticks = ceil(distance / effectiveStepLimit - 0.5).toInt().coerceAtLeast(1)
        return 2.0 * distance * ticks / (2.0 * ticks + 1)
    }

    private fun lockedAStarTargetCandidate(): Pair<LivingEntity, Double>? {
        val target = lockedAStarTarget ?: return null
        if (!target.isAlive || target.isRemoved) return null

        val distance = player.distanceTo(target).toDouble()
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
            packetAStarEnabled = usesPacketAStar,
            packetMovementMode = usesPacketMovementMode,
        )
    }

    private fun hasClearSpearKillDirectTravel(direction: Vec3, travel: Double): Boolean {
        val normalizedDirection = direction.normalize()
        if (normalizedDirection.lengthSqr() == 0.0) return false

        val origin = player.position()
        val destination = origin.add(normalizedDirection.scale(travel))
        return createServerValidatedSpearKillSegmentValidator(
            origin = origin,
            playerBoundingBox = player.boundingBox,
        ).isClear(origin, destination)
    }

    private fun createServerValidatedSpearKillSegmentValidator(
        origin: Vec3,
        playerBoundingBox: AABB,
    ) = createSpearKillAStarSegmentValidator(
        origin = origin,
        playerBoundingBox = playerBoundingBox,
        hasCollision = { box ->
            withVanillaSpearKillBlockShapes {
                !world.getBlockCollisions(player, box).allEmpty()
            }
        },
        resolveMovement = { box, movement ->
            withVanillaSpearKillBlockShapes {
                val entityCollisions = world.getEntityCollisions(player, box.expandTowards(movement))
                Entity.collideBoundingBox(player, movement, box, world, entityCollisions)
            }
        },
    )

    private fun createServerValidatedSpearKillDirectPacketSegmentValidator(
        origin: Vec3,
        playerBoundingBox: AABB,
    ) = createSpearKillDirectPacketSegmentValidator(
        origin = origin,
        playerBoundingBox = playerBoundingBox,
        hasDestinationCollision = { box ->
            withVanillaSpearKillBlockShapes {
                !world.getBlockCollisions(player, box).allEmpty()
            }
        },
        resolveMovement = { box, movement ->
            withVanillaSpearKillBlockShapes {
                resolveSpearKillDirectPacketMovement(player, box, movement)
            }
        },
    )

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
        if (!usesPacketMovementMode) {
            packetSessionSettings = null
            clearAStarRenderPath()
            attackMovements.addAll(createDirectAttackMovements(target, distance))
            return SpearKillAttackStartResult.STARTED
        }

        val settings = resolveSpearKillPacketSettings()
        if (!usesPacketAStar) {
            return startDirectPacketAttack(target, distance, settings)
        }
        motionPacketHeading = null
        packetSessionSettings = settings
        return startAStarPacketAttack(target, settings)
    }

    private fun startDirectPacketAttack(
        target: LivingEntity,
        distance: Double,
        settings: SpearKillPacketSessionSettings,
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
                stepCount = route.outboundMovements.size,
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
        requestSpearKillElytraFlight(settings)
        startSpearKillDirectPacketSession(
            session = packetBootSession,
            route = route,
            stepWaitTicks = settings.stepWaitTicks,
        )
        lockedAStarTarget = target
        plannedAStarTargetPosition = target.position()
        plannedAStarTargetVelocity = target.position().subtract(target.lastPos)
        aStarPlanTick = player.tickCount
        return SpearKillAttackStartResult.STARTED
    }

    private fun startAStarPacketAttack(
        target: LivingEntity,
        settings: SpearKillPacketSessionSettings,
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
        requestSpearKillElytraFlight(settings)
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
        return SpearKillAttackStartResult.STARTED
    }

    private fun createDirectAttackMovements(target: LivingEntity, distance: Double): List<Vec3> {
        val stepCount = ceil(distance / effectiveStepLimit).toInt().coerceAtLeast(1)
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

        return buildSpearKillAttackMovements(direction, distance, effectiveStepLimit)
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
        val effectiveMaxSpeed = effectiveStepLimit
        val terminalLungeDistance = maxAllowedSpeed.toDouble()
        val targetExtrapolation = PositionExtrapolation.getBestForEntity(target)
        val seedPrediction = predictAStarTarget(
            target = target,
            extrapolation = targetExtrapolation,
            ticks = spearKillAStarPredictionTicks(
                distance = routeOrigin.distanceTo(target.position()),
                maxSpeed = effectiveMaxSpeed,
                stepWaitTicks = stepWaitTicks,
            ),
        )
        val sessionBoundingBox = player.boundingBox.move(sessionOrigin.subtract(player.position()))
        val segmentValidator = createServerValidatedSpearKillSegmentValidator(
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
            lineOfSightShortcuts = lineOfSightShortcuts,
        ) ?: return null

        val seedSchedule = buildSpearKillPathSchedule(
            outboundStepCount = seedSpatialPlan.packetRoute.outboundMovements.size,
            stepWaitTicks = stepWaitTicks,
            terminalSuffixCount = seedSpatialPlan.terminalSuffixCount,
            preStrikeHoldTicks = 0,
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
        val packetRoute = buildSpearKillAStarPacketRoute(
            origin = routeOrigin,
            outboundWaypoints = outboundWaypoints,
            maxSpeed = effectiveMaxSpeed,
            segmentValidator = segmentValidator,
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
        val preStrikeHold = findEarliestSpearKillPreStrikeHold(
            outboundStepCount = spatialPlan.packetRoute.outboundMovements.size,
            stepWaitTicks = stepWaitTicks,
            terminalSuffixCount = spatialPlan.terminalSuffixCount,
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
            minPreStrikeHoldTicks = SPEAR_KILL_A_STAR_MIN_AIM_LOCK_TICKS,
        ) { schedule ->
            val prediction = predictAStarTarget(target, targetExtrapolation, schedule.hitTick)
            hasValidAStarTerminalAttackRay(prediction.boundingBox, eyeOffset, spatialPlan.approach)
        } ?: return null

        val schedule = buildSpearKillPathSchedule(
            outboundStepCount = spatialPlan.packetRoute.outboundMovements.size,
            stepWaitTicks = stepWaitTicks,
            terminalSuffixCount = spatialPlan.terminalSuffixCount,
            preStrikeHoldTicks = preStrikeHold,
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        ) ?: return null
        val hitPrediction = predictAStarTarget(target, targetExtrapolation, schedule.hitTick)
        return AStarAttackPlan(
            approach = spatialPlan.approach,
            packetRoute = spatialPlan.packetRoute,
            renderPath = spatialPlan.renderPath,
            targetPosition = hitPrediction.observedPosition,
            targetVelocity = target.position().subtract(target.lastPos),
            schedule = schedule,
            preStrikeHoldTicks = preStrikeHold,
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

    @Suppress("ReturnCount")
    private fun followLockedPacketTarget() {
        if (!usesPacketMovementMode || !packetBootSession.active || packetBootSession.recovering) return
        if (usesPacketAStar && !packetAStarAttackActive) return

        val target = lockedAStarTarget ?: return
        when (classifySpearKillPacketTargetState(
                isAlive = target.isAlive,
                isRemoved = target.isRemoved,
                isInCurrentWorld = target.level() === world,
                isWithinRange = player.distanceTo(target) <= maxTargetDistance,
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

        if (usesPacketAStar && packetBootSession.awaitingTerminalCommitAuthorization) {
            commitOrReplanAStarTerminal(target)
            return
        }

        val plannedPosition = plannedAStarTargetPosition ?: return
        val canReplacePath = if (usesPacketAStar) {
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
        if (usesPacketAStar) {
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
        return true
    }

    private fun replanLockedDirectPacketTarget(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ) {
        val route = createDirectPacketRouteForMovedTarget(target, routeOrigin, sessionOrigin)
        if (route == null || !packetBootSession.replaceRemainingOutbound(
                route.outboundMovements,
                strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
            )
        ) {
            terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
            return
        }
        plannedAStarTargetPosition = target.position()
        plannedAStarTargetVelocity = target.position().subtract(target.lastPos)
        aStarPlanTick = player.tickCount
    }

    private fun terminatePacketFollow(target: LivingEntity?, termination: PacketFollowTermination) {
        if (termination.rejectTarget && target != null) {
            rejectedAStarTarget = target
        } else if (rejectedAStarTarget === target) {
            rejectedAStarTarget = null
        }
        plannedAStarApproach = null
        plannedAStarTargetPosition = null
        plannedAStarTargetVelocity = Vec3.ZERO
        clearAStarRenderPath()
        packetBootSession.beginExactReturn()
        applyConfirmedPhysicalReturnPosition()
        val notificationKey = termination.notificationKey ?: return
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

        val maxSpeed = settings.transport.stepLimit
        if (!travel.isFinite() || travel <= 0.0 || !maxSpeed.isFinite() || maxSpeed <= 0.0) return null
        val stepCount = ceil(travel / maxSpeed).toInt().coerceAtLeast(1)
        val ticks = spearKillDirectPacketHitTicks(stepCount, settings.stepWaitTicks)
        val predictedTargetPosition = PositionExtrapolation.getBestForEntity(target)
            .getPositionInTicks(ticks.toDouble())
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = routeEye,
            predictedTargetPosition = predictedTargetPosition,
            targetEyeOffset = target.eyePosition.subtract(target.position()),
            fallbackDirection = player.lookAngle,
        )

        val sessionBoundingBox = player.boundingBox.move(sessionOrigin.subtract(player.position()))
        return buildSpearKillDirectPacketRoute(
            origin = routeOrigin,
            direction = direction,
            distance = travel,
            maxSpeed = maxSpeed,
            segmentValidator = createServerValidatedSpearKillDirectPacketSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = sessionBoundingBox,
            ),
        )
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
        val sessionBoundingBox = player.boundingBox.move(sessionOrigin.subtract(player.position()))
        val segmentValidator = createServerValidatedSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
        val route = SpearKillAStarRoutePlanner(
            allowDiagonal = aStar.diagonal,
            maxCost = aStar.maxCost,
            canTraverse = segmentValidator::isClear,
        ).plan(authoritativePosition, sessionOrigin) ?: return null
        val effectiveMaxSpeed = effectiveStepLimit
        val compactedRoute = compactSpearKillAStarWaypoints(
            origin = authoritativePosition,
            waypoints = route,
            maxSpeed = effectiveMaxSpeed,
            segmentValidator = segmentValidator,
            lineOfSightShortcuts = aStar.lineOfSightShortcuts,
        )
        return buildSpearKillAStarOutboundMovements(
            origin = authoritativePosition,
            waypoints = compactedRoute + sessionOrigin,
            maxSpeed = effectiveMaxSpeed,
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
    private val tickHandler = handler<GameTickEvent> {
        setbackGuard.tick(pathActive = packetBootSession.active)
        if (packetSetbackRecoveryAttempted && !packetBootSession.active && !setbackGuard.armed) {
            packetSetbackRecoveryAttempted = false
        }

        followLockedPacketTarget()

        if (packetBootSession.recovering) {
            packetRecoveryStallTicks++
            if (packetRecoveryStallTicks >= SPEAR_KILL_MAX_RECOVERY_STALL_TICKS) {
                // Stuck return / Blink desync — hard abort and snap home instead of floating forever.
                clearAttack()
            }
            return@handler
        }
        packetRecoveryStallTicks = 0
        if (!enabled) return@handler

        val attackRequested = hasAttackRequest
        if (!attackRequested) {
            if (!packetBootSession.active) {
                rejectedAStarTarget = null
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
            clearVirtualAttack()
            return@handler
        }

        if (!holdingSpear || !isUsingSpear) {
            // Only tear down an in-flight path here. Idle release used to call resetAttack every
            // tick (beginExactReturn / setback flags), which could leave the next charge unable
            // to start even though Preview still highlighted a target.
            if (hasActiveAttackPath) {
                resetAttack()
            } else if (!packetBootSession.active) {
                previewTarget = null
                rejectedAStarTarget = null
                clearAStarTargetLock()
                packetSetbackRecoveryAttempted = false
            }
            return@handler
        }

        val attackActive = hasActiveAttackPath
        val shouldFindTarget = Preview.enabled || (!attackActive && attackRequested)
        val target = when {
            usesPacketMovementMode && lockedAStarTarget != null -> lockedAStarTargetCandidate()
            shouldFindTarget -> findTarget()
            else -> null
        }
        previewTarget = target?.first

        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: run {
            resetAttack()
            return@handler
        }
        val chargeDuration = kineticWeapon.computeDamageUseDuration()

        // Keep the finite kinetic use window alive while aiming / standing still. Mid-path
        // refresh is skipped so Packet/A* cannot get aborted by an undercharged restart.
        if (
            shouldRefreshSpearKillServerUse(
                attackPathActive = attackActive,
                isUseKeyDown = mc.options.keyUse.isDown,
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = chargeDuration,
            )
        ) {
            refreshSpearKillServerUse()
            return@handler
        }

        if (player.ticksUsingItem <= kineticWeapon.delayTicks) {
            if (
                shouldAccelerateSpearKillCharge(
                    attackPathActive = attackActive,
                    isUseKeyDown = mc.options.keyUse.isDown,
                    isUsingSpear = isUsingSpear,
                    ticksUsingItem = player.ticksUsingItem,
                    delayTicks = kineticWeapon.delayTicks,
                )
            ) {
                Timer.requestTimerSpeed(
                    SPEAR_KILL_CHARGE_TIMER_SPEED,
                    Priority.IMPORTANT_FOR_USAGE_1,
                    ModuleSpearKill,
                    resetAfterTicks = 1,
                )
                repeat(SPEAR_KILL_CHARGE_ACCEL_PACKETS) {
                    network.send(MovePacketType.FULL.generatePacket())
                }
            }
            if (
                shouldResetSpearKillOnUndercharge(
                    ticksUsingItem = player.ticksUsingItem,
                    delayTicks = kineticWeapon.delayTicks,
                    isUsingSpear = isUsingSpear,
                    isUseKeyDown = mc.options.keyUse.isDown,
                )
            ) {
                resetAttack()
            }
            return@handler
        }

        if (!attackActive) {
            if (packetSetbackRecoveryAttempted) return@handler
            val (entity, dist) = target ?: return@handler
            if (player.ticksUsingItem >= chargeDuration || !attackRequested) return@handler
            if (usesPacketMovementMode && player.isPassenger) return@handler
            // A failed route belongs to this attack-key hold. Do not fall through to another
            // aligned entity while the key remains down: that would turn one unreachable target
            // into repeated path searches and violate the selected-target contract.
            // Packet routing owns its full corridor check and must return a visible BLOCKED result.
            // Motion keeps the existing LOS + direct-travel gate.
            if (!usesPacketMovementMode && !isDirectSpearKillTargetEligible(entity, dist)) return@handler
            if (usesPacketMovementMode) {
                val lockedTarget = lockedAStarTarget
                if ((lockedTarget != null && lockedTarget !== entity) || rejectedAStarTarget === entity) return@handler
                lockedAStarTarget = entity
            }
            when (createAttackMovement(entity, dist)) {
                SpearKillAttackStartResult.STARTED -> Unit
                SpearKillAttackStartResult.RETRY_LATER -> refreshSpearKillServerUse()
                SpearKillAttackStartResult.BLOCKED -> {
                    terminatePacketFollow(entity, PacketFollowTermination.BLOCKED)
                }
                SpearKillAttackStartResult.REJECTED -> if (usesPacketMovementMode) {
                    rejectedAStarTarget = entity
                }
            }
            return@handler
        }

        if (packetBootSession.active) {
            return@handler
        }

        val movement = attackMovements.removeFirst()
        motionPacketHeading = spearKillKineticHeading(movement)
        player.deltaMovement = movement
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
        if (!packetBootSession.active || event.isCancelled || plannedPacket != null || setbackRollback.confirming) {
            return@handler
        }

        val offset = packetBootSession.prepareNextStep()
        if (offset == null) {
            if (packetBootSession.holdingPreStrike) {
                // A dedicated, position-stable packet makes the terminal heading reach the server
                // one full tick before authorization can release the first lunge movement.
                sendFallbackMovementPacket()
            }
            return@handler
        }
        val position = packetPositionOrigin().add(offset)
        event.x = position.x
        event.y = position.y
        event.z = position.z
        event.ground = isSpearKillGrounded(event.ground, offset)
        awaitingVanillaMovementPacket = true
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
    private fun hasClearPendingSpearKillPacketStep(): Boolean {
        if (!packetBootSession.requiresDelivery) return false

        val sessionOrigin = packetSessionOrigin ?: return false
        val sessionBoundingBox = player.boundingBox.move(sessionOrigin.subtract(player.position()))
        val segmentValidator = if (packetAStarAttackActive) {
            createServerValidatedSpearKillSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = sessionBoundingBox,
            )
        } else {
            createServerValidatedSpearKillDirectPacketSegmentValidator(
                origin = sessionOrigin,
                playerBoundingBox = sessionBoundingBox,
            )
        }
        return isSpearKillPacketStepClear(
            sessionOrigin = sessionOrigin,
            committedOffset = packetBootSession.committedOffset,
            candidateOffset = packetBootSession.virtualOffset,
            maxStepLength = effectiveStepLimit,
            segmentValidator = segmentValidator,
        )
    }

    private fun rejectPendingSpearKillPacketStep(blocked: Boolean) {
        val blockedOutbound = blocked && packetBootSession.pendingOutboundStep
        packetBootSession.confirmStep(delivered = false)
        plannedPacket = null
        awaitingVanillaMovementPacket = false
        if (blockedOutbound) {
            terminatePacketFollow(lockedAStarTarget, PacketFollowTermination.BLOCKED)
        }
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
        val blockedPendingStep = carriesPendingStep && !hasClearPendingSpearKillPacketStep()
        if (carriesPendingStep && (event.isCancelled || blockedPendingStep)) {
            if (!event.isCancelled) {
                event.cancelEvent()
            }
            rejectPendingSpearKillPacketStep(blocked = blockedPendingStep)
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
        if (
            event.origin != TransferOrigin.OUTGOING ||
            !hasActiveAttackPath ||
            setbackRollback.confirming ||
            !shouldProtectFallDamage
        ) {
            return@handler
        }

        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        if (packetBootSession.active && (packet !== plannedPacket || packetBootSession.virtualOffset.y != 0.0)) {
            return@handler
        }

        fallDamageDeliveryTracker.protect(packet)
    }

    @Suppress("unused")
    private val packetDeliveryHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        if (event.origin == TransferOrigin.INCOMING) {
            val packet = event.packet as? ClientboundPlayerPositionPacket ?: return@handler
            if (!event.isCancelled && setbackGuard.armed) {
                setbackRollback.mark(packet)
            }
            return@handler
        }

        val packet = event.packet as? ServerboundMovePlayerPacket ?: return@handler
        val virtualPacket = virtualSessionPackets.remove(packet)
        val plannedPathPacket = packet === plannedPacket
        val pathPacket = virtualPacket || plannedPathPacket

        val queuedByBlink = pathPacket && BlinkManager.packetQueue.any { it.packet === packet }
        if (queuedByBlink) {
            BlinkManager.packetQueue.removeIf { it.packet === packet }
        }

        val delivered = !event.isCancelled && !queuedByBlink
        if (fallDamageDeliveryTracker.confirmFinalState(packet, cancelled = !delivered)) {
            player.resetFallDistance()
        }

        if (!pathPacket) return@handler

        if (virtualPacket && delivered && packet.hasPosition()) {
            setbackGuard.record(
                Vec3(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0)),
                player.position(),
            )
        }

        if (!plannedPathPacket) return@handler

        packetBootSession.confirmStep(delivered)
        applyConfirmedPhysicalReturnPosition()
        if (!packetBootSession.active) {
            packetSessionOrigin = null
            packetSessionSettings = null
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
            physicalReturnPositioner.clear()
        }
    }

    internal fun preparePacketSetback(packet: ClientboundPlayerPositionPacket, player: Player) {
        if (!setbackRollback.isMarked(packet)) return
        if (player.isPassenger) {
            clearAttack()
            return
        }

        val localState = SpearKillLocalPlayerState.capture(player)
        val sessionOrigin = packetSessionOrigin ?: player.position()
        val collisionSafeRecoveryRequired = packetAStarAttackActive
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
        if (preparedSetback == null ||
            collisionSafeRecoveryRequired && preparedSetback.exactRecoveryMovements == null
        ) {
            clearAttack()
            return
        }
        if (packetSetbackRecoveryAttempted) {
            clearAttack()
            return
        }

        val recoverySettings = packetSessionSettings
        clearVirtualAttack()
        packetSessionSettings = recoverySettings
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
        packetSessionOrigin = setback.sessionOrigin
        physicalReturnPositioner.clear()
        if (setback.physicalReturn) {
            val exactRecovery = setback.exactRecoveryMovements
            if (exactRecovery != null) {
                packetBootSession.beginPhysicalExactRecoveryFrom(setback.authoritativeOffset, exactRecovery)
            } else {
                packetBootSession.beginPhysicalRecoveryFrom(setback.authoritativeOffset, effectiveStepLimit)
            }
        } else {
            packetBootSession.beginRecoveryFrom(setback.authoritativeOffset, effectiveStepLimit)
        }
        applyConfirmedPhysicalReturnPosition(player)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { clearAttack() }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> { clearAttack() }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val aStar = movementConfiguration.packet.aStar
        if (shouldRenderSpearKillAStarPath(
                packetAStarEnabled = usesPacketAStar,
                renderPathEnabled = aStar.renderPath,
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
        migrateLegacySpearKillMovementConfig(jsonObject)
    }

    override fun onDisabled() {
        clearAttack()
        super.onDisabled()
    }
}

/** Extra distance around an entity's vanilla/Hitbox pick box that still counts as a crosshair selection. */
private const val SPEAR_KILL_TARGET_SELECTION_MARGIN = 0.75
private const val SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED = 1e-9
private const val SPEAR_KILL_MIN_ATTACK_RAY_RANGE = 2.0
private const val SPEAR_KILL_ATTACK_RAY_RANGE = 4.5
private const val SPEAR_KILL_A_STAR_MIN_AIM_LOCK_TICKS = 1
private const val SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS = 250L
private const val SPEAR_KILL_CHARGE_TIMER_SPEED = 5f
private const val SPEAR_KILL_CHARGE_ACCEL_PACKETS = 20
private const val SPEAR_KILL_MAX_RECOVERY_STALL_TICKS = 40

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
