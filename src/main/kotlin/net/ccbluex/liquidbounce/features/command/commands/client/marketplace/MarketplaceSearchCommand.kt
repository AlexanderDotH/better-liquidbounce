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
package net.ccbluex.liquidbounce.features.command.commands.client.marketplace

import net.ccbluex.liquidbounce.api.services.marketplace.MarketplaceApi
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.command.commands.client.marketplace.presentation.renderMarketplaceItemPage
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.regular

/**
 * Search marketplace items
 */
object MarketplaceSearchCommand : Command.Factory {

    override fun createCommand() = CommandBuilder.begin("search")
        .parameter(
            ParameterBuilder
                .begin<String>("query")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .vararg()
                .required()
                .build()
        )
        .parameter(
            ParameterBuilder
                .begin<Int>("page")
                .verifiedBy(ParameterBuilder.INTEGER_VALIDATOR)
                .optional()
                .build()
        )
        .suspendHandler {
            val query = (args[0] as Array<*>).joinToString(" ")
            val page = args.getOrNull(1) as? Int ?: 1

            chat(regular(command.result("searching")))

            val response = MarketplaceApi.getMarketplaceItems(
                page = page,
                limit = 10,
                query = query
            )

            command.renderMarketplaceItemPage("noResults", page, response.pagination.pages, response.items)
        }
        .build()
}
