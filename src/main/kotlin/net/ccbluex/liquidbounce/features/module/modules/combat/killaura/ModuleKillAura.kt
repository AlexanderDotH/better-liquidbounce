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


import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.ValueGroupDeserializationRegistry
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.combat.contract.CombatRuntimeEnvironment
import net.ccbluex.liquidbounce.features.global.GlobalSettingsCombat
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.FightBotTargetHandoff
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isKillAuraIntegrationArmed
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isKillAuraIntegrationAvailable
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.maximumTargetRange
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.ownsKillAuraRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoWeapon
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleFightBot
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleMaceKill
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill
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
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.features.aiming.point.PointTracker
import net.ccbluex.liquidbounce.utils.aiming.preference.LeastDifferencePreference
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.features.combat.runtime.CombatManager
import net.ccbluex.liquidbounce.features.combat.runtime.attackEntity
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.features.inventory.InventoryManager.isInventoryOpen
import net.ccbluex.liquidbounce.utils.inventory.isInContainerScreen
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.render.target.TargetRenderer
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * KillAura module
 *
 * Automatically attacks enemies.
 */
@Suppress("MagicNumber")
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
    private var targetSelectionEvaluated = false

    init {
        ValueGroupDeserializationRegistry.register("kill-aura-range", name, 100) { group, values ->
            if (group === ModuleKillAura) range.migrateFromValues(values)
        }
        tree(KillAuraAutoBlock)
        tree(KillAuraVelocityHit)
        tree(TargetRenderer(this) {
            targetTracker.target?.takeUnless { ModuleElytraTarget.isSameTargetRendering(it) }
        })
        tree(KillAuraFailSwing)
        tree(KillAuraRangeIndicator)
        CombatRuntimeEnvironment.bindKillAuraTarget { running && targetTracker.target != null }
    }

    val extendedInteractionRange: Float
        get() = if (KillAuraVelocityHit.isVelocityHitPossible) {
            range.interactionRange + KillAuraVelocityHit.extendRange
        } else {
            range.interactionRange
        }

    internal fun shouldUseReachHitFor(target: LivingEntity): Boolean {
        return GlobalSettingsCombat.delegateKillAuraAttacks && ModuleReach.hit.running &&
            ModuleReach.hit.isTargetInConfiguredRange(target) &&
            player.squaredBoxedDistanceTo(target) > extendedInteractionRange.sq()
    }

    private val heldRemoteWeapon: KillAuraRemoteWeapon
        get() = when {
            player.mainHandItem.item == Items.MACE -> KillAuraRemoteWeapon.MACE
            player.mainHandItem.isSpear || player.offhandItem.isSpear -> KillAuraRemoteWeapon.SPEAR
            else -> KillAuraRemoteWeapon.NONE
        }

    private fun isMaceKillRouteTarget(target: LivingEntity): Boolean =
        ModuleMaceKill.canAcceptKillAuraTarget(target)

    private fun isDistantSpearKillTarget(target: LivingEntity): Boolean =
        target.squaredBoxedDistanceTo(player) > extendedInteractionRange.sq() &&
            ModuleSpearKill.canAcceptKillAuraTarget(target)

    private fun remoteKillRouteFor(target: LivingEntity, requireArmed: Boolean): KillAuraAttackRoute =
        selectKillAuraRemoteKillRoute(
            delegateKillAuraAttacks = GlobalSettingsCombat.delegateKillAuraAttacks,
            normalAttackPossible = false,
            heldRemoteWeapon = heldRemoteWeapon,
            maceKillAvailable = if (requireArmed) {
                ModuleMaceKill.isKillAuraIntegrationArmed
            } else {
                ModuleMaceKill.isKillAuraIntegrationAvailable
            },
            maceKillTargetPossible = isMaceKillRouteTarget(target),
            spearKillAvailable = if (requireArmed) {
                ModuleSpearKill.isKillAuraIntegrationArmed
            } else {
                ModuleSpearKill.isKillAuraIntegrationAvailable
            },
            spearKillTargetPossible = isDistantSpearKillTarget(target),
            reachHitAvailable = false,
            reachHitTargetPossible = false,
        )

    private fun isRemoteKillRouteTarget(target: LivingEntity): Boolean =
        remoteKillRouteFor(target, requireArmed = false).isRemoteKillRoute

    private fun delegatedAttackRotation(target: LivingEntity): Rotation? = when {
        isRemoteKillRouteTarget(target) -> player.rotation
        shouldUseReachHitFor(target) -> calculateKillAuraDelegatedAttackRotation(
            eyes = player.eyePosition,
            targetBox = target.boundingBox,
        )
        else -> null
    }

    private fun canDispatchReachHit(target: LivingEntity, rotation: Rotation): Boolean =
        isLookingAtEntity(
            toEntity = target,
            rotation = rotation,
            range = ModuleReach.hit.maximumTargetRange.toDouble(),
            throughWallsRange = 0.0,
        ) != null

    private fun shouldSuppressForRemoteKill(target: LivingEntity?): Boolean {
        val route = selectKillAuraSuppressionRoute(
            maceKillOwnsAttempt = ModuleMaceKill.ownsKillAuraRoute,
            maceFightBotReservation = ModuleMaceKill.reservesFightBotMaceUse(target),
            spearKillOwnsAttempt = ModuleSpearKill.ownsKillAuraRoute,
            spearFightBotReservation = ModuleSpearKill.reservesFightBotSpearUse(target),
            distantSpearKillTarget = target?.let(::isDistantSpearKillTarget) == true,
        )
        return selectKillAuraRemoteKillSuppressionPolicy(route).suppressAutoWeapon
    }

    /**
     * Narrow selection boundary consumed by SpearKill. KillAura retains ownership of filtering and
     * priority while SpearKill remains independent from CPS and click scheduling.
     */
    internal fun targetForSpearKill(): LivingEntity? {
        if (!canProvideRemoteKillSelection() || !ModuleSpearKill.isKillAuraIntegrationAvailable) return null

        val trackedTarget = targetTracker.target
        val trackedRoute = trackedTarget?.let { remoteKillRouteFor(it, requireArmed = false) }
        val trackedTargetOwnedByAnotherRoute = trackedTarget?.let { target ->
            target.squaredBoxedDistanceTo(player) <= extendedInteractionRange.sq() ||
                trackedRoute == KillAuraAttackRoute.MACE_KILL || shouldUseReachHitFor(target)
        } == true
        if (KillAuraSpearTargetSelectionSnapshot(
                selectionEvaluated = targetSelectionEvaluated,
                trackedTargetPresent = trackedTarget != null,
                trackedTargetValid = trackedTarget?.let(targetTracker::validate) == true,
                trackedTargetUsesSpearKill = trackedRoute == KillAuraAttackRoute.SPEAR_KILL,
                trackedTargetOwnedByAnotherRoute = trackedTargetOwnedByAnotherRoute,
                spearKillRouteActive = ModuleSpearKill.ownsKillAuraRoute,
            ).shouldReacquire
        ) {
            // Re-run KillAura's own filters/priority instead of bypassing them with SpearKill Combat.
            targetTracker.reset()
            updateTarget()
            targetSelectionEvaluated = true
        }

        return targetTracker.target?.takeIf {
            remoteKillRouteFor(it, requireArmed = false) == KillAuraAttackRoute.SPEAR_KILL
        }
    }

    /** Side-effect-free target handoff consumed by MaceKill's inherited route controller. */
    internal fun targetForMaceKill(): LivingEntity? {
        if (!canProvideRemoteKillSelection()) return null

        return targetTracker.target?.takeIf {
            remoteKillRouteFor(it, requireArmed = false) == KillAuraAttackRoute.MACE_KILL
        }
    }

    /** True only after KillAura has ruled out an ordinary melee target for this selection cycle. */
    internal fun shouldPrechargeForSpearKill(): Boolean {
        if (!canProvideRemoteKillSelection() ||
            ModuleFightBot.targetHandoff != FightBotTargetHandoff.Inactive
        ) {
            return false
        }

        val trackedTarget = targetTracker.target
        return shouldPrechargeKillAuraSpear(
            acquisitionAvailable = ModuleSpearKill.isKillAuraIntegrationAvailable &&
                (heldRemoteWeapon != KillAuraRemoteWeapon.MACE || !ModuleMaceKill.isKillAuraIntegrationAvailable),
            targetSelectionEvaluated = targetSelectionEvaluated,
            hasTrackedTarget = trackedTarget != null,
            trackedTargetUsesSpearKill = trackedTarget?.let {
                remoteKillRouteFor(it, requireArmed = false) == KillAuraAttackRoute.SPEAR_KILL
            } == true,
        )
    }

    private fun canProvideRemoteKillSelection(): Boolean {
        if (!running || !requirementsMet || CombatManager.shouldPauseCombat) return false
        val inventoryOpen = isInventoryOpen || isInContainerScreen
        return !inventoryOpen || ignoreOpenInventory
    }

    private fun shouldAdoptRaycastTarget(
        handoff: FightBotTargetHandoff,
        candidate: LivingEntity,
        trackedTarget: LivingEntity?,
    ): Boolean {
        if (handoff !== FightBotTargetHandoff.Inactive) return false
        if (ModuleMaceKill.ownsKillAuraRoute || ModuleSpearKill.ownsKillAuraRoute) return false
        return candidate.shouldBeAttacked() && candidate !== trackedTarget
    }

    override fun onDisabled() {
        ModuleMaceKill.onKillAuraDisabled()
        ModuleSpearKill.onKillAuraDisabled()
        targetTracker.reset()
        targetSelectionEvaluated = false
        waitTicks = 0
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
            targetSelectionEvaluated = false
            return@handler
        }

        // Update the current target tracker to make sure you attack the best enemy
        updateTarget()
        targetSelectionEvaluated = true

        // A remote-kill attempt owns its selected weapon and cannot be replaced mid-route.
        if (!shouldSuppressForRemoteKill(targetTracker.target)) {
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

        if (target != null && ModuleMaceKill.shouldExcludeKillAuraTarget(target)) {
            KillAuraAutoBlock.stopBlocking()
            return@tickHandler
        }

        val maceReserved = ModuleMaceKill.reservesFightBotMaceUse(target)
        val spearReserved = ModuleSpearKill.reservesFightBotSpearUse(target)
        if (CombatManager.shouldPauseCombat) {
            if (!ModuleMaceKill.ownsKillAuraRoute && !maceReserved &&
                !ModuleSpearKill.ownsKillAuraRoute && !spearReserved
            ) {
                KillAuraAutoBlock.stopBlocking()
            }
            return@tickHandler
        }

        if (ModuleMaceKill.ownsKillAuraRoute || maceReserved ||
            ModuleSpearKill.ownsKillAuraRoute || spearReserved
        ) {
            return@tickHandler
        }

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

        val livingAttackTarget = attackTarget as? LivingEntity
        if (livingAttackTarget != null && shouldAdoptRaycastTarget(handoff, livingAttackTarget, target)) {
            targetTracker.target = livingAttackTarget
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

    private suspend fun attackTarget(target: Entity, rotation: Rotation) {
        debugParameter("Rotation") { rotation }
        debugParameter("Target") { target.scoreboardName }

        val isInRange = isNormalAttackPossible(target, rotation)
        val reachHitTarget = target as? LivingEntity
        val selectedRoute = determineAttackRoute(reachHitTarget, rotation, isInRange)
        val attackRoute = resolveMaceKillAttackRoute(selectedRoute, reachHitTarget, rotation, isInRange)
        debugParameter("Attack Route") { attackRoute }
        if (attackRoute.isRemoteKillRoute) {
            return
        }

        // Make it seem like we are blocking for routes still owned by KillAura.
        KillAuraAutoBlock.makeSeemBlock()

        // Check if our target is in range, otherwise deal with auto block
        if (attackRoute == KillAuraAttackRoute.NONE) {
            handleUnavailableTarget(target)
            return
        }

        debugParameter("Valid Rotation") { rotation }

        val mainHandStack = player.mainHandItem
        val attackScheduled = clicker.isClickTick && canAttackNow(target, mainHandStack) &&
            !KillAuraAutoBlock.isPrioritizingBlocking
        if (attackScheduled) {
            clicker.prepareForAttack(rotation) {
                executeSelectedAttack(target, reachHitTarget, rotation, attackRoute, mainHandStack)
            }
            return
        }
        if (KillAuraClicker.ticksSinceLastClick >= KillAuraAutoBlock.reblockTicks) {
            KillAuraAutoBlock.startBlocking()
        }
    }

    private suspend fun executeSelectedAttack(
        target: Entity,
        reachHitTarget: LivingEntity?,
        rotation: Rotation,
        attackRoute: KillAuraAttackRoute,
        mainHandStack: ItemStack,
    ): Boolean {
        if (!canAttackNow(target, mainHandStack)) return false

        val attackKeepSprint = keepSprint && !shouldBlockSprinting
        return executeKillAuraAttack(
            route = attackRoute,
            normalAttack = {
                attackEntity(target, SwingMode.DO_NOT_HIDE, attackKeepSprint)
                true
            },
            reachHitAttack = {
                val livingTarget = reachHitTarget ?: return@executeKillAuraAttack false
                ModuleReach.hit.tryAttack(
                    livingTarget,
                    rotation,
                    attackKeepSprint,
                    automatedByKillAura = true,
                )
            },
            onSuccess = { recordSuccessfulAttack(target) },
        )
    }

    private fun recordSuccessfulAttack(target: Entity) {
        range.update()
        KillAuraNotifyWhenFail.failedHitsIncrement = 0
        KillAuraAutoBlock.hasBlockedSinceAttack = false
        GenericDebugRecorder.recordDebugInfo(ModuleKillAura, "attackEntity", JsonObject().apply {
            add("player", GenericDebugRecorder.debugObject(player))
            add("targetPos", GenericDebugRecorder.debugObject(target))
        })
    }

    /** Launches MaceKill in this tick and resolves an ordinary, SpearKill, or Reach Hit fallback. */
    private fun resolveMaceKillAttackRoute(
        selectedRoute: KillAuraAttackRoute,
        target: LivingEntity?,
        rotation: Rotation,
        normalAttackPossible: Boolean,
    ): KillAuraAttackRoute = resolveKillAuraMaceLaunch(
        selectedRoute = selectedRoute,
        launchMaceKill = { target?.let(ModuleMaceKill::requestKillAuraMaceKill) == true },
        fallbackRoute = {
            determineAttackRoute(
                reachHitTarget = target,
                rotation = rotation,
                normalAttackPossible = normalAttackPossible,
                allowMaceKill = false,
            )
        },
    )

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
        reachHitTarget: LivingEntity?,
        rotation: Rotation,
        normalAttackPossible: Boolean,
        allowMaceKill: Boolean = true,
    ): KillAuraAttackRoute {
        val maceKillTargetPossible = allowMaceKill && reachHitTarget != null &&
            isMaceKillRouteTarget(reachHitTarget)
        val spearKillTargetPossible = !normalAttackPossible && reachHitTarget != null &&
            isDistantSpearKillTarget(reachHitTarget)
        val reachHitTargetPossible = !normalAttackPossible && !maceKillTargetPossible &&
            !spearKillTargetPossible && reachHitTarget != null &&
            shouldUseReachHitFor(reachHitTarget) && canDispatchReachHit(reachHitTarget, rotation)

        return selectKillAuraAttackRoute(
            delegateKillAuraAttacks = GlobalSettingsCombat.delegateKillAuraAttacks,
            normalAttackPossible = normalAttackPossible,
            heldRemoteWeapon = heldRemoteWeapon,
            maceKillAvailable = ModuleMaceKill.isKillAuraIntegrationArmed,
            maceKillTargetPossible = maceKillTargetPossible,
            spearKillRunning = ModuleSpearKill.isKillAuraIntegrationArmed,
            spearKillTargetPossible = spearKillTargetPossible,
            reachHitAvailable = ModuleReach.hit.running,
            reachHitTargetPossible = reachHitTargetPossible,
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
        val normalMaximumRange = if (targetTracker.closestSquaredEnemyDistance > range.interactionRange.sq()) {
            range.scanRange
        } else {
            extendedInteractionRange
        }
        if (updateFromFightBotHandoff(normalMaximumRange)) return

        val maximumRange = calculateKillAuraTargetingRange(
            delegateKillAuraAttacks = GlobalSettingsCombat.delegateKillAuraAttacks,
            normalMaximumRange = normalMaximumRange,
            reachHitAvailable = ModuleReach.hit.running,
            reachHitMaximumRange = ModuleReach.hit.maximumTargetRange,
            spearKillRunning = ModuleSpearKill.isKillAuraIntegrationAvailable,
            spearKillMaximumRange = ModuleSpearKill.maximumTargetRange,
            maceKillRunning = ModuleMaceKill.isKillAuraIntegrationAvailable,
            maceKillMaximumRange = ModuleMaceKill.maximumTargetRange,
        )

        debugParameter("Maximum Range") { maximumRange }
        debugParameter("Range") { range }
        val squaredMaxRange = maximumRange.sq()
        val squaredNormalRange = extendedInteractionRange.sq()
        val target = targetTracker.targets()
            .filter { entity -> entity.squaredBoxedDistanceTo(player) <= squaredMaxRange }
            .filterNot(ModuleMaceKill::shouldExcludeKillAuraTarget)
            .sortedBy { entity ->
                killAuraAttackRoutePriority(
                    entity.squaredBoxedDistanceTo(player),
                    squaredNormalRange.toDouble(),
                )
            }
            .firstOrNull { entity -> canTarget(entity, normalMaximumRange) }

        if (target != null) {
            targetTracker.target = target
        } else {
            targetTracker.reset()
        }
    }

    private fun updateFromFightBotHandoff(normalMaximumRange: Float): Boolean =
        when (val handoff = ModuleFightBot.targetHandoff) {
            FightBotTargetHandoff.Inactive -> false
            FightBotTargetHandoff.Idle -> {
                targetTracker.reset()
                true
            }
            is FightBotTargetHandoff.Locked -> {
                updateFightBotTarget(handoff.target, normalMaximumRange)
                true
            }
        }

    private fun canTarget(entity: LivingEntity, normalMaximumRange: Float): Boolean {
        val remoteKillTarget = isRemoteKillRouteTarget(entity)
        val delegatedReachHitTarget = !remoteKillTarget && shouldUseReachHitFor(entity)
        if (!shouldUseKillAuraAimPipeline(remoteKillTarget, delegatedReachHitTarget)) {
            return remoteKillTarget || canDispatchReachHit(
                entity,
                calculateKillAuraDelegatedAttackRotation(player.eyePosition, entity.boundingBox),
            )
        }

        val targeting = targetingParameters(
            entity,
            normalRange = normalMaximumRange,
            normalWallsRange = range.interactionThroughWallsRange,
        ) ?: return false
        return processTarget(entity, targeting.range, targeting.wallsRange, targeting.allowAimThroughWalls)
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
                delegatedRemoteKillTarget = isRemoteKillRouteTarget(entity),
                delegatedReachHitTarget = shouldUseReachHitFor(entity),
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
