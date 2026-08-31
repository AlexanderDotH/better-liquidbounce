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
package net.ccbluex.liquidbounce.features.misc

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExternalClientUserRegistryTest {

    private val uuid = UUID.fromString("30a9237d-2a07-42f5-82af-2039f6653f7b")

    @Test
    fun `an observation expires at its expiry instant`() {
        val registry = ExternalClientUserRegistry()
        val observation = user(
            client = ExternalClient.METEOR,
            evidence = ExternalClientEvidence.CAPE,
            observedAt = instant(10),
            expiresAt = instant(20),
        )

        registry.observe(observation)

        assertEquals(listOf(observation), registry.users(uuid, instant(19)))
        assertTrue(registry.users(uuid, instant(20)).isEmpty())
        assertTrue(registry.all(instant(20)).isEmpty())
    }

    @Test
    fun `current network evidence wins over recent chat and ownership evidence`() {
        val registry = ExternalClientUserRegistry()
        val cape = user(ExternalClient.FEATHER, ExternalClientEvidence.CAPE, instant(10), instant(100))
        val account = user(ExternalClient.FEATHER, ExternalClientEvidence.ACCOUNT, instant(20), instant(100))
        val chat = user(ExternalClient.FEATHER, ExternalClientEvidence.RECENT_CHAT, instant(30), instant(100))
        val network = user(ExternalClient.FEATHER, ExternalClientEvidence.NETWORK_ONLINE, instant(40), instant(100))

        listOf(network, chat, account, cape).forEach(registry::observe)

        assertEquals(listOf(network), registry.users(uuid, instant(50)))
    }

    @Test
    fun `weaker evidence survives after stronger evidence expires`() {
        val registry = ExternalClientUserRegistry()
        val cape = user(ExternalClient.LIQUIDBOUNCE, ExternalClientEvidence.CAPE, instant(10), instant(100))
        val network = user(
            ExternalClient.LIQUIDBOUNCE,
            ExternalClientEvidence.NETWORK_ONLINE,
            instant(20),
            instant(30),
        )

        registry.observe(cape)
        registry.observe(network)

        assertEquals(listOf(network), registry.users(uuid, instant(25)))
        assertEquals(listOf(cape), registry.users(uuid, instant(31)))
    }

    @Test
    fun `one UUID can retain observations for multiple clients`() {
        val registry = ExternalClientUserRegistry()
        val meteor = user(ExternalClient.METEOR, ExternalClientEvidence.CAPE, instant(10), instant(100))
        val wurst = user(ExternalClient.WURST, ExternalClientEvidence.ACCOUNT, instant(10), instant(100))

        registry.observe(meteor)
        registry.observe(wurst)

        assertEquals(setOf(meteor, wurst), registry.users(uuid, instant(20)).toSet())
    }

    @Test
    fun `published snapshots cannot be mutated and do not change after later observations`() {
        val registry = ExternalClientUserRegistry()
        val meteor = user(ExternalClient.METEOR, ExternalClientEvidence.CAPE, instant(10), instant(100))
        val wurst = user(ExternalClient.WURST, ExternalClientEvidence.ACCOUNT, instant(20), instant(100))
        registry.observe(meteor)
        val snapshot = registry.all(instant(30))

        registry.observe(wurst)

        assertEquals(listOf(meteor), snapshot)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (snapshot as MutableList<ExternalClientUser>).clear()
        }
    }

    @Test
    fun `scoped clearing removes only matching evidence`() {
        val registry = ExternalClientUserRegistry()
        val chat = user(
            ExternalClient.LIQUIDBOUNCE_FDP,
            ExternalClientEvidence.RECENT_CHAT,
            instant(10),
            instant(100),
        )
        val cape = user(ExternalClient.LIQUIDBOUNCE, ExternalClientEvidence.CAPE, instant(10), instant(100))
        val network = user(
            ExternalClient.FEATHER,
            ExternalClientEvidence.NETWORK_ONLINE,
            instant(10),
            instant(100),
        )
        val otherChat = user(
            ExternalClient.METEOR,
            ExternalClientEvidence.RECENT_CHAT,
            instant(10),
            instant(100),
        )
        listOf(chat, cape, network, otherChat).forEach(registry::observe)

        registry.clear(
            client = ExternalClient.LIQUIDBOUNCE_FDP,
            evidence = ExternalClientEvidence.RECENT_CHAT,
        )

        assertEquals(setOf(cape, network, otherChat), registry.all(instant(20)).toSet())
    }

    private fun user(
        client: ExternalClient,
        evidence: ExternalClientEvidence,
        observedAt: Instant,
        expiresAt: Instant,
    ) = ExternalClientUser(uuid, client, evidence, observedAt, expiresAt)

    private fun instant(epochSecond: Long): Instant = Instant.ofEpochSecond(epochSecond)
}
