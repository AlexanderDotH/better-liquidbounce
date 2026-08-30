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
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.builder.enumChoice
import net.ccbluex.liquidbounce.features.command.dsl.addParam
import net.ccbluex.liquidbounce.features.command.dsl.buildCommand
import net.ccbluex.liquidbounce.features.command.dsl.cast
import net.ccbluex.liquidbounce.features.command.dsl.castVararg
import net.ccbluex.liquidbounce.features.command.preset.accountOrException
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.cosmetic.ClientAccountManager
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable

/**
 * Edit marketplace item
 */
fun marketplaceEditItemCommand() = buildCommand("edit") {
    val parameters = marketplaceEditParameters()
    suspendHandler { updateMarketplaceItem(this, parameters) }
}

private data class MarketplaceEditParameters(
    val id: Parameter<Int>,
    val name: Parameter<String>,
    val type: Parameter<MarketplaceItemType>,
    val description: Parameter<String>,
) {
    fun read(context: Command.Handler.Context) = with(context) {
        MarketplaceItemUpdate(id.cast(), name.cast(), type.cast(), description.castVararg().joinToString(" "))
    }
}

private data class MarketplaceItemUpdate(
    val id: Int,
    val name: String,
    val type: MarketplaceItemType,
    val description: String,
)

private fun CommandBuilder.marketplaceEditParameters(): MarketplaceEditParameters {
    val id = addParam("id") {
        verifiedBy(ParameterBuilder.INTEGER_VALIDATOR)
            .required()
    }
    val name = addParam("name") {
        verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .required()
    }
    val type = addParam {
        enumChoice<MarketplaceItemType>("type") { it.isListable }
            .required()
    }
    val description = addParam("description") {
        verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .required()
            .vararg()
    }
    return MarketplaceEditParameters(id, name, type, description)
}

private suspend fun updateMarketplaceItem(
    context: Command.Handler.Context,
    parameters: MarketplaceEditParameters,
) {
    val clientAccount = ClientAccountManager.accountOrException()
    val update = parameters.read(context)
    val response = MarketplaceApi.updateMarketplaceItem(
        clientAccount.takeSession(),
        update.id,
        update.name,
        update.type,
        update.description,
    )
    chat(regular(context.command.result("success", variable(response.id.toString()), variable(response.name))))
}
