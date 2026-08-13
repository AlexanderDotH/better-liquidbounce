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
@file:Suppress("WildcardImport")

package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.once
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.features.module.modules.render.murdermystery.ModuleMurderMystery
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.entity.CachedPlayerSimulation
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulation
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulationCache
import net.ccbluex.liquidbounce.utils.entity.SimulatedArrow
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.OffhandReservationManager
import net.ccbluex.liquidbounce.utils.inventory.PlayerInventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.isPlayerInventory
import net.ccbluex.liquidbounce.utils.item.blocksAttacksComponent
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.network.releaseUsingItemInTickLoop
import net.ccbluex.liquidbounce.utils.network.sendPacketSilently
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.arrow.SpectralArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

@Suppress("TooManyFunctions")
object ModuleAutoDodge : ClientModule("AutoDodge", ModuleCategories.COMBAT) {
    private const val MIN_PACKET_DISTANCE = 0.9
    private const val MIN_PACKET_DISTANCE_SQ = MIN_PACKET_DISTANCE * MIN_PACKET_DISTANCE

    private object AllowRotationChange : ToggleableValueGroup(this, "AllowRotationChange", false) {
        val allowJump by boolean("AllowJump", true)
    }

    private object AllowTimer : ToggleableValueGroup(this, "AllowTimer", false) {
        val timerSpeed by float("TimerSpeed", 2.0F, 1.0F..10.0F, suffix = "x")
    }

    private object Spear : ToggleableValueGroup(this, "Spear", false) {
        val aimMargin by float("AimMargin", 0.75F, 0.0F..3.0F, suffix = "blocks")
        val jukeTicks by intRange("JukeTicks", 2..5, 1..10, suffix = "ticks")
        val threatMemory by int("ThreatMemory", 5, 0..20, suffix = "ticks")
        val teleport = SpearTeleportValueGroup(this, ModuleAutoDodge::resetSpearTeleport)

        fun movementSettings() = SpearMovementSettings(
            enabled = enabled,
            aimMargin = aimMargin.toDouble(),
            jukeTicks = jukeTicks,
            threatMemoryTicks = threatMemory,
            teleportEnabled = teleport.enabled,
            teleport = teleport.settings(),
        )

        object Shield : ToggleableValueGroup(this, "Shield", true) {
            val releaseDelay by int("ReleaseDelay", 3, 0..20, suffix = "ticks")
            val constraints = tree(
                PlayerInventoryConstraints(
                    startDelayDefault = 0..0,
                    clickDelayDefault = 0..0,
                    closeDelayDefault = 0..0,
                    missChanceDefault = 0..0,
                )
            )

            override fun onDisabled() {
                ModuleAutoDodge.disableSpearShield()
                super.onDisabled()
            }
        }

        init {
            tree(teleport)
            tree(Shield)
        }
    }

    private val ignore by multiEnumChoice("Ignore", Ignore.entries)

    private val spearMovementController = SpearMovementController()

    private var primarySpearThreat: SpearThreat? = null
    private var spearShieldState: SpearShieldState<ItemStack> = SpearShieldState.Idle
    private var pendingShieldInventoryCommand: SpearShieldCommand<ItemStack>? = null
    private var ownsOffhandReservation = false

    init {
        tree(AllowRotationChange)
        tree(AllowTimer)
        tree(Spear)
    }

    override val running: Boolean
        get() = shouldRunAutoDodgeHandlers(super.running, shieldCleanupPending)

    /** Keeps vanilla's key handler from releasing a shield use that AutoDodge currently owns. */
    @JvmStatic
    fun ownsSpearShieldUse(): Boolean = shouldPreserveAutoDodgeShieldUse(spearShieldState)

    /** Prevents vanilla from immediately restarting the item use AutoDodge is interrupting. */
    @JvmStatic
    fun suppressesVanillaSpearShieldUse(): Boolean = shouldSuppressAutoDodgeVanillaUse(spearShieldState)

    @Suppress("unused")
    val tickRep = handler<MovementInputEvent>(priority = SAFETY_FEATURE) { event ->
        val availability = resolveAutoDodgeBranchAvailability(runtimeContext())
        val canStartDefense = enabled && availability.spear
        val projectilePlan = if (enabled && availability.projectile) projectileDodgePlan() else null
        val spearMovement = spearMovementController.update(
            canStartDefense = canStartDefense,
            projectilePlanActive = projectilePlan != null,
            player = player,
            world = world,
            settings = Spear.movementSettings(),
        )
        primarySpearThreat = spearMovement.threat
        val action = AutoDodgeMovementArbitrator.chooseAction(
            projectilePlan,
            spearMovement.teleportPlan,
            spearMovement.jukePlan,
        )
        var teleported = false
        val dodgePlan = when (action) {
            is AutoDodgeMovementAction.Dodge -> action.plan
            is AutoDodgeMovementAction.Teleport -> {
                if (performSpearTeleport(action.plan)) {
                    event.directionalInput = DirectionalInput.NONE
                    teleported = true
                    null
                } else {
                    spearMovement.jukePlan?.asDodgePlan()
                }
            }
            AutoDodgeMovementAction.None -> null
        }

        // A teleport can change the shield arc immediately, so evaluate shield ownership at the final position.
        updateSpearShield(canStartDefense)
        debugSpearState()

        if (teleported) {
            return@handler
        }
        dodgePlan ?: return@handler

        event.directionalInput = dodgePlan.directionalInput

        dodgePlan.yawChange?.let { yawChange ->
            player.yRot = yawChange
        }

        if (dodgePlan.shouldJump && AllowRotationChange.allowJump && player.onGround()) {
            once<MovementInputEvent> { movementInputEvent ->
                movementInputEvent.jump = true
            }
        }

        if (AllowTimer.enabled && dodgePlan.useTimer) {
            Timer.requestTimerSpeed(AllowTimer.timerSpeed, Priority.IMPORTANT_FOR_PLAYER_LIFE, this@ModuleAutoDodge)
        }
    }

    private fun runtimeContext() = AutoDodgeRuntimeContext(
        blinkActive = ModuleBlink.running,
        inventoryBlocked = Ignore.OPEN_INVENTORY !in ignore &&
            (InventoryManager.isInventoryOpen || mc.gui.screen() is ContainerScreen),
        scaffoldBlocked = Ignore.USING_SCAFFOLD !in ignore && ModuleScaffold.running,
        usingItem = player.isUsingItem,
        allowWhileUsingItem = Ignore.USING_ITEM in ignore,
        murderMysteryDisallowsProjectile = ModuleMurderMystery.disallowsArrowDodge(),
        cleanupPending = shieldCleanupPending,
    )

    private fun projectileDodgePlan(): DodgePlan? {
        val arrows = world.findFlyingArrows()
        val simulatedPlayer = CachedPlayerSimulation(PlayerSimulationCache.getSimulationForLocalPlayer())
        val inflictedHit = getInflictedHits(
            simulatedPlayer,
            arrows,
            hitboxExpansion = DodgePlanner.SAFE_DISTANCE_WITH_PADDING,
        ) ?: return null

        return planEvasion(DodgePlannerConfig(allowRotations = AllowRotationChange.enabled), inflictedHit)
    }

    private fun performSpearTeleport(plan: SpearTeleportPlan): Boolean {
        return spearMovementController.executeTeleport(
            player = player,
            world = world,
            plan = plan,
            settings = Spear.teleport.settings(),
            sendPacket = { sendPacketSilently(it) },
        )
    }

    private fun updateSpearShield(canStartDefense: Boolean) {
        val threat = primarySpearThreat.takeIf {
            canStartDefense && Spear.enabled && Spear.Shield.enabled
        }
        val currentSession = spearShieldState.sessionOrNull()
        val selection = if (spearShieldState is SpearShieldState.Idle ||
            spearShieldState is SpearShieldState.Aborted) {
            threat?.let { findShieldRoute() }
        } else {
            null
        }
        val policy = currentSession?.policy ?: selection?.policy
        val aligned = threat != null && policy?.isAlignedWith(threat) == true
        val shieldUseActive = isShieldUseActive()
        val observation = shieldObservation(
            threatPresent = threat != null,
            aligned = aligned,
        )

        val transition = if (!enabled || !Spear.enabled || !Spear.Shield.enabled) {
            SpearShieldController.disable(spearShieldState, observation)
        } else if (selection != null && threat != null && aligned && !shieldUseActive) {
            if (selection.route.needsOffhandReservation() && !reserveOffhand()) {
                return
            }

            SpearShieldController.acquire(
                current = spearShieldState,
                request = SpearShieldAcquisition(
                    tick = player.tickCount.toLong(),
                    aligned = true,
                    route = selection.route,
                    usingItem = player.isUsingItem,
                    usingShield = false,
                    useKeyDown = mc.options.keyUse.isPressedOnAny,
                    policy = selection.policy,
                ),
            )
        } else {
            SpearShieldController.update(
                current = spearShieldState,
                observation = observation,
            )
        }

        applyShieldTransition(transition)
    }

    private fun findShieldRoute(): ShieldRouteSelection? {
        val equippedRoute = player.offhandItem.shieldPolicy()?.let { policy ->
            ShieldRouteSelection(
                SpearShieldRoute.AlreadyEquipped(SpearShieldHand.OFF_HAND),
                policy,
            )
        } ?: player.mainHandItem.shieldPolicy()?.let { policy ->
            ShieldRouteSelection(
                SpearShieldRoute.AlreadyEquipped(SpearShieldHand.MAIN_HAND),
                policy,
            )
        }
        if (equippedRoute != null) {
            return equippedRoute
        }

        return findInventoryShieldRoute()
    }

    private fun findInventoryShieldRoute(): ShieldRouteSelection? {
        if (!HotbarItemSlot.OFFHAND.canBeSwapTarget ||
            !player.containerMenu.isPlayerInventory ||
            OffhandReservationManager.isReservedByOther(this)) {
            return null
        }

        val sourceSlot = Slots.HotbarAndInventory.firstNotNullOfOrNull { slot ->
            slot.shieldPolicy()?.let { policy -> slot to policy }
        } ?: return null
        val sourceId = sourceSlot.first.getIdForServer(null) ?: return null
        val snapshot = SpearShieldInventorySnapshot(
            containerId = player.containerMenu.containerId,
            sourceSlot = sourceId,
            shieldStack = sourceSlot.first.itemStack.copy(),
            displacedOffhandStack = player.offhandItem.copy(),
        )

        return ShieldRouteSelection(SpearShieldRoute.SwapToOffhand(snapshot), sourceSlot.second)
    }

    private fun ItemSlot.shieldPolicy(): SpearShieldPolicy? = itemStack.shieldPolicy()

    private fun ItemStack.shieldPolicy(): SpearShieldPolicy? {
        if (!this.`is`(Items.SHIELD) || !isItemEnabled(world.enabledFeatures()) ||
            player.cooldowns.isOnCooldown(this)) {
            return null
        }

        val blocksAttacks = blocksAttacksComponent ?: return null
        return SpearShieldPolicy.from(blocksAttacks, Spear.Shield.releaseDelay)
    }

    private fun SpearShieldPolicy.isAlignedWith(threat: SpearThreat): Boolean = isAligned(
        serverYawDegrees = RotationManager.serverRotation.yRot,
        attackerDeltaX = threat.candidate.position.x - player.x,
        attackerDeltaZ = threat.candidate.position.z - player.z,
    )

    private fun shieldObservation(
        threatPresent: Boolean,
        aligned: Boolean,
    ) = SpearShieldObservation(
        tick = player.tickCount.toLong(),
        threatPresent = threatPresent,
        aligned = aligned,
        usingItem = player.isUsingItem,
        shieldUseActive = isShieldUseActive(),
        useKeyDown = mc.options.keyUse.isPressedOnAny,
        inventoryLayout = shieldInventoryLayout(),
    )

    private fun isShieldUseActive(): Boolean = player.isUsingItem &&
        player.useItem.blocksAttacksComponent != null

    private fun shieldInventoryLayout(): SpearShieldInventoryLayout {
        val snapshot = spearShieldState.sessionOrNull()?.swapSnapshot()
            ?: return SpearShieldInventoryLayout.NOT_REQUIRED
        val sourceSlot = findSnapshotSourceSlot(snapshot.sourceSlot)
            ?: return SpearShieldInventoryLayout.CHANGED
        val expectBrokenRestore = (spearShieldState as? SpearShieldState.Restoring)?.kind ==
            SpearShieldRestoreKind.AFTER_SHIELD_BREAK

        return snapshot.classify(
            containerId = player.containerMenu.containerId,
            sourceStack = sourceSlot.itemStack,
            offhandStack = player.offhandItem,
            stacksMatch = ItemStack::matches,
            isEmpty = ItemStack::isEmpty,
            expectBrokenShieldRestored = expectBrokenRestore,
        )
    }

    private fun findSnapshotSourceSlot(sourceSlot: Int): ItemSlot? =
        Slots.HotbarAndInventory.firstOrNull { it.getIdForServer(null) == sourceSlot }

    private fun applyShieldTransition(transition: SpearShieldTransition<ItemStack>) {
        spearShieldState = transition.state
        if (spearShieldState is SpearShieldState.Idle || spearShieldState is SpearShieldState.Aborted) {
            pendingShieldInventoryCommand = null
        }

        for (command in transition.commands) {
            if (!executeShieldCommand(command)) {
                break
            }
        }

        renewOrReleaseOffhandReservation()
    }

    private fun executeShieldCommand(command: SpearShieldCommand<ItemStack>): Boolean = when (command) {
        SpearShieldCommand.ReserveOffhand -> reserveOffhand()
        SpearShieldCommand.ReleaseItemUse -> {
            interaction.releaseUsingItemInTickLoop()
            true
        }
        is SpearShieldCommand.SwapIntoOffhand -> {
            pendingShieldInventoryCommand = command
            true
        }
        is SpearShieldCommand.StartShieldUse -> {
            startShieldUse(command.hand)
            true
        }
        SpearShieldCommand.StopShieldUse -> {
            interaction.releaseUsingItemInTickLoop()
            true
        }
        is SpearShieldCommand.RestoreOffhand -> {
            pendingShieldInventoryCommand = command
            true
        }
        SpearShieldCommand.ReleaseOffhandReservation -> {
            releaseOffhandReservation()
            true
        }
    }

    private fun startShieldUse(hand: SpearShieldHand) {
        val interactionHand = when (hand) {
            SpearShieldHand.MAIN_HAND -> InteractionHand.MAIN_HAND
            SpearShieldHand.OFF_HAND -> InteractionHand.OFF_HAND
        }
        if (player.getItemInHand(interactionHand).shieldPolicy() == null) {
            return
        }

        val rotation = RotationManager.serverRotation
        useItem(interactionHand, rotation.yRot, rotation.xRot)
    }

    private fun reserveOffhand(): Boolean {
        val reserved = OffhandReservationManager.reserve(
            owner = this,
            priority = Priority.IMPORTANT_FOR_USER_SAFETY,
        )
        ownsOffhandReservation = reserved
        return reserved
    }

    private fun renewOrReleaseOffhandReservation() {
        if (spearShieldState.needsOffhandReservation()) {
            if (!reserveOffhand()) {
                logger.warn("Lost AutoDodge spear shield offhand reservation during ${shieldStateName()}")
            }
            return
        }

        releaseOffhandReservation()
    }

    private fun releaseOffhandReservation() {
        if (ownsOffhandReservation) {
            OffhandReservationManager.release(this)
        }
        ownsOffhandReservation = false
    }

    @Suppress("unused")
    private val scheduleShieldInventoryHandler = handler<ScheduleInventoryActionEvent> { event ->
        val command = pendingShieldInventoryCommand ?: return@handler
        val snapshot = when (command) {
            is SpearShieldCommand.SwapIntoOffhand -> command.snapshot
            is SpearShieldCommand.RestoreOffhand -> command.snapshot
            else -> return@handler
        }
        val layout = shieldInventoryLayout()
        if (!canScheduleSpearShieldInventoryCommand(
                command,
                layout,
                reservedByModule = OffhandReservationManager.isReservedBy(this),
            )) {
            return@handler
        }

        val sourceSlot = findSnapshotSourceSlot(snapshot.sourceSlot) ?: return@handler
        event.schedule(
            Spear.Shield.constraints,
            InventoryAction.Click.performSwap(from = sourceSlot, to = HotbarItemSlot.OFFHAND),
            priority = Priority.IMPORTANT_FOR_USER_SAFETY,
        )
        pendingShieldInventoryCommand = null
    }

    private fun resetSpearMovement() {
        spearMovementController.resetMovement()
        primarySpearThreat = null
    }

    private fun resetSpearTeleport() {
        spearMovementController.resetTeleport()
    }

    private val shieldCleanupPending: Boolean
        get() = spearShieldState !is SpearShieldState.Idle &&
            spearShieldState !is SpearShieldState.Aborted

    private fun SpearShieldState<ItemStack>.sessionOrNull(): SpearShieldSession<ItemStack>? = when (this) {
        SpearShieldState.Idle -> null
        is SpearShieldState.Interrupting -> session
        is SpearShieldState.Equipping -> session
        is SpearShieldState.Blocking -> session
        is SpearShieldState.LoweredAwaitingRestore -> session
        is SpearShieldState.Restoring -> session
        is SpearShieldState.Aborted -> session
    }

    private fun SpearShieldSession<ItemStack>.swapSnapshot(): SpearShieldInventorySnapshot<ItemStack>? =
        (route as? SpearShieldRoute.SwapToOffhand)?.snapshot

    private fun SpearShieldRoute<ItemStack>.needsOffhandReservation(): Boolean = when (this) {
        is SpearShieldRoute.SwapToOffhand -> true
        is SpearShieldRoute.AlreadyEquipped -> hand == SpearShieldHand.OFF_HAND
    }

    private fun SpearShieldState<ItemStack>.needsOffhandReservation(): Boolean {
        if (this is SpearShieldState.Idle || this is SpearShieldState.Aborted) {
            return false
        }
        return sessionOrNull()?.route?.needsOffhandReservation() == true
    }

    private fun disableSpearShield() {
        if (mc.player == null) {
            resetSpearShieldForWorldChange()
            return
        }

        applyShieldTransition(
            SpearShieldController.disable(
                current = spearShieldState,
                observation = shieldObservation(threatPresent = false, aligned = false),
            )
        )
    }

    private fun resetSpearShieldForWorldChange() {
        spearShieldState = SpearShieldController.worldReset<ItemStack>().state
        pendingShieldInventoryCommand = null
        releaseOffhandReservation()
    }

    private fun shieldStateName(): String = when (val state = spearShieldState) {
        SpearShieldState.Idle -> "Idle"
        is SpearShieldState.Interrupting -> "Interrupting"
        is SpearShieldState.Equipping -> "Equipping"
        is SpearShieldState.Blocking -> "Blocking"
        is SpearShieldState.LoweredAwaitingRestore -> "LoweredAwaitingRestore"
        is SpearShieldState.Restoring -> "Restoring"
        is SpearShieldState.Aborted -> "Aborted/${state.reason}"
    }

    private fun debugSpearState() {
        debugParameter("Spear/Threat") {
            primarySpearThreat?.let { "${it.candidate.name}/${it.kind}" } ?: "-"
        }
        debugParameter("Spear/CommittedInput") {
            spearMovementController.jukeDecision?.plan?.input ?: DirectionalInput.NONE
        }
        debugParameter("Spear/CommittedTicks") {
            spearMovementController.jukeDecision?.ticksRemaining ?: 0
        }
        debugParameter("Spear/TeleportState") { spearMovementController.teleportState.debugName }
        debugParameter("Spear/TeleportDestination") {
            spearMovementController.plannedTeleport?.destination ?: "-"
        }
        debugParameter("Spear/ShieldState") { shieldStateName() }
        debugParameter("Spear/BlockReadyTick") {
            (spearShieldState as? SpearShieldState.Blocking)?.blockReadyAtTick ?: "-"
        }
        debugParameter("Spear/ShieldReady") {
            val blocking = spearShieldState as? SpearShieldState.Blocking
            blocking != null && isShieldUseActive() && blocking.session.policy.isReady(player.ticksUsingItem)
        }
        debugParameter("Spear/OffhandReservation") {
            when {
                OffhandReservationManager.isReservedBy(this) -> "AutoDodge"
                OffhandReservationManager.isReserved -> "Other"
                else -> "-"
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        resetSpearMovement()
        resetSpearTeleport()
        resetSpearShieldForWorldChange()
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        resetSpearMovement()
        resetSpearTeleport()
        resetSpearShieldForWorldChange()
    }

    override fun onDisabled() {
        resetSpearMovement()
        resetSpearTeleport()
        disableSpearShield()
        super.onDisabled()
    }

    private data class ShieldRouteSelection(
        val route: SpearShieldRoute<ItemStack>,
        val policy: SpearShieldPolicy,
    )

    private fun ClientLevel.findFlyingArrows() = entitiesForRendering().filter { entity ->
        (entity is Arrow || entity is SpectralArrow ||
                (entity is ThrownTrident && entity.clientSideReturnTridentTickCount == 0)) && !entity.isInGround
    }

    private fun <T : PlayerSimulation> getInflictedHits(
        simulatedPlayer: T,
        arrows: List<Entity>,
        maxTicks: Int = 80,
        hitboxExpansion: Double = 0.7,
    ): HitInfo? {
        val simulatedArrows = arrows.mapToArray {
            SimulatedArrow(it.level(), it.position(), it.deltaMovement, false)
        }

        for (i in 0 until maxTicks) {
            simulatedPlayer.tick()

            simulatedArrows.forEachIndexed { arrowIndex, arrow ->
                if (arrow.inGround) {
                    return@forEachIndexed
                }

                val lastPos = arrow.pos

                arrow.tick()

                val playerHitBox =
                    AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
                        .inflate(hitboxExpansion)
                        .move(simulatedPlayer.pos)
                val raycastResult = playerHitBox.clip(lastPos, arrow.pos)

                raycastResult.orElse(null)?.let { hitPos ->
                    return HitInfo(
                        tickDelta = i,
                        arrowEntity = arrows[arrowIndex],
                        hitPos = hitPos,
                        prevArrowPos = lastPos,
                        arrowVelocity = arrow.velocity,
                    )
                }
            }
        }

        return null
    }

    data class EvadingPacket(
        val idx: Int,
        /**
         * Ticks until impact. Null if evaded
         */
        val ticksToImpact: Int?
    )

    /**
     * Returns the index of the first position packet that avoids all arrows in the next X seconds
     */
    @Suppress("ReturnCount")
    fun findAvoidingArrowPosition(): EvadingPacket? {
        var packetIndex = 0

        var lastPosition: Vec3? = null

        var bestPacketPosition: Vec3? = null
        var bestPacketIdx: Int? = null
        var bestTimeToImpact = 0

        for (position in BlinkManager.positions) {
            packetIndex += 1

            // Process packets only if they are at least some distance away from each other
            if (lastPosition != null) {
                if (lastPosition.distanceToSqr(position) < MIN_PACKET_DISTANCE_SQ) {
                    continue
                }
            }

            lastPosition = position

            val inflictedHit = getInflictedHit(position)

            if (inflictedHit == null) {
                return EvadingPacket(packetIndex - 1, null)
            } else if (inflictedHit.tickDelta > bestTimeToImpact) {
                bestTimeToImpact = inflictedHit.tickDelta
                bestPacketIdx = packetIndex - 1
                bestPacketPosition = position
            }
        }

        // If the evading packet is less than one player hitbox away from the current position, we should rather
        // call the evasion a failure
        val packetPosition = bestPacketPosition
        if (bestPacketIdx != null && packetPosition != null &&
            packetPosition.distanceToSqr(player.position()) > MIN_PACKET_DISTANCE_SQ) {
            return EvadingPacket(bestPacketIdx, bestTimeToImpact)
        }

        return null
    }

    fun getInflictedHit(pos: Vec3): HitInfo? {
        val arrows = world.findFlyingArrows()
        val playerSimulation = PlayerSimulation.Rigid(pos)

        return getInflictedHits(playerSimulation, arrows, maxTicks = 40)
    }

    data class HitInfo(
        val tickDelta: Int,
        val arrowEntity: Entity,
        val hitPos: Vec3,
        val prevArrowPos: Vec3,
        val arrowVelocity: Vec3,
    )

    private enum class Ignore(
        override val tag: String
    ) : Tagged {
        OPEN_INVENTORY("OpenInventory"),
        USING_ITEM("UsingItem"),
        USING_SCAFFOLD("UsingScaffold")
    }
}

internal data class AutoDodgeRuntimeContext(
    val blinkActive: Boolean = false,
    val inventoryBlocked: Boolean = false,
    val scaffoldBlocked: Boolean = false,
    val usingItem: Boolean = false,
    val allowWhileUsingItem: Boolean = false,
    val murderMysteryDisallowsProjectile: Boolean = false,
    val cleanupPending: Boolean = false,
)

internal data class AutoDodgeBranchAvailability(
    val projectile: Boolean,
    val spear: Boolean,
    val cleanup: Boolean,
)

internal fun resolveAutoDodgeBranchAvailability(context: AutoDodgeRuntimeContext): AutoDodgeBranchAvailability {
    val commonAvailable = !context.blinkActive && !context.inventoryBlocked && !context.scaffoldBlocked
    return AutoDodgeBranchAvailability(
        projectile = commonAvailable && !context.murderMysteryDisallowsProjectile &&
            (!context.usingItem || context.allowWhileUsingItem),
        spear = commonAvailable,
        cleanup = context.cleanupPending,
    )
}

internal fun shouldRunAutoDodgeHandlers(
    moduleRunning: Boolean,
    shieldCleanupPending: Boolean,
): Boolean = moduleRunning || shieldCleanupPending

internal fun canScheduleSpearShieldInventoryCommand(
    command: SpearShieldCommand<*>,
    layout: SpearShieldInventoryLayout,
    reservedByModule: Boolean,
): Boolean {
    if (!reservedByModule) {
        return false
    }

    return when (command) {
        is SpearShieldCommand.SwapIntoOffhand -> layout == SpearShieldInventoryLayout.ORIGINAL
        is SpearShieldCommand.RestoreOffhand -> layout == SpearShieldInventoryLayout.EQUIPPED ||
            layout == SpearShieldInventoryLayout.SHIELD_BROKEN
        else -> false
    }
}

internal inline fun collectSpearMovementSimulation(
    tickCount: Int = SpearDodgePlanner.SIMULATION_TICKS,
    tick: () -> Unit,
    sample: () -> SpearMovementSample,
): SpearMovementSimulation {
    val samples = ArrayList<SpearMovementSample>(tickCount)
    repeat(tickCount) {
        tick()
        samples += sample()
    }
    return SpearMovementSimulation(samples)
}
