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
package net.ccbluex.liquidbounce.features.module.modules.misc.bettertab

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.misc.ExternalClient
import net.ccbluex.liquidbounce.features.misc.ExternalClientEvidence
import net.ccbluex.liquidbounce.features.misc.ExternalClientUser
import net.ccbluex.liquidbounce.features.misc.ClientBrand
import net.ccbluex.liquidbounce.features.misc.ClientBrandColors
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.ccbluex.liquidbounce.utils.text.TextBuilder
import net.minecraft.network.chat.Component

enum class ClientLabelStyle(override val tag: String) : Tagged {
    FULL("Full"),
    SHORT("Short"),
}

internal object BetterTabClientIndicators {

    fun playerBadges(
        users: Iterable<ExternalClientUser>,
        labelStyle: ClientLabelStyle,
        ownershipSignals: Boolean,
        enabledClients: Set<ExternalClient>,
        liquidBounceColor: Color4b,
    ): Component? {
        val markers = markers(users, ownershipSignals, enabledClients)
        if (markers.isEmpty()) {
            return null
        }

        return TextBuilder().apply {
            markers.forEach { marker ->
                add(
                    PlainText.of(
                        " [${marker.label(labelStyle)}]",
                        color(marker.client, liquidBounceColor).toTextColor(),
                    ),
                )
            }
        }.build()
    }

    fun legend(
        users: Iterable<ExternalClientUser>,
        labelStyle: ClientLabelStyle,
        ownershipSignals: Boolean,
        enabledClients: Set<ExternalClient>,
        liquidBounceColor: Color4b,
    ): Component? {
        val counts = users.groupBy(ExternalClientUser::uuid)
            .values
            .flatMap { markers(it, ownershipSignals, enabledClients) }
            .groupingBy { it }
            .eachCount()
        if (counts.isEmpty()) {
            return null
        }

        return TextBuilder(PlainText.of("Clients:")).apply {
            counts.entries
                .sortedWith(compareBy({ it.key.client.ordinal }, { it.key.uncertain }))
                .forEach { (marker, count) ->
                    add(
                        PlainText.of(
                            " [${marker.label(labelStyle)} $count]",
                            color(marker.client, liquidBounceColor).toTextColor(),
                        ),
                    )
                }
        }.build()
    }

    fun appendLegend(serverHeader: Component?, legend: Component?): Component? = when {
        legend == null -> serverHeader
        serverHeader == null -> legend
        else -> TextBuilder(serverHeader).add(PlainText.NEW_LINE).add(legend).build()
    }

    fun color(client: ExternalClient, liquidBounceColor: Color4b): Color4b =
        ClientBrandColors.color(client.brand, liquidBounceColor)

    private fun markers(
        users: Iterable<ExternalClientUser>,
        ownershipSignals: Boolean,
        enabledClients: Set<ExternalClient>,
    ): List<ClientMarker> {
        val strongest = users
            .filter { it.client in enabledClients && (ownershipSignals || !it.evidence.uncertain) }
            .groupBy(ExternalClientUser::client)
            .mapValues { (_, evidence) -> evidence.maxWith(USER_ORDER) }
            .toMutableMap()

        val liquidBounce = strongest[ExternalClient.LIQUIDBOUNCE]
        val liquidBounceFdp = strongest[ExternalClient.LIQUIDBOUNCE_FDP]
        if (liquidBounce != null && liquidBounceFdp != null) {
            val weaker = if (USER_ORDER.compare(liquidBounce, liquidBounceFdp) > 0) {
                ExternalClient.LIQUIDBOUNCE_FDP
            } else {
                ExternalClient.LIQUIDBOUNCE
            }
            strongest.remove(weaker)
        }

        return strongest.values
            .sortedBy { it.client.ordinal }
            .map { ClientMarker(it.client, it.evidence.uncertain) }
    }

    private fun ClientMarker.label(style: ClientLabelStyle): String {
        val label = when (style) {
            ClientLabelStyle.FULL -> client.fullLabel()
            ClientLabelStyle.SHORT -> client.shortLabel()
        }
        return label + if (uncertain) "?" else ""
    }

    private fun ExternalClient.fullLabel() = when (this) {
        ExternalClient.LIQUIDBOUNCE -> "LiquidBounce"
        ExternalClient.LIQUIDBOUNCE_FDP -> "LiquidBounce/FDP"
        ExternalClient.METEOR -> "Meteor"
        ExternalClient.WURST -> "Wurst"
        ExternalClient.FEATHER -> "Feather"
        ExternalClient.LABYMOD -> "LabyMod"
        ExternalClient.OPTIFINE -> "OptiFine"
        ExternalClient.ESSENTIAL -> "Essential"
    }

    private fun ExternalClient.shortLabel() = when (this) {
        ExternalClient.LIQUIDBOUNCE -> "LB"
        ExternalClient.LIQUIDBOUNCE_FDP -> "LB/FDP"
        ExternalClient.METEOR -> "MET"
        ExternalClient.WURST -> "WUR"
        ExternalClient.FEATHER -> "FTH"
        ExternalClient.LABYMOD -> "LABY"
        ExternalClient.OPTIFINE -> "OF"
        ExternalClient.ESSENTIAL -> "ESS"
    }

    private val ExternalClient.brand
        get() = when (this) {
            ExternalClient.LIQUIDBOUNCE,
            ExternalClient.LIQUIDBOUNCE_FDP,
            -> ClientBrand.LIQUIDBOUNCE
            ExternalClient.METEOR -> ClientBrand.METEOR
            ExternalClient.WURST -> ClientBrand.WURST
            ExternalClient.FEATHER -> ClientBrand.FEATHER
            ExternalClient.LABYMOD -> ClientBrand.LABYMOD
            ExternalClient.OPTIFINE -> ClientBrand.OPTIFINE
            ExternalClient.ESSENTIAL -> ClientBrand.ESSENTIAL
        }

    private data class ClientMarker(val client: ExternalClient, val uncertain: Boolean)

    private val USER_ORDER = compareBy<ExternalClientUser> { it.evidence.strength }
        .thenBy { it.observedAt }
        .thenBy { it.expiresAt }

}
