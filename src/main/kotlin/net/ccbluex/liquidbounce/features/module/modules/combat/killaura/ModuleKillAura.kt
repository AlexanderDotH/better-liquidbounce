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
package net.ccbluex.liquidbounce.features.module.modules.combat.killaura

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.global.GlobalSettingsCombat
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.FightBotTargetHandoff
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoWeapon
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleFightBot
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSuperHit
import net.ccbluex.liquidbounce.features.module.modules.combat.lockedTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.selectKillAuraTargetForFightBot
import net.ccbluex.liquidbounce.features.module.modules.combat.state
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals.CriticalsSelectionMode
import net.ccbluex.liquidbounce.features.module.modules.combat.elytratarget.ModuleElytraTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRotationsValueGroup.KillAuraRotationTiming.ON_TICK
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRotationsValueGroup.KillAuraRotationTiming.SNAP
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.RaycastMode.TRACE_ALL
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.RaycastMode.TRACE_NONE
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.RaycastMode.TRACE_ONLYENEMY
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraVelocityHit
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraFailSwing
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraFailSwing.dealWithFakeSwing
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraNotifyWhenFail
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraNotifyWhenFail.failedHits
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraNotifyWhenFail.renderFailedHits
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraRange
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraRangeIndicator
import net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.modes.GenericDebugRecorder
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.point.PointTracker
import net.ccbluex.liquidbounce.utils.aiming.preference.LeastDifferencePreference
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.isInventoryOpen
import net.ccbluex.liquidbounce.utils.inventory.isInContainerScreen
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

/**
 * KillAura module
 *
 * Automatically attacks enemies.
 */
@Suppress("MagicNumber", "TooManyFunctions")
object ModuleKillAura : ClientModule("KillAura", ModuleCategories.COMBAT) {

    // Attack speed
    val clicker = tree(KillAuraClicker)
    val range = tree(KillAuraRange)
    val targetTracker = tree(KillAuraTargetTracker)

    // Rotation
    private val rotations = tree(KillAuraRotationsValueGroup)
    private val pointTracker = tree(PointTracker(this))

    private val requires by multiEnumChoice<KillAuraRequirements>("Requires")

    private val requirementsMet
        get() = requires.all { it.asBoolean }

    // Bypass techniques
    internal val raycast by enumChoice("Raycast", TRACE_ALL)
    private val criticalsSelectionMode by enumChoice("Criticals", CriticalsSelectionMode.SMART)
    private val keepSprint by boolean("KeepSprint", true)

    // Inventory Handling
    internal val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)
    internal val simulateInventoryClosing by boolean("SimulateInventoryClosing", true)

    /**
     * The use of suspend [waitTicks] is a bit too
     * risky for a large and complex module
     * such as KillAura. So back to the basics.
     */
    internal var waitTicks = 0

    init {
        tree(KillAuraAutoBlock)
        tree(KillAuraVelocityHit)
        tree(TargetRenderer(this) {
            targetTracker.target?.takeUnless { ModuleElytraTarget.isSameTargetRendering(it) }
        })
        tree(KillAuraFailSwing)
        tree(KillAuraRangeIndicator)
    }

    val extendedInteractionRange: Float
        get() = if (KillAuraVelocityHit.isVelocityHitPossible) {
            range.interactionRange + KillAuraVelocityHit.extendRange
        } else {
            range.interactionRange
        }

    internal fun shouldUseSuperHitFor(target: LivingEntity): Boolean {
        return GlobalSettingsCombat.delegateKillAuraAttacks && ModuleSuperHit.running &&
            ModuleSuperHit.isTargetInConfiguredRange(target) &&
            player.squaredBoxedDistanceTo(target) > extendedInteractionRange.sq()
    }

    private fun isDistantSpearKillTarget(target: LivingEntity): Boolean =
        target.squaredBoxedDistanceTo(player) > extendedInteractionRange.sq() &&
            ModuleSpearKill.canAcceptKillAuraTarget(target)

    private fun delegatedAttackRotation(target: LivingEntity): Rotation? = when {
        isDistantSpearKillTarget(target) -> player.rotation
        shouldUseSuperHitFor(target) -> calculateKillAuraDelegatedAttackRotation(
            eyes = player.eyePosition,
            targetBox = target.boundingBox,
        )
        else -> null
    }

    private fun canDispatchSuperHit(target: LivingEntity, rotation: Rotation): Boolean =
        isLookingAtEntity(
            toEntity = target,
            rotation = rotation,
            range = ModuleSuperHit.maximumTargetRange.toDouble(),
            throughWallsRange = 0.0,
        ) != null

    private fun shouldSuppressForSpearKill(target: LivingEntity?): Boolean {
        val route = if (ModuleSpearKill.ownsKillAuraRoute || ModuleSpearKill.reservesFightBotSpearUse(target) ||
            target?.let(::isDistantSpearKillTarget) == true
        ) {
            KillAuraAttackRoute.SPEAR_KILL
        } else {
            KillAuraAttackRoute.NONE
        }
        return selectKillAuraSpearKillSuppressionPolicy(route).suppressAutoWeapon
    }

    /**
     * Narrow selection boundary consumed by SpearKill. KillAura retains ownership of filtering and
     * priority while SpearKill remains independent from CPS and click scheduling.
     */
    internal fun targetForSpearKill(): LivingEntity? {
        if (!running || !requirementsMet || CombatManager.shouldPauseCombat) return null
        val inventoryOpen = isInventoryOpen || isInContainerScreen
        if (inventoryOpen && !ignoreOpenInventory) return null

        return targetTracker.target?.takeIf(::isDistantSpearKillTarget)
    }

    override fun onDisabled() {
        targetTracker.reset()
        failedHits.clear()
        KillAuraNotifyWhenFail.failedHitsIncrement = 0
        KillAuraVelocityHit.reset()
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        event.renderEnvironment {
            renderFailedHits()
            KillAuraRangeIndicator.render(this, event.partialTicks)
        }
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        if (waitTicks > 0) {
            waitTicks--
        }

        // Make sure killaura-logic is not running while inventory is open
        val isInInventoryScreen = isInventoryOpen || mc.gui.screen() is ContainerScreen
        val shouldResetTarget = player.isSpectator || player.isDeadOrDying || !requirementsMet

        if (isInInventoryScreen && !ignoreOpenInventory || shouldResetTarget) {
            // Reset current target
            targetTracker.reset()
            return@handler
        }

        // Update the current target tracker to make sure you attack the best enemy
        updateTarget()

        // The held kinetic spear is part of SpearKill's explicit intent and cannot be replaced.
        if (!shouldSuppressForSpearKill(targetTracker.target)) {
            ModuleAutoWeapon.onTarget(targetTracker.target)
        }
    }

    @Suppress("unused")
    private val gameHandler = tickHandler {
        if (player.isDeadOrDying || player.isSpectator) {
            return@tickHandler
        }

        // Check if there is target to attack
        val target = targetTracker.target

        val spearReserved = ModuleSpearKill.reservesFightBotSpearUse(target)
        if (CombatManager.shouldPauseCombat) {
            if (!ModuleSpearKill.ownsKillAuraRoute && !spearReserved) {
                KillAuraAutoBlock.stopBlocking()
            }
            return@tickHandler
        }

        if (ModuleSpearKill.ownsKillAuraRoute || spearReserved) return@tickHandler

        if (target == null) {
            val hasUnblocked = KillAuraAutoBlock.stopBlocking()

            // Deal with fake swing when there is no target
            if (KillAuraFailSwing.enabled && requirementsMet) {
                if (hasUnblocked && KillAuraAutoBlock.pauseOnUnblockTicks > 0) {
                    waitTicks = KillAuraAutoBlock.pauseOnUnblockTicks
                } else {
                    dealWithFakeSwing(null)
                }
            }
            return@tickHandler
        }

        // Check if the module should (not) continue after the blocking state is updated
        if (!requirementsMet) {
            return@tickHandler
        }

        val delegatedRotation = (target as? LivingEntity)?.let(::delegatedAttackRotation)
        val rotation = (delegatedRotation ?: if (rotations.rotationTiming == ON_TICK) {
            val targeting = targetingParameters(
                target,
                normalRange = range.interactionRange,
                normalWallsRange = range.interactionThroughWallsRange,
            )
            targeting?.let {
                findRotation(target, it.range, it.wallsRange, it.allowAimThroughWalls)?.rotation
            }
        } else {
            null
        } ?: RotationManager.currentRotation ?: player.rotation).normalize()

        val raycastTarget = when {
            raycast != TRACE_NONE -> {
                findEntityInCrosshair(range.interactionRange.toDouble(), rotation, predicate = {
                    when (raycast) {
                        TRACE_ONLYENEMY -> it.shouldBeAttacked()
                        TRACE_ALL -> true
                        else -> false
                    }
                })?.entity
            }
            else -> null
        }
        val handoff = ModuleFightBot.targetHandoff
        val attackTarget = selectKillAuraTargetForFightBot<Entity>(
            handoff = handoff.state,
            lockedTarget = handoff.lockedTarget,
            trackedTarget = target,
            crosshairTarget = raycastTarget,
        ) ?: return@tickHandler

        if (handoff === FightBotTargetHandoff.Inactive && !ModuleSpearKill.ownsKillAuraRoute &&
            attackTarget is LivingEntity && attackTarget.shouldBeAttacked() && attackTarget != target
        ) {
            targetTracker.target = attackTarget
        }

        attackTarget(attackTarget, rotation)
    }

    val shouldBlockSprinting
        get() = shouldBlockSprintForCriticals(
            keepSprintEnabled = keepSprint,
            elytraTargetRunning = ModuleElytraTarget.running,
            criticalsRequestSprintStop = criticalsSelectionMode.shouldStopSprinting(clicker, targetTracker.target),
        )

    @Suppress("unused")
    private val sprintHandler = handler<SprintEvent> { event ->
        if (shouldBlockSprinting && (event.source == SprintEvent.Source.MOVEMENT_TICK ||
                event.source == SprintEvent.Source.INPUT)) {
            event.sprint = false
        }
    }

    @Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod")
    private suspend fun attackTarget(target: Entity, rotation: Rotation) {
        debugParameter("Rotation") { rotation }
        debugParameter("Target") { target.scoreboardName }

        val isInRange = isNormalAttackPossible(target, rotation)
        val superHitTarget = target as? LivingEntity
        val attackRoute = determineAttackRoute(superHitTarget, rotation, isInRange)
        debugParameter("Attack Route") { attackRoute }

        // SpearKill starts from its own tick handler immediately; KillAura must not click, block,
        // switch weapons, or publish attack-success bookkeeping for this route.
        if (attackRoute == KillAuraAttackRoute.SPEAR_KILL) return

        // Make it seem like we are blocking for routes still owned by KillAura.
        KillAuraAutoBlock.makeSeemBlock()

        // Check if our target is in range, otherwise deal with auto block
        if (attackRoute == KillAuraAttackRoute.NONE) {
            handleUnavailableTarget(target)
            return
        }

        debugParameter("Valid Rotation") { rotation }

        val mainHandStack = player.mainHandItem

        // Attack enemy, according to the attack scheduler
        if (clicker.isClickTick && canAttackNow(target, mainHandStack) &&
            !KillAuraAutoBlock.isPrioritizingBlocking) {
            clicker.prepareForAttack(rotation) {
                // On each click, we check if we are still ready to attack
                if (!canAttackNow(target, mainHandStack)) {
                    return@prepareForAttack false
                }

                val attackKeepSprint = keepSprint && !shouldBlockSprinting
                executeKillAuraAttack(
                    route = attackRoute,
                    normalAttack = {
                        attackEntity(target, SwingMode.DO_NOT_HIDE, attackKeepSprint)
                        true
                    },
                    superHitAttack = {
                        val livingTarget = superHitTarget ?: return@executeKillAuraAttack false
                        ModuleSuperHit.tryAttack(
                            livingTarget,
                            rotation,
                            attackKeepSprint,
                            automatedByKillAura = true,
                        )
                    },
                    onSuccess = {
                        range.update()
                        KillAuraNotifyWhenFail.failedHitsIncrement = 0
                        KillAuraAutoBlock.hasBlockedSinceAttack = false

                        GenericDebugRecorder.recordDebugInfo(ModuleKillAura, "attackEntity", JsonObject().apply {
                            add("player", GenericDebugRecorder.debugObject(player))
                            add("targetPos", GenericDebugRecorder.debugObject(target))
                        })
                    },
                )
            }
        } else if (KillAuraClicker.ticksSinceLastClick >= KillAuraAutoBlock.reblockTicks) {
            KillAuraAutoBlock.startBlocking()
        }
    }

    private fun isNormalAttackPossible(target: Entity, rotation: Rotation): Boolean {
        val attackHitResult = isLookingAtEntity(
            toEntity = target,
            rotation = rotation,
            range = extendedInteractionRange.toDouble(),
            throughWallsRange = range.interactionThroughWallsRange.toDouble()
        )

        debugParameter("Target Hit Result") { attackHitResult?.location }

        val isInRange = ModuleElytraTarget.canIgnoreKillAuraRotations ||
            attackHitResult != null && range.isInRange(pos = attackHitResult.location)
        debugParameter("Is In Range") { isInRange }
        return isInRange
    }

    private fun determineAttackRoute(
        superHitTarget: LivingEntity?,
        rotation: Rotation,
        normalAttackPossible: Boolean,
    ): KillAuraAttackRoute {
        val spearKillTargetPossible = !normalAttackPossible && superHitTarget != null &&
            isDistantSpearKillTarget(superHitTarget)
        val superHitTargetPossible = !normalAttackPossible && !spearKillTargetPossible && superHitTarget != null &&
            shouldUseSuperHitFor(superHitTarget) && canDispatchSuperHit(superHitTarget, rotation)

        return selectKillAuraAttackRoute(
            delegateKillAuraAttacks = GlobalSettingsCombat.delegateKillAuraAttacks,
            normalAttackPossible = normalAttackPossible,
            spearKillRunning = ModuleSpearKill.isKillAuraIntegrationArmed,
            spearKillTargetPossible = spearKillTargetPossible,
            superHitAvailable = ModuleSuperHit.running,
            superHitTargetPossible = superHitTargetPossible,
        )
    }

    private suspend fun handleUnavailableTarget(target: Entity) {
        if (KillAuraAutoBlock.enabled && KillAuraAutoBlock.onScanRange &&
            player.squaredBoxedDistanceTo(target) <= range.scanRange.sq()) {
            if (KillAuraClicker.ticksSinceLastClick >= KillAuraAutoBlock.reblockTicks) {
                KillAuraAutoBlock.startBlocking()
            }

            return
        }

        // Make sure we are not blocking
        val hasUnblocked = KillAuraAutoBlock.stopBlocking()
        if (hasUnblocked && KillAuraAutoBlock.pauseOnUnblockTicks > 0) {
            waitTicks = KillAuraAutoBlock.pauseOnUnblockTicks
        } else if (KillAuraFailSwing.enabled) {
            dealWithFakeSwing(target)
        }
    }

    private fun updateTarget() {
        // Calculate maximum range based on enemy distance
        val normalMaximumRange = if (targetTracker.closestSquaredEnemyDistance > range.interactionRange.sq()) {
            range.scanRange
        } else {
            extendedInteractionRange
        }

        when (val handoff = ModuleFightBot.targetHandoff) {
            FightBotTargetHandoff.Inactive -> Unit
            FightBotTargetHandoff.Idle -> {
                targetTracker.reset()
                return
            }
            is FightBotTargetHandoff.Locked -> {
                updateFightBotTarget(handoff.target, normalMaximumRange)
                return
            }
        }

        val maximumRange = calculateKillAuraTargetingRange(
            delegateKillAuraAttacks = GlobalSettingsCombat.delegateKillAuraAttacks,
            normalMaximumRange = normalMaximumRange,
            superHitAvailable = ModuleSuperHit.running,
            superHitMaximumRange = ModuleSuperHit.maximumTargetRange,
            spearKillRunning = ModuleSpearKill.isKillAuraIntegrationAvailable,
            spearKillMaximumRange = ModuleSpearKill.maximumTargetRange,
        )

        debugParameter("Maximum Range") { maximumRange }
        debugParameter("Range") { range }
        val squaredMaxRange = maximumRange.sq()
        val squaredNormalRange = extendedInteractionRange.sq()

        // Find a suitable target
        val target = targetTracker.targets()
            .filter { entity -> entity.squaredBoxedDistanceTo(player) <= squaredMaxRange }
            .sortedBy { entity ->
                killAuraAttackRoutePriority(entity.squaredBoxedDistanceTo(player), squaredNormalRange.toDouble())
            }
            .firstOrNull { entity ->
                val distantSpearKillTarget = isDistantSpearKillTarget(entity)
                val delegatedSuperHitTarget = !distantSpearKillTarget && shouldUseSuperHitFor(entity)
                if (!shouldUseKillAuraAimPipeline(distantSpearKillTarget, delegatedSuperHitTarget)) {
                    return@firstOrNull distantSpearKillTarget || canDispatchSuperHit(
                        entity,
                        calculateKillAuraDelegatedAttackRotation(player.eyePosition, entity.boundingBox),
                    )
                }

                val targeting = targetingParameters(
                    entity,
                    normalRange = normalMaximumRange,
                    normalWallsRange = range.interactionThroughWallsRange,
                ) ?: return@firstOrNull false

                processTarget(entity, targeting.range, targeting.wallsRange, targeting.allowAimThroughWalls)
            }

        if (target != null) {
            targetTracker.target = target
        } else {
            targetTracker.reset()
        }
    }

    private fun updateFightBotMovementRotation() {
        RotationManager.setRotationTarget(
            rotations.toRotationTarget(
                ModuleFightBot.getMovementRotation(),
                considerInventory = !ignoreOpenInventory
            ),
            priority = Priority.IMPORTANT_FOR_USAGE_2,
            provider = ModuleKillAura
        )
    }

    private fun updateFightBotTarget(fightBotTarget: LivingEntity, normalMaximumRange: Float) {
        targetTracker.target = fightBotTarget
        val targeting = targetingParameters(
            fightBotTarget,
            normalRange = normalMaximumRange,
            normalWallsRange = range.interactionThroughWallsRange,
        )

        if (targeting == null || !processTarget(
                fightBotTarget,
                targeting.range,
                targeting.wallsRange,
                targeting.allowAimThroughWalls,
            )
        ) {
            updateFightBotMovementRotation()
        }
    }

    @Suppress("ReturnCount")
    private fun processTarget(
        entity: LivingEntity,
        range: Float,
        wallsRange: Float,
        allowAimThroughWalls: Boolean = true,
    ): Boolean {
        val (rotation, _) = findRotation(entity, range, wallsRange, allowAimThroughWalls) ?: return false
        val ticks = rotations.calculateTicks(rotation)
        debugParameter("Rotation Ticks") { ticks }

        when (rotations.rotationTiming) {

            // If our click scheduler is not going to click the moment we reach the target,
            // we should not start aiming towards the target just yet.
            SNAP -> if (!clicker.willClickAt(ticks.coerceAtLeast(1))) {
                return true
            }

            // [ON_TICK] will always instantly aim onto the target on attack, however, if
            // our rotation is unable to be ready in time, we can at least start aiming towards
            // the target.
            ON_TICK -> if (ticks <= 1) {
                return true
            }

            else -> {
                // Continue with regular aiming
            }
        }

        RotationManager.setRotationTarget(
            rotations.toRotationTarget(
                rotation,
                entity,
                considerInventory = !ignoreOpenInventory
            ),
            priority = Priority.IMPORTANT_FOR_USAGE_2,
            provider = this@ModuleKillAura
        )
        return true
    }

    /**
     * Get the best spot to attack the entity
     *
     * @param entity The entity to attack
     * @param range The range to attack the entity (NOT SQUARED)
     *
     *  @return The best spot to attack the entity
     */
    private fun findRotation(
        entity: Entity,
        range: Float,
        wallsRange: Float,
        allowAimThroughWalls: Boolean = true,
    ): RotationWithVector? {
        val eyes = player.eyePosition
        val point = pointTracker.findPoint(eyes, entity)

        debugGeometry("Box") { ModuleDebug.DebuggedBox(point.box, Color4b.ORANGE.with(a = 90)) }
        debugGeometry("Point") { ModuleDebug.DebuggedPoint(point.pos, Color4b.WHITE, size = 0.1) }

        val rotationPreference = LeastDifferencePreference.leastDifferenceToLastPoint(eyes, point.pos)

        // raytrace to the point
        val rotation = raytraceBox(
            eyes = eyes,
            box = point.box,
            range = range.toDouble(),
            wallsRange = wallsRange.toDouble(),
            rotationPreference = rotationPreference
        )

        return if (rotation == null && allowAimThroughWalls && rotations.aimThroughWalls) {
            val rotationThroughWalls = raytraceBox(
                eyes = eyes,
                box = point.box,
                // Since [range] is squared, we need to square root
                range = range.toDouble(),
                wallsRange = range.toDouble(),
                rotationPreference = rotationPreference
            )

            rotationThroughWalls
        } else {
            rotation
        }
    }

    private fun targetingParameters(
        entity: LivingEntity,
        normalRange: Float,
        normalWallsRange: Float,
    ): TargetingParameters? {
        if (!shouldUseKillAuraAimPipeline(
                distantSpearKillTarget = isDistantSpearKillTarget(entity),
                delegatedSuperHitTarget = shouldUseSuperHitFor(entity),
            )
        ) {
            return null
        }

        return TargetingParameters(normalRange, normalWallsRange, allowAimThroughWalls = true)
            .takeIf { entity.squaredBoxedDistanceTo(player) <= normalRange.sq() }
    }

    /**
     * Check if we can attack the target at the current moment
     */
    internal fun canAttackNow(
        target: Entity? = null,
        itemStack: ItemStack = player.mainHandItem,
    ): Boolean {
        if (!itemStack.isItemEnabled(world.enabledFeatures())) {
            return false
        }

        if (player.cannotAttackWithItem(itemStack, 0)) {
            return false
        }

        val criticalHitAllowed = target == null || player.isFallFlying || criticalsSelectionMode.isCriticalHit(target)
        if (!criticalHitAllowed) {
            return false
        }

        val isInventoryBlockingAttack = (isInventoryOpen || isInContainerScreen) &&
            !ignoreOpenInventory && !simulateInventoryClosing
        return !isInventoryBlockingAttack
    }

    enum class RaycastMode(override val tag: String) : Tagged {
        TRACE_NONE("None"),
        TRACE_ONLYENEMY("Enemy"),
        TRACE_ALL("All")
    }

    private data class TargetingParameters(
        val range: Float,
        val wallsRange: Float,
        val allowAimThroughWalls: Boolean,
    )

}

internal fun shouldBlockSprintForCriticals(
    keepSprintEnabled: Boolean,
    elytraTargetRunning: Boolean,
    criticalsRequestSprintStop: Boolean,
): Boolean = !keepSprintEnabled && !elytraTargetRunning && criticalsRequestSprintStop

internal enum class KillAuraAttackRoute {
    NONE,
    NORMAL,
    SPEAR_KILL,
    SUPER_HIT,
}

internal fun selectKillAuraAttackRoute(
    delegateKillAuraAttacks: Boolean,
    normalAttackPossible: Boolean,
    spearKillRunning: Boolean = false,
    spearKillTargetPossible: Boolean = false,
    superHitAvailable: Boolean,
    superHitTargetPossible: Boolean,
): KillAuraAttackRoute = selectKillAuraSpearKillRoute(
    delegateKillAuraAttacks = delegateKillAuraAttacks,
    normalAttackPossible = normalAttackPossible,
    spearKillRunning = spearKillRunning,
    spearKillTargetPossible = spearKillTargetPossible,
    superHitAvailable = superHitAvailable,
    superHitTargetPossible = superHitTargetPossible,
)

internal fun calculateKillAuraTargetingRange(
    delegateKillAuraAttacks: Boolean,
    normalMaximumRange: Float,
    superHitAvailable: Boolean,
    superHitMaximumRange: Float,
    spearKillRunning: Boolean = false,
    spearKillMaximumRange: Float = 0f,
): Float = if (!delegateKillAuraAttacks) {
    normalMaximumRange
} else {
    maxOf(
        normalMaximumRange,
        superHitMaximumRange.takeIf { superHitAvailable } ?: 0f,
        spearKillMaximumRange.takeIf { spearKillRunning } ?: 0f,
    )
}

internal suspend fun executeKillAuraAttack(
    route: KillAuraAttackRoute,
    normalAttack: suspend () -> Boolean,
    superHitAttack: suspend () -> Boolean,
    onSuccess: () -> Unit,
): Boolean {
    val success = when (route) {
        KillAuraAttackRoute.NONE -> false
        KillAuraAttackRoute.NORMAL -> normalAttack()
        KillAuraAttackRoute.SPEAR_KILL -> false
        KillAuraAttackRoute.SUPER_HIT -> superHitAttack()
    }

    if (success) {
        onSuccess()
    }

    return success
}
