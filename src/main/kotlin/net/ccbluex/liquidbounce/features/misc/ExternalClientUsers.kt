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

import net.ccbluex.liquidbounce.config.types.list.Tagged
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

enum class ExternalClient(override val tag: String) : Tagged {
    LIQUIDBOUNCE("LiquidBounce"),
    LIQUIDBOUNCE_FDP("LiquidBounceFDP"),
    FEATHER("Feather"),
    METEOR("Meteor"),
    WURST("Wurst"),
    LABYMOD("LabyMod"),
    OPTIFINE("OptiFine"),
    ESSENTIAL("Essential"),
}

enum class ExternalClientEvidence(val strength: Int, val uncertain: Boolean) {
    CAPE(0, true),
    ACCOUNT(0, true),
    RECENT_CHAT(1, false),
    NETWORK_ONLINE(2, false),
}

data class ExternalClientUser(
    val uuid: UUID,
    val client: ExternalClient,
    val evidence: ExternalClientEvidence,
    val observedAt: Instant,
    val expiresAt: Instant,
)

internal class ExternalClientUserRegistry {

    private val observations = AtomicReference<List<ExternalClientUser>>(emptyList())

    fun observe(user: ExternalClientUser) {
        observations.updateAndGet { current ->
            val previous = current.firstOrNull { it.sameSourceAs(user) }
            if (previous != null && OBSERVATION_ORDER.compare(previous, user) >= 0) {
                return@updateAndGet current
            }

            immutable(current.filterNot { it.sameSourceAs(user) } + user)
        }
    }

    fun users(uuid: UUID, now: Instant = Instant.now()): List<ExternalClientUser> =
        strongest(now) { it.uuid == uuid }

    fun all(now: Instant = Instant.now()): List<ExternalClientUser> = strongest(now) { true }

    fun clear(client: ExternalClient? = null, evidence: ExternalClientEvidence? = null) {
        observations.updateAndGet { current ->
            immutable(current.filterNot { user ->
                (client == null || user.client == client) &&
                    (evidence == null || user.evidence == evidence)
            })
        }
    }

    private fun strongest(
        now: Instant,
        include: (ExternalClientUser) -> Boolean,
    ): List<ExternalClientUser> {
        val strongest = linkedMapOf<Pair<UUID, ExternalClient>, ExternalClientUser>()
        observations.get().forEach { user ->
            if (user.observedAt.isAfter(now) || !user.expiresAt.isAfter(now) || !include(user)) {
                return@forEach
            }

            val key = user.uuid to user.client
            val current = strongest[key]
            if (current == null || OBSERVATION_ORDER.compare(user, current) > 0) {
                strongest[key] = user
            }
        }
        return immutable(strongest.values)
    }

    private fun ExternalClientUser.sameSourceAs(other: ExternalClientUser) =
        uuid == other.uuid && client == other.client && evidence == other.evidence

    private fun <T> immutable(values: Collection<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))

    private companion object {
        val OBSERVATION_ORDER = compareBy<ExternalClientUser> { it.evidence.strength }
            .thenBy { it.observedAt }
            .thenBy { it.expiresAt }
    }
}

object ExternalClientUsers {

    private val registry = ExternalClientUserRegistry()

    fun observe(user: ExternalClientUser) = registry.observe(user)

    fun users(uuid: UUID, now: Instant = Instant.now()) = registry.users(uuid, now)

    fun all(now: Instant = Instant.now()) = registry.all(now)

    fun clear(client: ExternalClient? = null, evidence: ExternalClientEvidence? = null) =
        registry.clear(client, evidence)
}
