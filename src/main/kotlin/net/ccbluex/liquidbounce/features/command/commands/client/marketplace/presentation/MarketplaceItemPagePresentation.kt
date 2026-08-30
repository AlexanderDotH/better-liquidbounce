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
package net.ccbluex.liquidbounce.features.command.commands.client.marketplace.presentation

import net.ccbluex.liquidbounce.api.models.marketplace.MarketplaceItem
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceManager
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.onClick
import net.ccbluex.liquidbounce.utils.text.onHover
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent

internal fun Command.renderMarketplaceItemPage(
    emptyResultKey: String,
    page: Int,
    pages: Int,
    items: List<MarketplaceItem>,
) {
    if (items.isEmpty()) {
        chat(regular(result(emptyResultKey)))
        return
    }
    chat(regular(result("header", variable(page.toString()), variable(pages.toString()))))
    items.forEach { item -> chat(regular(renderMarketplaceItem(item))) }
}

private fun Command.renderMarketplaceItem(item: MarketplaceItem): net.minecraft.network.chat.MutableComponent {
    val isSubscribed = MarketplaceManager.isSubscribed(item.id)
    val action = if (isSubscribed) "unsubscribe" else "subscribe"
    return result(
        "item",
        variable(item.id.toString()),
        variable(item.name + if (isSubscribed) "*" else ""),
        variable(item.type.toString().lowercase()),
        variable(if (item.featured) "★" else ""),
    ).onClick(ClickEvent.SuggestCommand(".marketplace $action ${item.id}"))
        .onHover(HoverEvent.ShowText(variable(result("hover", variable(action), item.id))))
}
