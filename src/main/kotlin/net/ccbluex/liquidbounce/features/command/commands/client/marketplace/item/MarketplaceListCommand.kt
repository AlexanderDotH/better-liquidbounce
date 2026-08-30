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
package net.ccbluex.liquidbounce.features.command.commands.client.marketplace.item

import net.ccbluex.liquidbounce.api.models.marketplace.MarketplaceItemType
import net.ccbluex.liquidbounce.api.services.marketplace.MarketplaceApi
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.builder.enumChoice
import net.ccbluex.liquidbounce.features.command.dsl.addParam
import net.ccbluex.liquidbounce.features.command.dsl.buildCommand
import net.ccbluex.liquidbounce.features.command.dsl.cast
import net.ccbluex.liquidbounce.features.command.commands.client.marketplace.presentation.renderMarketplaceItemPage

/**
 * List marketplace items
 */
fun marketplaceListCommand() = buildCommand("list") {

    val type = addParam {
        enumChoice<MarketplaceItemType>("type") { it.isListable }
            .required()
    }

    val page = addParam("page") {
        verifiedBy(ParameterBuilder.INTEGER_VALIDATOR)
            .optional(1)
    }

    val featured = addParam("featured") {
        verifiedBy(ParameterBuilder.BOOLEAN_VALIDATOR)
            .optional(false)
    }

    suspendHandler {
        val type = type.cast()
        val page = page.cast()
        val featured = featured.cast()

        val response = MarketplaceApi.getMarketplaceItems(page, 10, type = type, featured = featured)

        command.renderMarketplaceItemPage("noItems", page, response.pagination.pages, response.items)
    }

}
