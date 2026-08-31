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

import net.ccbluex.liquidbounce.features.misc.ExternalClient
import net.ccbluex.liquidbounce.features.misc.ExternalClientEvidence
import net.ccbluex.liquidbounce.features.misc.ExternalClientUser
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.minecraft.network.chat.Component
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BetterTabClientIndicatorsTest {

    @Test
    fun `full and short labels cover every supported client`() {
        val fullLabels = mapOf(
            ExternalClient.LIQUIDBOUNCE to "LiquidBounce",
            ExternalClient.LIQUIDBOUNCE_FDP to "LiquidBounce/FDP",
            ExternalClient.METEOR to "Meteor",
            ExternalClient.WURST to "Wurst",
            ExternalClient.FEATHER to "Feather",
            ExternalClient.LABYMOD to "LabyMod",
        )
        val shortLabels = mapOf(
            ExternalClient.LIQUIDBOUNCE to "LB",
            ExternalClient.LIQUIDBOUNCE_FDP to "LB/FDP",
            ExternalClient.METEOR to "MET",
            ExternalClient.WURST to "WUR",
            ExternalClient.FEATHER to "FTH",
            ExternalClient.LABYMOD to "LABY",
        )

        fullLabels.forEach { (client, label) ->
            assertEquals(" [$label]", badges(user(client), labelStyle = ClientLabelStyle.FULL).string)
        }
        shortLabels.forEach { (client, label) ->
            assertEquals(" [$label]", badges(user(client), labelStyle = ClientLabelStyle.SHORT).string)
        }
    }

    @Test
    fun `ownership evidence carries a question mark and can be hidden`() {
        val users = listOf(
            user(ExternalClient.LIQUIDBOUNCE, ExternalClientEvidence.CAPE),
            user(ExternalClient.METEOR, ExternalClientEvidence.ACCOUNT),
        )

        assertEquals(" [LiquidBounce?] [Meteor?]", badges(*users.toTypedArray()).string)
        assertNull(
            BetterTabClientIndicators.playerBadges(
                users = users,
                labelStyle = ClientLabelStyle.FULL,
                ownershipSignals = false,
                enabledClients = ExternalClient.entries.toSet(),
                liquidBounceColor = LB_COLOR,
            ),
        )
    }

    @Test
    fun `current network and recent chat evidence are certain`() {
        val users = listOf(
            user(ExternalClient.FEATHER, ExternalClientEvidence.NETWORK_ONLINE),
            user(ExternalClient.LIQUIDBOUNCE_FDP, ExternalClientEvidence.RECENT_CHAT),
        )

        assertEquals(" [LiquidBounce/FDP] [Feather]", badges(*users.toTypedArray()).string)
    }

    @Test
    fun `LiquidBounce cape and recent AxoChat coalesce to one honest marker`() {
        val uuid = UUID.randomUUID()
        val users = listOf(
            user(ExternalClient.LIQUIDBOUNCE, ExternalClientEvidence.CAPE, uuid),
            user(ExternalClient.LIQUIDBOUNCE_FDP, ExternalClientEvidence.RECENT_CHAT, uuid),
        )

        assertEquals(" [LiquidBounce/FDP]", badges(*users.toTypedArray()).string)
    }

    @Test
    fun `one player can carry multiple different client badges`() {
        val uuid = UUID.randomUUID()
        val users = listOf(
            user(ExternalClient.METEOR, ExternalClientEvidence.NETWORK_ONLINE, uuid),
            user(ExternalClient.WURST, ExternalClientEvidence.ACCOUNT, uuid),
        )

        assertEquals(" [Meteor] [Wurst?]", badges(*users.toTypedArray()).string)
    }

    @Test
    fun `legend counts unique players by rendered marker`() {
        val users = listOf(
            user(ExternalClient.FEATHER, ExternalClientEvidence.NETWORK_ONLINE),
            user(ExternalClient.METEOR, ExternalClientEvidence.ACCOUNT),
            user(ExternalClient.METEOR, ExternalClientEvidence.ACCOUNT),
        )

        val legend = BetterTabClientIndicators.legend(
            users = users,
            labelStyle = ClientLabelStyle.FULL,
            ownershipSignals = true,
            enabledClients = ExternalClient.entries.toSet(),
            liquidBounceColor = LB_COLOR,
        )

        assertEquals("Clients: [Feather 1] [Meteor? 2]", legend!!.string)
    }

    @Test
    fun `badges and legend use the same client color resolver`() {
        val meteor = user(ExternalClient.METEOR)
        val badge = badges(meteor)
        val legend = BetterTabClientIndicators.legend(
            users = listOf(meteor),
            labelStyle = ClientLabelStyle.FULL,
            ownershipSignals = true,
            enabledClients = ExternalClient.entries.toSet(),
            liquidBounceColor = LB_COLOR,
        )!!
        val expected = BetterTabClientIndicators.color(ExternalClient.METEOR, LB_COLOR).toTextColor()

        assertEquals(expected, coloredSegment(badge).style.color)
        assertEquals(expected, coloredSegment(legend).style.color)
        assertEquals(Color4b.fromHex("#0080FF"), BetterTabClientIndicators.color(ExternalClient.LIQUIDBOUNCE, LB_COLOR))
        assertEquals(
            Color4b.fromHex("#0080FF"),
            BetterTabClientIndicators.color(ExternalClient.LIQUIDBOUNCE_FDP, LB_COLOR),
        )
        assertEquals(Color4b.fromHex("#913DE2"), BetterTabClientIndicators.color(ExternalClient.METEOR, LB_COLOR))
        assertEquals(Color4b.fromHex("#BF5E01"), BetterTabClientIndicators.color(ExternalClient.WURST, LB_COLOR))
        assertEquals(Color4b.fromHex("#D73232"), BetterTabClientIndicators.color(ExternalClient.FEATHER, LB_COLOR))
        assertEquals(Color4b.fromHex("#2563EB"), BetterTabClientIndicators.color(ExternalClient.LABYMOD, LB_COLOR))
    }

    @Test
    fun `legend appends without changing the existing server header`() {
        val serverHeader = PlainText.of("Existing server header", Color4b.RED.toTextColor())
        val legend = PlainText.of("Clients: [Meteor 1]")

        val combined = BetterTabClientIndicators.appendLegend(serverHeader, legend)!!

        assertEquals("Existing server header\nClients: [Meteor 1]", combined.string)
        assertSame(serverHeader, combined.siblings.first())
    }

    private fun badges(
        vararg users: ExternalClientUser,
        labelStyle: ClientLabelStyle = ClientLabelStyle.FULL,
    ): Component = checkNotNull(
        BetterTabClientIndicators.playerBadges(
            users = users.asList(),
            labelStyle = labelStyle,
            ownershipSignals = true,
            enabledClients = ExternalClient.entries.toSet(),
            liquidBounceColor = LB_COLOR,
        ),
    )

    private fun user(
        client: ExternalClient,
        evidence: ExternalClientEvidence = ExternalClientEvidence.NETWORK_ONLINE,
        uuid: UUID = UUID.randomUUID(),
    ) = ExternalClientUser(
        uuid = uuid,
        client = client,
        evidence = evidence,
        observedAt = NOW,
        expiresAt = NOW.plusSeconds(60),
    )

    private fun coloredSegment(component: Component) =
        (listOf(component) + component.siblings).single { it.style.color != null }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-31T12:00:00Z")
        val LB_COLOR = Color4b.LIQUID_BOUNCE
    }
}
