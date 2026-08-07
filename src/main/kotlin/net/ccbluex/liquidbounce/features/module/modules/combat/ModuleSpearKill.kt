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
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
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
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
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
import kotlin.math.tan

/**
 * Spear kill module
 *
 * Automatically attacks enemies using a charged spear.
 */
@Suppress("TooManyFunctions", "LargeClass")
object ModuleSpearKill : ClientModule("SpearKill", ModuleCategories.COMBAT, aliases = listOf("AutoSpear")) {

    private val maxTargetDistance by float("MaxTargetDistance", 50f, 3f..200f)
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
    private var plannedAStarTargetPosition: Vec3? = null
    private var aStarPlanTick = 0
    private var packetSetbackRecoveryAttempted = false
    private var packetSessionSettings: SpearKillPacketSessionSettings? = null
    private var motionPacketHeading: Rotation? = null

    private data class AStarAttackPlan(
        val packetRoute: SpearKillAStarPacketRoute,
        val renderPath: List<Vec3>,
        val targetPosition: Vec3,
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

    internal val currentAttackVelocity get() = if (packetBootSession.active) 0.0 else currentMovement.length()
    internal val currentAttackDirection get() = currentMovement.normalize()
    internal val usesPacketMovement get() = packetBootSession.active
    private val currentMovement get() = attackMovements.firstOrNull() ?: Vec3.ZERO
    private val hasActiveAttackPath get() = attackMovements.isNotEmpty() || packetBootSession.active
    internal val controlsSpearUse get() = hasActiveAttackPath
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
        clearAStarTargetLock()
    }

    private fun clearAStarRenderPath() {
        plannedAStarRenderPath = emptyList()
    }

    private fun clearAStarTargetLock() {
        lockedAStarTarget = null
        plannedAStarTargetPosition = null
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
        val targetSearchBox = player.boundingBox.inflate(
            searchDistance + spearKillTargetSelectionMargin(searchDistance, usesPacketAStar),
        )
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
                hitboxMargin = spearKillTargetSelectionMargin(dist, usesPacketAStar),
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
        val travel = calculateSpearKillTravel(sqrt(bestDistanceSquared))
        return (entity to travel).takeIf { isDirectSpearKillTargetEligible(entity, travel) }
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
        )
    }

    private fun hasClearSpearKillDirectTravel(direction: Vec3, travel: Double): Boolean {
        val normalizedDirection = direction.normalize()
        if (normalizedDirection.lengthSqr() == 0.0) return false

        val origin = player.position()
        val destination = origin.add(normalizedDirection.scale(travel))
        return createSpearKillAStarSegmentValidator(
            origin = origin,
            playerBoundingBox = player.boundingBox,
            hasCollision = { box -> !world.getBlockCollisions(player, box).allEmpty() },
        ).isClear(origin, destination)
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
        if (!usesPacketMovementMode) {
            packetSessionSettings = null
            clearAStarRenderPath()
            attackMovements.addAll(createDirectAttackMovements(target, distance))
            return SpearKillAttackStartResult.STARTED
        }

        motionPacketHeading = null
        val settings = resolveSpearKillPacketSettings()
        packetSessionSettings = settings
        if (!usesPacketAStar) {
            packetAStarAttackActive = false
            val movements = run {
                clearAStarRenderPath()
                createDirectAttackMovements(target, distance)
            }
            packetSessionOrigin = player.position()
            physicalReturnPositioner.clear()
            requestSpearKillElytraFlight(settings)
            packetBootSession.startPhysicalReturn(
                path = movements,
                outboundSteps = (movements.size - 1) / 2,
                stepWaitTicks = settings.stepWaitTicks,
            )
            return SpearKillAttackStartResult.STARTED
        }

        clearAStarRenderPath()
        val origin = player.position()
        val plan = createAStarAttackPlan(target, origin, origin)
        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        val startResult = classifySpearKillAStarStartFailure(
            routeFound = plan != null && kineticWeapon != null,
            hasDamageWindow = plan != null && kineticWeapon != null && hasSpearKillAStarDamageWindow(
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = kineticWeapon.computeDamageUseDuration(),
                outboundStepCount = plan.packetRoute.outboundMovements.size,
                stepWaitTicks = settings.stepWaitTicks,
                confirmationTicks = SPEAR_KILL_A_STAR_STRIKE_HOLD_TICKS,
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
            strikeHoldTicks = SPEAR_KILL_A_STAR_STRIKE_HOLD_TICKS,
            stepWaitTicks = settings.stepWaitTicks,
        )
        plannedAStarRenderPath = plan.renderPath
        plannedAStarTargetPosition = plan.targetPosition
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

    @Suppress("ReturnCount")
    private fun createAStarAttackPlan(
        target: LivingEntity,
        routeOrigin: Vec3,
        sessionOrigin: Vec3,
    ): AStarAttackPlan? {
        val eyeOffset = player.eyePosition.subtract(player.position())
        val routeEyePosition = routeOrigin.add(eyeOffset)
        val aStar = movementConfiguration.packet.aStar
        val effectiveMaxSpeed = effectiveStepLimit
        val targetPrediction = predictAStarTarget(target, routeOrigin, effectiveMaxSpeed)
        val routePlanner = SpearKillAStarRoutePlanner(
            allowDiagonal = aStar.diagonal,
            maxCost = aStar.maxCost,
        )
        val sessionBoundingBox = player.boundingBox.move(sessionOrigin.subtract(player.position()))
        val segmentValidator = createSpearKillAStarSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
            hasCollision = { box -> !world.getBlockCollisions(player, box).allEmpty() },
        )
        val preferredDirection = calculateSpearKillAttackDirection(
            playerEyePosition = routeEyePosition,
            predictedTargetPosition = targetPrediction.position,
            targetEyeOffset = targetPrediction.eyePosition.subtract(targetPrediction.position),
            fallbackDirection = player.lookAngle,
        )
        val approaches = filterSpearKillAStarApproachesByTerminalClearance(
            approaches = createSpearKillAStarAttackApproachCandidates(
                targetBox = targetPrediction.boundingBox,
                targetEyePosition = targetPrediction.eyePosition,
                playerEyeOffset = eyeOffset,
                preferredDirection = preferredDirection,
                terminalLungeDistance = effectiveMaxSpeed,
            ),
            segmentValidator = segmentValidator,
        )
        for (approach in approaches) {
            val route = routePlanner.plan(routeOrigin, approach.plannerGoal) ?: continue
            val compactedRoute = simplifySpearKillAStarWaypoints(
                origin = routeOrigin,
                waypoints = route,
                maxSpeed = effectiveMaxSpeed,
                segmentValidator = segmentValidator,
            )
            val outboundWaypoints = compactedRoute + approach.plannerGoal + approach.terminalWaypoint
            val packetRoute = buildSpearKillAStarPacketRoute(
                origin = routeOrigin,
                outboundWaypoints = outboundWaypoints,
                maxSpeed = effectiveMaxSpeed,
                segmentValidator = segmentValidator,
            ) ?: continue
            if (!isSpearKillAStarTerminalStepValid(packetRoute.outboundMovements, approach, effectiveMaxSpeed) ||
                !hasValidAStarTerminalAttackRay(targetPrediction.boundingBox, eyeOffset, approach)
            ) {
                continue
            }
            return AStarAttackPlan(
                packetRoute = packetRoute,
                renderPath = buildSpearKillAStarRenderPath(routeOrigin, outboundWaypoints),
                targetPosition = targetPrediction.observedPosition,
            )
        }
        return null
    }

    private fun predictAStarTarget(
        target: LivingEntity,
        routeOrigin: Vec3,
        effectiveMaxSpeed: Double,
    ): AStarTargetPrediction {
        val observedPosition = target.position()
        val predictionTicks = spearKillAStarPredictionTicks(
            distance = routeOrigin.distanceTo(observedPosition),
            maxSpeed = effectiveMaxSpeed,
            stepWaitTicks = activePacketStepWaitTicks,
        )
        val predictedPosition = PositionExtrapolation.getBestForEntity(target)
            .getPositionInTicks(predictionTicks.toDouble())
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

    private fun followLockedAStarTarget() {
        if (!packetAStarAttackActive || !packetBootSession.active || packetBootSession.recovering) return

        val target = lockedAStarTarget ?: return
        if (!target.isAlive || target.isRemoved || target.level() !== world ||
            player.distanceTo(target) > maxTargetDistance
        ) {
            abortAStarFollow(target)
            return
        }

        val plannedPosition = plannedAStarTargetPosition ?: return
        if (!shouldReplanSpearKillAStarTarget(
                plannedPosition,
                target.position(),
                player.tickCount - aStarPlanTick,
            ) || !packetBootSession.canReplaceRemainingOutbound || plannedPacket != null ||
            awaitingVanillaMovementPacket
        ) {
            return
        }

        val sessionOrigin = packetSessionOrigin ?: return
        val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
        val plan = createAStarAttackPlan(target, routeOrigin, sessionOrigin)
        if (plan == null || !packetBootSession.replaceRemainingOutbound(
                plan.packetRoute.outboundMovements,
                SPEAR_KILL_A_STAR_STRIKE_HOLD_TICKS,
            )
        ) {
            abortAStarFollow(target)
            return
        }

        plannedAStarRenderPath = plan.renderPath
        plannedAStarTargetPosition = plan.targetPosition
        aStarPlanTick = player.tickCount
    }

    private fun abortAStarFollow(target: LivingEntity) {
        rejectedAStarTarget = target
        plannedAStarTargetPosition = null
        clearAStarRenderPath()
        packetBootSession.beginExactReturn()
        applyConfirmedPhysicalReturnPosition()
    }

    private fun createCollisionSafeSetbackRecovery(
        sessionOrigin: Vec3,
        authoritativeOffset: Vec3,
    ): List<Vec3>? {
        packetBootSession.exactRecoveryMovementsFrom(authoritativeOffset)?.let { return it }
        if (!packetAStarAttackActive) return null

        val authoritativePosition = sessionOrigin.add(authoritativeOffset)
        val aStar = movementConfiguration.packet.aStar
        val route = SpearKillAStarRoutePlanner(aStar.diagonal, aStar.maxCost)
            .plan(authoritativePosition, sessionOrigin) ?: return null
        val sessionBoundingBox = player.boundingBox.move(sessionOrigin.subtract(player.position()))
        val segmentValidator = createSpearKillAStarSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
            hasCollision = { box -> !world.getBlockCollisions(player, box).allEmpty() },
        )
        val effectiveMaxSpeed = effectiveStepLimit
        val compactedRoute = simplifySpearKillAStarWaypoints(
            origin = authoritativePosition,
            waypoints = route,
            maxSpeed = effectiveMaxSpeed,
            segmentValidator = segmentValidator,
        )
        return buildSpearKillAStarOutboundMovements(
            origin = authoritativePosition,
            waypoints = compactedRoute + sessionOrigin,
            maxSpeed = effectiveMaxSpeed,
            segmentValidator = segmentValidator,
        )
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        setbackGuard.tick(pathActive = packetBootSession.active)
        if (packetSetbackRecoveryAttempted && !packetBootSession.active && !setbackGuard.armed) {
            packetSetbackRecoveryAttempted = false
        }

        followLockedAStarTarget()

        if (packetBootSession.recovering) {
            return@handler
        }
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
            resetAttack()
            return@handler
        }

        val attackActive = hasActiveAttackPath
        val shouldFindTarget = Preview.enabled || (!attackActive && attackRequested)
        val target = when {
            usesPacketAStar && lockedAStarTarget != null -> lockedAStarTargetCandidate()
            shouldFindTarget -> findTarget()
            else -> null
        }
        previewTarget = target?.first

        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: run {
            resetAttack()
            return@handler
        }
        val chargeDuration = kineticWeapon.computeDamageUseDuration()

        if (player.ticksUsingItem <= kineticWeapon.delayTicks) {
            resetAttack()
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
            if (usesPacketAStar) {
                val lockedTarget = lockedAStarTarget
                if ((lockedTarget != null && lockedTarget !== entity) || rejectedAStarTarget === entity) return@handler
                lockedAStarTarget = entity
            }
            when (createAttackMovement(entity, dist)) {
                SpearKillAttackStartResult.STARTED,
                SpearKillAttackStartResult.RETRY_LATER,
                -> Unit
                SpearKillAttackStartResult.REJECTED -> if (usesPacketAStar) {
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

        val offset = packetBootSession.prepareNextStep() ?: return@handler
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
        val segmentValidator = createSpearKillAStarSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
            hasCollision = { box -> !world.getBlockCollisions(player, box).allEmpty() },
        )
        return isSpearKillPacketStepClear(
            sessionOrigin = sessionOrigin,
            committedOffset = packetBootSession.committedOffset,
            candidateOffset = packetBootSession.virtualOffset,
            maxStepLength = effectiveStepLimit,
            segmentValidator = segmentValidator,
        )
    }

    /**
     * Keeps the planned route intact when its current edge has become unsafe. The session retries
     * this exact edge on a later tick rather than skipping, shortening, or synthesizing a path.
     */
    private fun rejectUnsafePendingSpearKillPacketStep() {
        packetBootSession.confirmStep(delivered = false)
        plannedPacket = null
        awaitingVanillaMovementPacket = false
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
        if (carriesPendingStep && (event.isCancelled || !hasClearPendingSpearKillPacketStep())) {
            if (!event.isCancelled) {
                event.cancelEvent()
            }
            rejectUnsafePendingSpearKillPacketStep()
            return@handler
        }

        if (shouldSuppressSpearKillAStarStrikeHoldPacket(
                packetAStarAttackActive = packetAStarAttackActive,
                holdingStrike = packetBootSession.holdingStrike,
            )
        ) {
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
private const val SPEAR_KILL_TARGET_SELECTION_MARGIN = 0.35
private const val SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED = 1e-9
private const val SPEAR_KILL_MIN_ATTACK_RAY_RANGE = 2.0
private const val SPEAR_KILL_ATTACK_RAY_RANGE = 4.5
private const val SPEAR_KILL_A_STAR_STRIKE_HOLD_TICKS = 2
private const val SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS = 250L

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

internal fun spearKillTargetSelectionMargin(distance: Double, packetAStarEnabled: Boolean): Double {
    if (!packetAStarEnabled || !distance.isFinite() || distance <= 0.0) {
        return SPEAR_KILL_TARGET_SELECTION_MARGIN
    }

    return maxOf(
        SPEAR_KILL_TARGET_SELECTION_MARGIN,
        distance * tan(Math.toRadians(SPEAR_KILL_A_STAR_SELECTION_CONE_DEGREES)),
    )
}

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

private const val SPEAR_KILL_A_STAR_SELECTION_CONE_DEGREES = 15.0
