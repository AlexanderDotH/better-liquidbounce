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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantOfferMatcher
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantPlanningStep
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantRoundRobinPass
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantRoundRobinPlanner
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantTradeRule
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.item.trading.MerchantOffer

internal fun AutoShopVanillaMode.awaitOffers(state: MerchantSessionState.AwaitingOffers, tick: Int) {
    val menu = currentOwnedMenu(state.containerId)
        ?: return finishSession(MerchantSessionEndCause.TARGET_LOST, tick)
    if (menu.offers.isEmpty()) return

    if (session.markOffersReady(menu.containerId, tick)) {
        roundRobinPass = MerchantRoundRobinPass.start(tradeFilters.get().size)
        cpsGate.reset()
        planningStepCache.invalidate()
    }
}

internal fun AutoShopVanillaMode.trade(state: MerchantSessionState.Trading, tick: Int) {
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
    if (MerchantTradeCadencePolicy.shouldWaitForCps(step, cpsGate.canAttempt(tick))) return
    planningStepCache.invalidate()
    when (step) {
        is MerchantPlanningStep.Attempt -> attemptTrade(menu, rules, step, tick)
        is MerchantPlanningStep.PassComplete -> finishPass(menu, rules, step, tick)
    }
}

private fun AutoShopVanillaMode.attemptTrade(
    menu: MerchantMenu,
    rules: List<MerchantTradeRule>,
    step: MerchantPlanningStep.Attempt,
    tick: Int,
) {
    if (!isCurrentAttemptExecutable(menu, rules, step)) return
    val output = menu.offers.getOrNull(step.trade.offerIndex)?.result?.copy()
    val successful = executeSingleTrade(menu, step.trade.offerIndex)
    roundRobinPass = step.recordOutcome(successful)
    cpsGate.recordAttempt(tick, cps)
    if (successful && output != null) notifyPurchase(output)
    if (!menu.carried.isEmpty) finishSession(MerchantSessionEndCause.TRADE_BLOCKED, tick)
}

private fun AutoShopVanillaMode.finishPass(
    menu: MerchantMenu,
    rules: List<MerchantTradeRule>,
    step: MerchantPlanningStep.PassComplete,
    tick: Int,
) {
    if (step.anySuccess) {
        roundRobinPass = MerchantRoundRobinPass.start(rules.size)
        return
    }
    notifyInsufficientResources(menu, rules)
    finishSession(MerchantSessionEndCause.TRADE_BLOCKED, tick)
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

internal fun canExecute(menu: MerchantMenu, offer: MerchantOffer): Boolean {
    if (!menu.carried.isEmpty) return false
    val inventory = menu.slots.subList(PLAYER_INVENTORY_START, PLAYER_INVENTORY_END).map { it.item }
    val payments = menu.slots.subList(PAYMENT_START, PAYMENT_END).map { it.item }
    return MerchantTradeFeasibility.canExecute(offer, inventory, payments)
}

private fun executeSingleTrade(menu: MerchantMenu, offerIndex: Int): Boolean {
    val offer = menu.offers.getOrNull(offerIndex) ?: return false
    val previousUses = offer.uses
    menu.setSelectionHint(offerIndex)
    menu.tryMoveItems(offerIndex)
    network.send(ServerboundSelectTradePacket(offerIndex))
    if (!menu.getSlot(RESULT_SLOT).hasItem() || !menu.carried.isEmpty) return false

    interaction.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.PICKUP, player)
    val traded = offer.uses > previousUses
    if (!depositCarriedOutput(menu)) return false
    return traded
}

private fun depositCarriedOutput(menu: MerchantMenu): Boolean {
    val inventory = menu.slots.subList(PLAYER_INVENTORY_START, PLAYER_INVENTORY_END).map { it.item }
    val plan = MerchantOutputDepositPlanner.plan(menu.carried, inventory, PLAYER_INVENTORY_START)
    if (!plan.complete) return false
    for (slot in plan.destinationSlots) {
        val previousCount = menu.carried.count
        interaction.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player)
        if (!menu.carried.isEmpty && menu.carried.count >= previousCount) return false
    }
    return menu.carried.isEmpty
}

private const val PAYMENT_START = 0
private const val PAYMENT_END = 2
private const val RESULT_SLOT = 2
private const val PLAYER_INVENTORY_START = 3
private const val PLAYER_INVENTORY_END = 39
