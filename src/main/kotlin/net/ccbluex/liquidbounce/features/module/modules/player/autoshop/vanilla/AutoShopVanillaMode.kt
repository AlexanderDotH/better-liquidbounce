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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantOfferMatcher
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantPlanningStep
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantReachValue
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantRoundRobinPass
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantRoundRobinPlanner
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantTradeFiltersValue
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantTradeRule
import net.ccbluex.liquidbounce.utils.aiming.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.entity.interactEntity
import net.ccbluex.liquidbounce.utils.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.entity.npc.villager.AbstractVillager
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem
import kotlin.math.sqrt

@Suppress("TooManyFunctions")
object AutoShopVanillaMode : Mode("Vanilla") {

    private val tradeFilters = value(MerchantTradeFiltersValue("Trades"))
    private val reach = value(MerchantReachValue("Reach"))
    private val cps by intRange("CPS", 4..8, 1..20, "clicks")
    private val rotations = tree(RotationsValueGroup(this))

    private val session = MerchantSessionCoordinator()
    private val cpsGate = MerchantCpsGate()
    private val abandonedOpeningGuard = MerchantAbandonedOpeningGuard(
        MerchantSessionCoordinator.DEFAULT_TIMEOUT_TICKS,
    )
    private val feedbackGate = MerchantTradeFeedbackGate()
    private val planningStepCache = MerchantPlanningStepCache()

    private var roundRobinPass: MerchantRoundRobinPass? = null
    private var ownedMenu: MerchantMenu? = null
    private var sendingOwnedInteraction = false
    private var suppressAcquisitionUntilTick = Int.MIN_VALUE

    override val parent: ModeValueGroup<Mode>
        get() = ModuleAutoShop.modes

    @Suppress("unused")
    private val rotationHandler = handler<RotationUpdateEvent> {
        val localPlayer = mc.player ?: return@handler
        val currentTick = localPlayer.tickCount
        if (yieldToUserInteraction(currentTick)) {
            return@handler
        }

        val target = when (val current = session.state) {
            MerchantSessionState.Idle -> acquireTarget(currentTick)
            is MerchantSessionState.Rotating -> merchant(current.targetId)
            else -> null
        } ?: return@handler

        val spot = eligibleRotationSpot(target)
        if (spot == null) {
            finishSession(MerchantSessionEndCause.TARGET_LOST, currentTick)
            return@handler
        }

        requestInteractionRotation(target, spot)
    }

    @Suppress("unused")
    private val gameTickHandler = handler<GameTickEvent> {
        val localPlayer = mc.player ?: return@handler
        if (yieldToUserInteraction(localPlayer.tickCount)) {
            return@handler
        }

        val current = session.state
        if (current === MerchantSessionState.Idle) {
            return@handler
        }

        when {
            mc.gui.screen() != null ->
                finishSession(MerchantSessionEndCause.UNEXPECTED_GUI, localPlayer.tickCount)
            session.hasTimedOut(localPlayer.tickCount) ->
                finishSession(MerchantSessionEndCause.TIMEOUT, localPlayer.tickCount)
            !lockedTargetIsValid() ->
                finishSession(MerchantSessionEndCause.TARGET_LOST, localPlayer.tickCount)
            else -> when (current) {
                is MerchantSessionState.AwaitingOffers -> awaitOffers(current, localPlayer.tickCount)
                is MerchantSessionState.Trading -> trade(current, localPlayer.tickCount)
                else -> Unit
            }
        }
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent>(priority = FIRST_PRIORITY) { event ->
        val screen = event.screen
        if (screen is MerchantScreen && discardAbandonedMerchantScreen(screen)) {
            event.cancelEvent()
            return@handler
        }

        if (screen is MerchantScreen && claimOwnedScreen(screen)) {
            event.cancelEvent()
            return@handler
        }

        if (screen != null && session.state !== MerchantSessionState.Idle) {
            finishSession(MerchantSessionEndCause.UNEXPECTED_GUI, mc.player?.tickCount ?: 0)
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = FIRST_PRIORITY) { event ->
        when (val packet = event.packet) {
            is ServerboundInteractPacket -> handleInteractionPacket(event.origin, packet)
            is ServerboundUseItemOnPacket,
            is ServerboundUseItemPacket -> handleUserInteractionPacket(event.origin)
            is ClientboundOpenScreenPacket -> handleOpenScreenPacket(event.origin, packet)
            is ClientboundMerchantOffersPacket -> handleMerchantOffersPacket(event.origin, packet)
            is ClientboundContainerClosePacket -> handleServerClose(event.origin, packet)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        endSession(MerchantSessionEndCause.WORLD_CHANGE)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        endSession(MerchantSessionEndCause.DISCONNECT)
    }

    override fun disable() {
        endSession(MerchantSessionEndCause.DISABLE_OR_MODE_SWITCH)
    }

    private fun acquireTarget(tick: Int): AbstractVillager? {
        val localPlayer = mc.player ?: return null
        val canAcquire = MerchantAcquisitionPolicy.canAcquire(
            tick = tick,
            suppressedUntilTick = suppressAcquisitionUntilTick,
            guiOpen = mc.gui.screen() != null,
            inventoryMenuActive = localPlayer.containerMenu === localPlayer.inventoryMenu,
            safeHandAvailable = safeInteractionHand() != null,
            hasActiveRule = tradeFilters.get().any { it.isActive },
            interactionInputActive = interactionInputActive(),
        )
        if (!canAcquire) {
            return null
        }

        val level = mc.level ?: return null
        val reachSetting = reach.get()
        val candidates = level.getEntitiesOfClass(
            AbstractVillager::class.java,
            localPlayer.boundingBox.inflate(reachSetting.range.toDouble()),
        ).map { merchant ->
            MerchantTargetCandidate(
                entity = merchant,
                entityId = merchant.id,
                boxedDistance = sqrt(merchant.boundingBox.distanceToSqr(localPlayer.eyePosition)),
                visible = false,
                alive = merchant.isAlive && !merchant.isRemoved,
                adult = !merchant.isBaby,
                sleeping = merchant.isSleeping,
            )
        }

        val selected = MerchantTargetSelector.selectReachable(
            candidates,
            reachSetting.range,
            canRetry = { session.canRetry(it, tick) },
        ) { merchant ->
            raytraceBox(
                eyes = localPlayer.eyePosition,
                box = merchant.boundingBox,
                range = reachSetting.range.toDouble(),
                wallsRange = reachSetting.wallRange.toDouble(),
            ) != null
        } ?: return null

        return selected.entity.takeIf { session.tryLock(selected.entityId, tick) }
    }

    private fun requestInteractionRotation(target: AbstractVillager, spot: RotationWithVector) {
        val targetId = target.id
        val whenReached = RestrictedSingleUseAction(
            { canOpenAfterRotation(targetId) },
            { queueInteraction(targetId) },
        )

        RotationManager.setRotationTarget(
            rotations.toRotationTarget(
                spot.rotation,
                entity = target,
                considerInventory = false,
                whenReached = whenReached,
            ),
            Priority.IMPORTANT_FOR_USAGE_1,
            ModuleAutoShop,
        )
    }

    private fun canOpenAfterRotation(targetId: Int): Boolean {
        if (interactionInputActive()) {
            return false
        }

        val target = merchant(targetId) ?: return false
        val spot = rotationSpot(target) ?: return false
        val angleDifference = RotationManager.serverRotation.directionAngleTo(spot.rotation)
        return MerchantRotationGate.canInteract(session.state, targetId, angleDifference, AIM_THRESHOLD)
    }

    private fun queueInteraction(targetId: Int) {
        PostRotationExecutor.addTask(ModuleAutoShop, postMove = true, priority = true) {
            openMerchant(targetId)
        }
    }

    private fun openMerchant(targetId: Int) {
        val localPlayer = mc.player ?: return
        if (yieldToUserInteraction(localPlayer.tickCount)) {
            return
        }

        val target = merchant(targetId)
            ?: return finishSession(MerchantSessionEndCause.TARGET_LOST, localPlayer.tickCount)
        val spot = eligibleRotationSpot(target)
        if (mc.gui.screen() != null || spot == null) {
            finishSession(MerchantSessionEndCause.TARGET_LOST, localPlayer.tickCount)
            return
        }

        if (RotationManager.serverRotation.directionAngleTo(spot.rotation) > AIM_THRESHOLD) {
            return
        }

        val hand = safeInteractionHand()
            ?: return finishSession(MerchantSessionEndCause.TRADE_BLOCKED, localPlayer.tickCount)

        if (!session.markInteractionSent(targetId, localPlayer.tickCount)) {
            return
        }

        sendingOwnedInteraction = true
        val result = try {
            interactEntity(
                target,
                net.minecraft.world.phys.EntityHitResult(target, spot.vec),
                hand,
                SwingMode.DO_NOT_HIDE,
            )
        } finally {
            sendingOwnedInteraction = false
        }

        if (result?.consumesAction() != true) {
            finishSession(MerchantSessionEndCause.TRADE_BLOCKED, localPlayer.tickCount)
        }
    }

    private fun handleInteractionPacket(origin: TransferOrigin, packet: ServerboundInteractPacket) {
        if (origin != TransferOrigin.OUTGOING) {
            return
        }

        if (sendingOwnedInteraction && packet.entityId == session.targetId) {
            return
        }

        prioritizeUserInteraction()
    }

    private fun handleUserInteractionPacket(origin: TransferOrigin) {
        if (origin == TransferOrigin.OUTGOING) {
            prioritizeUserInteraction()
        }
    }

    private fun handleOpenScreenPacket(origin: TransferOrigin, packet: ClientboundOpenScreenPacket) {
        if (origin != TransferOrigin.INCOMING || session.state !is MerchantSessionState.Opening) {
            return
        }

        mc.execute {
            if (!running || session.state !is MerchantSessionState.Opening) {
                return@execute
            }

            val tick = mc.player?.tickCount ?: return@execute
            if (packet.type == MenuType.MERCHANT) {
                session.expectMerchantContainer(packet.containerId, tick)
            } else {
                prioritizeUserInteraction(tick)
            }
        }
    }

    private fun handleServerClose(origin: TransferOrigin, packet: ClientboundContainerClosePacket) {
        if (origin != TransferOrigin.INCOMING) {
            return
        }

        mc.execute {
            if (session.isOwnedContainer(packet.containerId)) {
                endSession(MerchantSessionEndCause.SERVER_CLOSE)
            }
        }
    }

    private fun handleMerchantOffersPacket(origin: TransferOrigin, packet: ClientboundMerchantOffersPacket) {
        if (origin != TransferOrigin.INCOMING) {
            return
        }

        mc.execute {
            if (session.isOwnedContainer(packet.containerId)) {
                planningStepCache.invalidate()
            }
        }
    }

    private fun prioritizeUserInteraction(tick: Int = mc.player?.tickCount ?: 0) {
        val wasOpening = session.state is MerchantSessionState.Opening
        abandonedOpeningGuard.remember(wasOpening, tick)
        val graceTicks = if (wasOpening) {
            MerchantSessionCoordinator.DEFAULT_TIMEOUT_TICKS
        } else {
            USER_INTERACTION_GRACE_TICKS
        }
        suppressAcquisitionUntilTick = maxOf(suppressAcquisitionUntilTick, tick + graceTicks)
        if (session.state !== MerchantSessionState.Idle) {
            finishSession(MerchantSessionEndCause.USER_INTERACTION, tick)
        }
    }

    private fun yieldToUserInteraction(tick: Int): Boolean {
        if (!interactionInputActive()) {
            return false
        }

        prioritizeUserInteraction(tick)
        return true
    }

    private fun interactionInputActive(): Boolean =
        mc.options.keyUse.isPressedOnAny ||
            mc.options.keyAttack.isPressedOnAny ||
            mc.options.keyPickItem.isPressedOnAny

    private fun discardAbandonedMerchantScreen(screen: MerchantScreen): Boolean {
        val localPlayer = mc.player ?: return false
        if (localPlayer.containerMenu !== screen.menu ||
            !abandonedOpeningGuard.consumeMerchantScreen(localPlayer.tickCount)) {
            return false
        }

        localPlayer.closeContainer()
        return true
    }

    private fun claimOwnedScreen(screen: MerchantScreen): Boolean {
        val localPlayer = mc.player ?: return false
        val menu = screen.menu
        if (!MerchantScreenClaimPolicy.canClaim(running, localPlayer.containerMenu === menu) ||
            !session.claimMerchantScreen(menu.containerId, localPlayer.tickCount)) {
            return false
        }

        ownedMenu = menu
        return true
    }

    private fun awaitOffers(state: MerchantSessionState.AwaitingOffers, tick: Int) {
        val menu = currentOwnedMenu(state.containerId)
            ?: return finishSession(MerchantSessionEndCause.TARGET_LOST, tick)
        if (menu.offers.isEmpty()) {
            return
        }

        if (session.markOffersReady(menu.containerId, tick)) {
            roundRobinPass = MerchantRoundRobinPass.start(tradeFilters.get().size)
            cpsGate.reset()
            planningStepCache.invalidate()
        }
    }

    private fun trade(state: MerchantSessionState.Trading, tick: Int) {
        val menu = currentOwnedMenu(state.containerId)
            ?: return finishSession(MerchantSessionEndCause.TARGET_LOST, tick)
        val rules = tradeFilters.get()
        if (rules.none { it.isActive } || !menu.carried.isEmpty) {
            finishSession(MerchantSessionEndCause.TRADE_BLOCKED, tick)
            return
        }

        val pass = roundRobinPass ?: MerchantRoundRobinPass.start(rules.size).also { roundRobinPass = it }
        val step = planningStepCache.getOrPlan {
            MerchantRoundRobinPlanner.next(pass, rules, menu.offers) { canExecute(menu, it) }
        }
        if (MerchantTradeCadencePolicy.shouldWaitForCps(step, cpsGate.canAttempt(tick))) {
            return
        }
        planningStepCache.invalidate()

        when (step) {
            is MerchantPlanningStep.Attempt -> {
                if (!isCurrentAttemptExecutable(menu, rules, step)) {
                    return
                }

                val output = menu.offers.getOrNull(step.trade.offerIndex)?.result?.copy()
                val successful = executeSingleTrade(menu, step.trade.offerIndex)
                roundRobinPass = step.recordOutcome(successful)
                cpsGate.recordAttempt(tick, cps)
                if (successful && output != null) {
                    notifyPurchase(output)
                }
                if (!menu.carried.isEmpty) {
                    finishSession(MerchantSessionEndCause.TRADE_BLOCKED, tick)
                }
            }
            is MerchantPlanningStep.PassComplete -> {
                if (step.anySuccess) {
                    roundRobinPass = MerchantRoundRobinPass.start(rules.size)
                } else {
                    notifyInsufficientResources(menu, rules)
                    finishSession(MerchantSessionEndCause.TRADE_BLOCKED, tick)
                }
            }
        }
    }

    private fun isCurrentAttemptExecutable(
        menu: MerchantMenu,
        rules: List<MerchantTradeRule>,
        step: MerchantPlanningStep.Attempt,
    ): Boolean {
        val rule = rules.getOrNull(step.trade.ruleIndex) ?: return false
        val offer = menu.offers.getOrNull(step.trade.offerIndex) ?: return false
        return MerchantOfferMatcher.matches(rule, offer) && canExecute(menu, offer)
    }

    private fun canExecute(menu: MerchantMenu, offer: MerchantOffer): Boolean {
        if (!menu.carried.isEmpty) {
            return false
        }

        val inventory = menu.slots.subList(PLAYER_INVENTORY_START, PLAYER_INVENTORY_END)
            .map { it.item }
        val payments = menu.slots.subList(PAYMENT_START, PAYMENT_END)
            .map { it.item }
        return MerchantTradeFeasibility.canExecute(offer, inventory, payments)
    }

    private fun notifyPurchase(result: ItemStack) {
        if (!feedbackGate.shouldNotifyPurchase()) {
            return
        }

        notification(
            title = "AutoShop",
            message = "Bought ${result.count} × ${result.hoverName.string}",
            severity = NotificationEvent.Severity.SUCCESS,
        )
    }

    private fun notifyInsufficientResources(
        menu: MerchantMenu,
        rules: List<MerchantTradeRule>,
    ) {
        val inventory = menu.slots.subList(PLAYER_INVENTORY_START, PLAYER_INVENTORY_END).map { it.item }
        val payments = menu.slots.subList(PAYMENT_START, PAYMENT_END).map { it.item }
        val cannotPay = rules.any { rule ->
            menu.offers.any { offer ->
                MerchantOfferMatcher.matches(rule, offer) && MerchantTradeFeasibility.evaluate(
                    offer,
                    inventory,
                    payments,
                ) == MerchantTradeFeasibilityResult.INSUFFICIENT_RESOURCES
            }
        }
        if (!cannotPay) {
            return
        }

        notification(
            title = "AutoShop",
            message = "Not enough resources to pay for the configured trade",
            severity = NotificationEvent.Severity.ERROR,
        )
    }

    private fun executeSingleTrade(menu: MerchantMenu, offerIndex: Int): Boolean {
        val offer = menu.offers.getOrNull(offerIndex) ?: return false
        val previousUses = offer.uses

        menu.setSelectionHint(offerIndex)
        menu.tryMoveItems(offerIndex)
        network.send(ServerboundSelectTradePacket(offerIndex))
        if (!menu.getSlot(RESULT_SLOT).hasItem() || !menu.carried.isEmpty) {
            return false
        }

        interaction.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.PICKUP, player)
        val traded = offer.uses > previousUses
        if (!depositCarriedOutput(menu)) {
            return false
        }
        return traded
    }

    private fun depositCarriedOutput(menu: MerchantMenu): Boolean {
        val inventory = menu.slots.subList(PLAYER_INVENTORY_START, PLAYER_INVENTORY_END).map { it.item }
        val plan = MerchantOutputDepositPlanner.plan(menu.carried, inventory, PLAYER_INVENTORY_START)
        if (!plan.complete) {
            return false
        }

        for (slot in plan.destinationSlots) {
            val previousCount = menu.carried.count
            interaction.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player)
            if (!menu.carried.isEmpty && menu.carried.count >= previousCount) {
                return false
            }
        }

        return menu.carried.isEmpty
    }

    private fun lockedTargetIsValid(): Boolean {
        val target = session.targetId?.let(::merchant) ?: return false
        return eligibleRotationSpot(target) != null
    }

    private fun eligibleRotationSpot(target: AbstractVillager): RotationWithVector? {
        if (!target.isAlive || target.isRemoved || target.isBaby || target.isSleeping) {
            return null
        }

        return rotationSpot(target)
    }

    private fun rotationSpot(target: AbstractVillager): RotationWithVector? {
        val localPlayer = mc.player ?: return null
        val reachSetting = reach.get()
        return raytraceBox(
            eyes = localPlayer.eyePosition,
            box = target.boundingBox,
            range = reachSetting.range.toDouble(),
            wallsRange = reachSetting.wallRange.toDouble(),
        )
    }

    private fun safeInteractionHand(): InteractionHand? {
        val localPlayer = mc.player ?: return null
        return InteractionHand.entries.firstOrNull { localPlayer.getItemInHand(it).isEmpty }
            ?: InteractionHand.entries.firstOrNull { hand ->
                val item = localPlayer.getItemInHand(hand).item
                item !== Items.NAME_TAG && item !is SpawnEggItem
            }
    }

    private fun merchant(entityId: Int): AbstractVillager? = mc.level?.getEntity(entityId) as? AbstractVillager

    private fun currentOwnedMenu(containerId: Int): MerchantMenu? {
        val menu = ownedMenu ?: return null
        val localPlayer = mc.player ?: return null
        return menu.takeIf { it.containerId == containerId && localPlayer.containerMenu === it }
    }

    private fun finishSession(cause: MerchantSessionEndCause, tick: Int) {
        endSession(cause, tick)
    }

    private fun endSession(
        cause: MerchantSessionEndCause,
        tick: Int = mc.player?.tickCount ?: 0,
    ) {
        val decision = MerchantCleanupPolicy.forCause(cause)
        val menuToClose = ownedMenu
        ownedMenu = null
        roundRobinPass = null
        planningStepCache.invalidate()
        cpsGate.reset()
        feedbackGate.reset()
        sendingOwnedInteraction = false

        if (decision.rememberRetry) {
            session.finish(tick)
        } else {
            session.resetAll()
            abandonedOpeningGuard.reset()
            suppressAcquisitionUntilTick = Int.MIN_VALUE
        }

        val localPlayer = mc.player
        if (decision.closeOwnedMenu && menuToClose != null && localPlayer?.containerMenu === menuToClose) {
            localPlayer.closeContainer()
        }
    }

    private const val AIM_THRESHOLD = 2f
    private const val USER_INTERACTION_GRACE_TICKS = 2
    private const val PAYMENT_START = 0
    private const val PAYMENT_END = 2
    private const val RESULT_SLOT = 2
    private const val PLAYER_INVENTORY_START = 3
    private const val PLAYER_INVENTORY_END = 39
}
