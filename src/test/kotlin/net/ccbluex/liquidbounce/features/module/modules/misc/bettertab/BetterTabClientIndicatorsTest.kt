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
import net.minecraft.network.chat.contents.ObjectContents
import net.minecraft.network.chat.contents.objects.AtlasSprite
import net.minecraft.resources.Identifier
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BetterTabClientIndicatorsTest {

    @Test
    fun `icons retain full and short accessible labels for every supported client`() {
        val fullLabels = mapOf(
            ExternalClient.LIQUIDBOUNCE to "LiquidBounce",
            ExternalClient.LIQUIDBOUNCE_FDP to "LiquidBounce/FDP",
            ExternalClient.METEOR to "Meteor",
            ExternalClient.WURST to "Wurst",
            ExternalClient.FEATHER to "Feather",
            ExternalClient.LABYMOD to "LabyMod",
            ExternalClient.OPTIFINE to "OptiFine",
            ExternalClient.ESSENTIAL to "Essential",
        )
        val shortLabels = mapOf(
            ExternalClient.LIQUIDBOUNCE to "LB",
            ExternalClient.LIQUIDBOUNCE_FDP to "LB/FDP",
            ExternalClient.METEOR to "MET",
            ExternalClient.WURST to "WUR",
            ExternalClient.FEATHER to "FTH",
            ExternalClient.LABYMOD to "LABY",
            ExternalClient.OPTIFINE to "OF",
            ExternalClient.ESSENTIAL to "ESS",
        )

        fullLabels.forEach { (client, label) ->
            val badge = badges(user(client), labelStyle = ClientLabelStyle.FULL)

            assertEquals(" $label", badge.string)
            assertTrue(badge.siblings.any { it.string == label && it.contents !is ObjectContents })
            assertEquals(listOf(clientIconId(client)), iconSprites(badge))
        }
        shortLabels.forEach { (client, label) ->
            val badge = badges(user(client), labelStyle = ClientLabelStyle.SHORT)

            assertEquals(" $label", badge.string)
            assertTrue(badge.siblings.any { it.string == label && it.contents !is ObjectContents })
            assertEquals(listOf(clientIconId(client)), iconSprites(badge))
        }
    }

    @Test
    fun `icons can be hidden while the selected label remains visible`() {
        val full = badges(user(ExternalClient.METEOR), showIcons = false)
        val short = badges(
            user(ExternalClient.METEOR),
            labelStyle = ClientLabelStyle.SHORT,
            showIcons = false,
        )

        val legend = BetterTabClientIndicators.legend(
            users = listOf(user(ExternalClient.METEOR)),
            labelStyle = ClientLabelStyle.FULL,
            ownershipSignals = true,
            enabledClients = ExternalClient.entries.toSet(),
            liquidBounceColor = LB_COLOR,
            showIcons = false,
        )!!

        assertEquals(" Meteor", full.string)
        assertEquals(" MET", short.string)
        assertEquals("Clients: Meteor 1", legend.string)
        assertEquals(emptyList(), iconSprites(full))
        assertEquals(emptyList(), iconSprites(short))
        assertEquals(emptyList(), iconSprites(legend))
    }

    @Test
    fun `every client icon is bundled in the GUI sprite atlas path`() {
        ExternalClient.entries.map(::clientIconId).distinct().forEach { id ->
            assertNotNull(
                javaClass.getResource("/assets/${id.namespace}/textures/gui/sprites/${id.path}.png"),
                id.toString(),
            )
        }
    }

    @Test
    fun `ownership evidence renders without a question mark and can be hidden`() {
        val users = listOf(
            user(ExternalClient.LIQUIDBOUNCE, ExternalClientEvidence.CAPE),
            user(ExternalClient.METEOR, ExternalClientEvidence.ACCOUNT),
            user(ExternalClient.OPTIFINE, ExternalClientEvidence.CAPE),
        )

        val badges = badges(*users.toTypedArray())

        assertEquals(" LiquidBounce Meteor OptiFine", badges.string)
        assertEquals(
            listOf(
                clientIconId(ExternalClient.LIQUIDBOUNCE),
                clientIconId(ExternalClient.METEOR),
                clientIconId(ExternalClient.OPTIFINE),
            ),
            iconSprites(badges),
        )
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
            user(ExternalClient.ESSENTIAL, ExternalClientEvidence.NETWORK_ONLINE),
        )

        assertEquals(" LiquidBounce/FDP Feather Essential", badges(*users.toTypedArray()).string)
    }

    @Test
    fun `LiquidBounce cape and recent AxoChat coalesce to one honest marker`() {
        val uuid = UUID.randomUUID()
        val users = listOf(
            user(ExternalClient.LIQUIDBOUNCE, ExternalClientEvidence.CAPE, uuid),
            user(ExternalClient.LIQUIDBOUNCE_FDP, ExternalClientEvidence.RECENT_CHAT, uuid),
        )

        assertEquals(" LiquidBounce/FDP", badges(*users.toTypedArray()).string)
    }

    @Test
    fun `one player can carry multiple different client badges`() {
        val uuid = UUID.randomUUID()
        val users = listOf(
            user(ExternalClient.METEOR, ExternalClientEvidence.NETWORK_ONLINE, uuid),
            user(ExternalClient.WURST, ExternalClientEvidence.ACCOUNT, uuid),
        )

        assertEquals(" Meteor Wurst", badges(*users.toTypedArray()).string)
    }

    @Test
    fun `legend counts unique players by rendered marker`() {
        val users = listOf(
            user(ExternalClient.FEATHER, ExternalClientEvidence.NETWORK_ONLINE),
            user(ExternalClient.METEOR, ExternalClientEvidence.ACCOUNT),
            user(ExternalClient.METEOR, ExternalClientEvidence.NETWORK_ONLINE),
        )

        val legend = BetterTabClientIndicators.legend(
            users = users,
            labelStyle = ClientLabelStyle.FULL,
            ownershipSignals = true,
            enabledClients = ExternalClient.entries.toSet(),
            liquidBounceColor = LB_COLOR,
        )

        assertEquals("Clients: Feather 1 Meteor 2", legend!!.string)
        assertEquals(
            listOf(clientIconId(ExternalClient.FEATHER), clientIconId(ExternalClient.METEOR)),
            iconSprites(legend),
        )
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

        assertEquals(expected, coloredIcon(badge).style.color)
        assertEquals(expected, coloredIcon(legend).style.color)
        assertEquals(Color4b.fromHex("#0080FF"), BetterTabClientIndicators.color(ExternalClient.LIQUIDBOUNCE, LB_COLOR))
        assertEquals(
            Color4b.fromHex("#0080FF"),
            BetterTabClientIndicators.color(ExternalClient.LIQUIDBOUNCE_FDP, LB_COLOR),
        )
        assertEquals(Color4b.fromHex("#913DE2"), BetterTabClientIndicators.color(ExternalClient.METEOR, LB_COLOR))
        assertEquals(Color4b.fromHex("#BF5E01"), BetterTabClientIndicators.color(ExternalClient.WURST, LB_COLOR))
        assertEquals(Color4b.fromHex("#D73232"), BetterTabClientIndicators.color(ExternalClient.FEATHER, LB_COLOR))
        assertEquals(Color4b.fromHex("#2563EB"), BetterTabClientIndicators.color(ExternalClient.LABYMOD, LB_COLOR))
        assertEquals(Color4b.fromHex("#5168CF"), BetterTabClientIndicators.color(ExternalClient.OPTIFINE, LB_COLOR))
        assertEquals(Color4b.fromHex("#2997FF"), BetterTabClientIndicators.color(ExternalClient.ESSENTIAL, LB_COLOR))
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
        showIcons: Boolean = true,
    ): Component = checkNotNull(
        BetterTabClientIndicators.playerBadges(
            users = users.asList(),
            labelStyle = labelStyle,
            showIcons = showIcons,
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

    private fun iconSprites(component: Component): List<Identifier> =
        (listOf(component) + component.siblings).mapNotNull { child ->
            val objectContents = child.contents as? ObjectContents ?: return@mapNotNull null
            (objectContents.contents as? AtlasSprite)?.sprite
        }

    private fun coloredIcon(component: Component) =
        (listOf(component) + component.siblings).single { it.contents is ObjectContents }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-31T12:00:00Z")
        val LB_COLOR = Color4b.LIQUID_BOUNCE
    }
}
