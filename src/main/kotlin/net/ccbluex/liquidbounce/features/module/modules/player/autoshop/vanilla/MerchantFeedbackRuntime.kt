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

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantOfferMatcher
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantTradeRule
import net.ccbluex.liquidbounce.features.chat.notification
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.item.ItemStack

internal fun AutoShopVanillaMode.notifyPurchase(result: ItemStack) {
    if (!feedbackGate.shouldNotifyPurchase()) return
    notification(
        title = "AutoShop",
        message = "Bought ${result.count} × ${result.hoverName.string}",
        severity = NotificationEvent.Severity.SUCCESS,
    )
}

internal fun AutoShopVanillaMode.notifyInsufficientResources(
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
    if (!cannotPay) return
    notification(
        title = "AutoShop",
        message = "Not enough resources to pay for the configured trade",
        severity = NotificationEvent.Severity.ERROR,
    )
}

private const val PAYMENT_START = 0
private const val PAYMENT_END = 2
private const val PLAYER_INVENTORY_START = 3
private const val PLAYER_INVENTORY_END = 39
