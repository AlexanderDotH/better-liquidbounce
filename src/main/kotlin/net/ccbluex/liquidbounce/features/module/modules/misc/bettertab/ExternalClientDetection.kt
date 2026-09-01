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
package net.ccbluex.liquidbounce.features.module.modules.misc.bettertab

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.util.UndashedUuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.core.HttpException
import net.ccbluex.liquidbounce.api.core.HttpMethod
import net.ccbluex.liquidbounce.api.core.HttpClient.mojangApiClient
import net.ccbluex.liquidbounce.api.models.cosmetics.CosmeticCategory
import net.ccbluex.liquidbounce.features.cosmetic.CosmeticService
import net.ccbluex.liquidbounce.features.misc.ExternalClient
import net.ccbluex.liquidbounce.features.misc.ExternalClientEvidence
import net.ccbluex.liquidbounce.features.misc.ExternalClientUser
import net.ccbluex.liquidbounce.features.misc.ExternalClientUsers
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

private const val OPTIFINE_CAPE_PREFIX = "https://optifine.net/capes/"
private val MINECRAFT_NAME = Regex("[A-Za-z0-9_]{1,16}")

@Suppress("TooManyFunctions")
object ExternalClientDetection {

    private const val METEOR_CAPE_OWNERS = "https://meteorclient.com/api/capeowners"
    private const val WURST_CAPES = "https://www.wurstclient.net/api/v1/capes.json"
    private const val FEATHER_ACCOUNT_SEARCH = "https://api.feathermc.com/v1/minecraft/account-search"
    private const val LABY_CAPE_PREFIX = "https://dl.labymod.net/capes/"

    private val listCacheLifetime = Duration.ofMinutes(10)
    private val networkRefreshInterval = Duration.ofSeconds(30)
    private val featherAccountLifetime = Duration.ofMinutes(30)
    private val networkOnlineLifetime = Duration.ofSeconds(90)
    private val labyCacheLifetime = Duration.ofMinutes(30)
    private val optifineCacheLifetime = Duration.ofMinutes(30)
    private val profileCacheLifetime = Duration.ofMinutes(30)
    private val liquidBounceCacheLifetime = Duration.ofSeconds(60)

    private val lock = Any()
    private var nextMeteorRefresh = Instant.EPOCH
    private var nextWurstRefresh = Instant.EPOCH
    private var nextFeatherRefresh = Instant.EPOCH
    private var nextLabyRefresh = Instant.EPOCH
    private var nextOptifineRefresh = Instant.EPOCH
    private var nextEssentialRefresh = Instant.EPOCH
    private var nextProfileRefresh = Instant.EPOCH
    private var meteorOwners = emptySet<UUID>()
    private var meteorOwnersExpireAt = Instant.EPOCH
    private var wurstOwners = emptySet<UUID>()
    private var wurstOwnersExpireAt = Instant.EPOCH
    private val liquidBounceCheckedUntil = hashMapOf<UUID, Instant>()
    private val labyCheckedUntil = hashMapOf<UUID, Instant>()
    private val labyInFlight = hashSetOf<UUID>()
    private val optifineCheckedUntil = hashMapOf<UUID, Instant>()
    private val optifineInFlight = hashSetOf<UUID>()
    private val profileCache = hashMapOf<String, CachedProfile>()

    @Suppress("CognitiveComplexMethod", "LongMethod")
    suspend fun refresh(players: Map<UUID, String>, clients: Set<ExternalClient>, ownershipSignals: Boolean) {
        val canonicalByLocal = resolveCanonicalPlayerUuids(players)
        val providerPlayers = players.entries.associate { (uuid, name) -> canonicalByLocal.getValue(uuid) to name }
        val requested = providerPlayers.keys.toCollection(linkedSetOf())
        if (requested.isEmpty()) {
            return
        }

        val now = Instant.now()
        var liquidBounceCandidates = emptyList<UUID>()
        var labyCandidates = emptyList<UUID>()
        var optifineCandidates = emptyList<Pair<UUID, String>>()
        var cachedMeteorOwners = emptyList<UUID>()
        var cachedMeteorExpiresAt = Instant.EPOCH
        var cachedWurstOwners = emptyList<UUID>()
        var cachedWurstExpiresAt = Instant.EPOCH
        var meteorDue = false
        var wurstDue = false
        var featherDue = false
        var essentialDue = false
        synchronized(lock) {
            if (ownershipSignals && ExternalClient.LIQUIDBOUNCE in clients) {
                liquidBounceCandidates = claimUnchecked(
                    requested,
                    liquidBounceCheckedUntil,
                    now,
                    liquidBounceCacheLifetime,
                )
            }
            if (ownershipSignals && ExternalClient.METEOR in clients) {
                if (now.isBefore(meteorOwnersExpireAt)) {
                    cachedMeteorOwners = requested.filter(meteorOwners::contains)
                    cachedMeteorExpiresAt = meteorOwnersExpireAt
                }
                if (!now.isBefore(nextMeteorRefresh)) {
                    nextMeteorRefresh = now.plus(listCacheLifetime)
                    meteorDue = true
                }
            }
            if (ownershipSignals && ExternalClient.WURST in clients) {
                if (now.isBefore(wurstOwnersExpireAt)) {
                    cachedWurstOwners = requested.filter(wurstOwners::contains)
                    cachedWurstExpiresAt = wurstOwnersExpireAt
                }
                if (!now.isBefore(nextWurstRefresh)) {
                    nextWurstRefresh = now.plus(listCacheLifetime)
                    wurstDue = true
                }
            }
            if (ExternalClient.FEATHER in clients && !now.isBefore(nextFeatherRefresh)) {
                nextFeatherRefresh = now.plus(networkRefreshInterval)
                featherDue = true
            }
            if (ownershipSignals && ExternalClient.LABYMOD in clients && !now.isBefore(nextLabyRefresh)) {
                nextLabyRefresh = now.plus(networkRefreshInterval)
                labyCandidates = claimLaby(requested, now)
            }
            if (ownershipSignals && ExternalClient.OPTIFINE in clients && !now.isBefore(nextOptifineRefresh)) {
                nextOptifineRefresh = now.plus(networkRefreshInterval)
                optifineCandidates = claimOptifine(providerPlayers, now)
            }
            if (ExternalClient.ESSENTIAL in clients && !now.isBefore(nextEssentialRefresh)) {
                nextEssentialRefresh = now.plus(networkRefreshInterval)
                essentialDue = true
            }
        }

        cachedMeteorOwners.forEach { uuid ->
            observeUntil(uuid, ExternalClient.METEOR, ExternalClientEvidence.CAPE, cachedMeteorExpiresAt)
        }
        cachedWurstOwners.forEach { uuid ->
            observeUntil(uuid, ExternalClient.WURST, ExternalClientEvidence.CAPE, cachedWurstExpiresAt)
        }
        if (liquidBounceCandidates.isNotEmpty()) {
            withContext(Dispatchers.Minecraft) {
                liquidBounceCandidates.forEach(::detectLiquidBounce)
            }
        }
        coroutineScope {
            if (meteorDue) launch { detectMeteor(requested) }
            if (wurstDue) launch { detectWurst(requested) }
            if (featherDue) launch { detectFeather(requested, ownershipSignals) }
            if (labyCandidates.isNotEmpty()) launch { detectLaby(labyCandidates) }
            if (optifineCandidates.isNotEmpty()) launch { detectOptifine(optifineCandidates) }
            if (essentialDue) launch { detectEssential(players.keys) }
        }
        mirrorCanonicalObservations(canonicalByLocal)
    }

    private suspend fun resolveCanonicalPlayerUuids(players: Map<UUID, String>): Map<UUID, UUID> {
        val now = Instant.now()
        val names = synchronized(lock) {
            profileCache.entries.removeIf { !it.value.expiresAt.isAfter(now) }
            if (now.isBefore(nextProfileRefresh)) {
                emptyList()
            } else {
                players.asSequence()
                    .filter { (uuid, name) -> uuid.version() != 4 && MINECRAFT_NAME.matches(name) }
                    .map { it.value }
                    .distinctBy(String::normalizedMinecraftName)
                    .filterNot { profileCache.containsKey(it.normalizedMinecraftName()) }
                    .take(PROFILE_LOOKUP_LIMIT)
                    .toList()
            }
        }

        if (names.isNotEmpty()) {
            var complete = true
            names.chunked(PROFILE_LOOKUP_BATCH_SIZE).forEach { batch ->
                if (!resolveProfileBatch(batch)) {
                    complete = false
                }
            }
            synchronized(lock) {
                nextProfileRefresh = Instant.now().plusSeconds(if (complete) 1 else 30)
            }
        }

        val resolved = synchronized(lock) {
            profileCache.mapNotNull { (name, cached) -> cached.uuid?.let { name to it } }.toMap()
        }
        return canonicalPlayerUuids(players, resolved)
    }

    private suspend fun resolveProfileBatch(names: List<String>): Boolean {
        val profiles = try {
            withTimeoutOrNull(5_000L) { mojangApiClient.mojangApi.fetchProfiles(names) }
                ?: return false
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            return false
        }
        val requested = names.associateBy(String::normalizedMinecraftName)
        val resolved = profiles.mapNotNull { profile ->
            val normalizedName = profile.name.normalizedMinecraftName()
            if (normalizedName !in requested) {
                return@mapNotNull null
            }
            val uuid = runCatching { UndashedUuid.fromString(profile.id) }.getOrNull()
                ?.takeIf { it.version() == 4 }
                ?: return@mapNotNull null
            normalizedName to uuid
        }.toMap()
        val expiresAt = Instant.now().plus(profileCacheLifetime)
        synchronized(lock) {
            requested.keys.forEach { name -> profileCache[name] = CachedProfile(resolved[name], expiresAt) }
        }
        return true
    }

    private fun mirrorCanonicalObservations(canonicalByLocal: Map<UUID, UUID>) {
        canonicalByLocal.forEach { (local, canonical) ->
            if (local == canonical) {
                return@forEach
            }
            ExternalClientUsers.users(canonical).forEach { user ->
                ExternalClientUsers.observe(user.copy(uuid = local))
            }
        }
    }

    private fun detectLiquidBounce(uuid: UUID) {
        CosmeticService.fetchCosmetic(uuid, CosmeticCategory.CAPE) {
            observe(uuid, ExternalClient.LIQUIDBOUNCE, ExternalClientEvidence.CAPE, liquidBounceCacheLifetime)
        }
    }

    private suspend fun detectMeteor(requested: Set<UUID>) {
        val response = requestBytes(METEOR_CAPE_OWNERS, HttpMethod.GET) ?: return
        val owners = parseMeteorCapeOwners(response)
        val expiresAt = Instant.now().plus(listCacheLifetime)
        synchronized(lock) {
            meteorOwners = owners
            meteorOwnersExpireAt = expiresAt
            nextMeteorRefresh = expiresAt
        }
        owners.filter(requested::contains).forEach { uuid ->
            observeUntil(uuid, ExternalClient.METEOR, ExternalClientEvidence.CAPE, expiresAt)
        }
    }

    private suspend fun detectWurst(requested: Set<UUID>) {
        val response = requestBytes(WURST_CAPES, HttpMethod.GET) ?: return
        val owners = parseWurstCapeOwners(response)
        val expiresAt = Instant.now().plus(listCacheLifetime)
        synchronized(lock) {
            wurstOwners = owners
            wurstOwnersExpireAt = expiresAt
            nextWurstRefresh = expiresAt
        }
        owners.filter(requested::contains).forEach { uuid ->
            observeUntil(uuid, ExternalClient.WURST, ExternalClientEvidence.CAPE, expiresAt)
        }
    }

    private suspend fun detectFeather(requested: Set<UUID>, ownershipSignals: Boolean) {
        featherAccountBatches(requested).forEach { batch ->
            val response = requestBytes(
                FEATHER_ACCOUNT_SEARCH,
                HttpMethod.POST,
                featherRequestBody(batch),
            ) ?: return@forEach
            parseFeatherAccounts(response, batch.toSet()).forEach { account ->
                if (ownershipSignals) {
                    observe(
                        account.uuid,
                        ExternalClient.FEATHER,
                        ExternalClientEvidence.ACCOUNT,
                        featherAccountLifetime,
                    )
                }
                if (account.online) {
                    observe(
                        account.uuid,
                        ExternalClient.FEATHER,
                        ExternalClientEvidence.NETWORK_ONLINE,
                        networkOnlineLifetime,
                    )
                }
            }
        }
    }

    private suspend fun detectLaby(candidates: List<UUID>) {
        candidates.chunked(4).forEach { chunk ->
            coroutineScope {
                chunk.map { uuid ->
                    async { detectLaby(uuid) }
                }.awaitAll()
            }
        }
    }

    private suspend fun detectLaby(uuid: UUID) {
        val hasCape = try {
            head("$LABY_CAPE_PREFIX$uuid")
        } catch (exception: CancellationException) {
            synchronized(lock) { labyInFlight.remove(uuid) }
            throw exception
        }
        val expiresAt = hasCape?.let { Instant.now().plus(labyCacheLifetime) }
        synchronized(lock) {
            labyInFlight.remove(uuid)
            if (expiresAt != null) {
                labyCheckedUntil[uuid] = expiresAt
            }
        }
        if (hasCape == true) {
            observeUntil(uuid, ExternalClient.LABYMOD, ExternalClientEvidence.CAPE, requireNotNull(expiresAt))
        }
    }

    private suspend fun detectOptifine(candidates: List<Pair<UUID, String>>) {
        candidates.chunked(4).forEach { chunk ->
            coroutineScope {
                chunk.map { (uuid, url) ->
                    async { detectOptifine(uuid, url) }
                }.awaitAll()
            }
        }
    }

    private suspend fun detectOptifine(uuid: UUID, url: String) {
        val hasCape = try {
            head(url)
        } catch (exception: CancellationException) {
            synchronized(lock) { optifineInFlight.remove(uuid) }
            throw exception
        }
        val expiresAt = hasCape?.let { Instant.now().plus(optifineCacheLifetime) }
        synchronized(lock) {
            optifineInFlight.remove(uuid)
            if (expiresAt != null) {
                optifineCheckedUntil[uuid] = expiresAt
            }
        }
        if (hasCape == true) {
            observeUntil(uuid, ExternalClient.OPTIFINE, ExternalClientEvidence.CAPE, requireNotNull(expiresAt))
        }
    }

    private fun detectEssential(requested: Set<UUID>) {
        EssentialPresenceBridge.onlineUsers(requested).forEach { uuid ->
            observe(uuid, ExternalClient.ESSENTIAL, ExternalClientEvidence.NETWORK_ONLINE, networkOnlineLifetime)
        }
    }

    private fun observe(
        uuid: UUID,
        client: ExternalClient,
        evidence: ExternalClientEvidence,
        lifetime: Duration,
    ) {
        val observedAt = Instant.now()
        observeUntil(uuid, client, evidence, observedAt.plus(lifetime), observedAt)
    }

    private fun observeUntil(
        uuid: UUID,
        client: ExternalClient,
        evidence: ExternalClientEvidence,
        expiresAt: Instant,
        observedAt: Instant = Instant.now(),
    ) {
        if (!expiresAt.isAfter(observedAt)) {
            return
        }
        ExternalClientUsers.observe(
            ExternalClientUser(uuid, client, evidence, observedAt, expiresAt)
        )
    }

    private suspend fun requestBytes(url: String, method: HttpMethod, body: RequestBody? = null): ByteArray? {
        if (!url.isHttps()) {
            return null
        }

        return try {
            withTimeoutOrNull(5_000L) {
                HttpClient.request(url, method, body = body).use(::readBounded)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun head(url: String): Boolean? {
        if (!url.isHttps()) {
            return null
        }

        return try {
            withTimeoutOrNull(5_000L) {
                HttpClient.request(url, HttpMethod.HEAD).use { response ->
                    response.request.url.isHttps && isCapeContentType(response.header("Content-Type"))
                }
            }
        } catch (exception: HttpException) {
            false.takeIf { exception.code == 404 }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private fun readBounded(response: Response): ByteArray? {
        if (!response.request.url.isHttps) {
            return null
        }
        val contentLength = response.body.contentLength()
        if (contentLength >= 0 && !isExternalClientResponseSizeAllowed(contentLength)) {
            return null
        }
        val source = response.body.source()
        if (source.request(EXTERNAL_CLIENT_RESPONSE_LIMIT + 1L)) {
            return null
        }
        return source.readByteArray()
    }

    private fun featherRequestBody(batch: List<UUID>): RequestBody {
        val ids = JsonArray(batch.size).apply { batch.forEach { add(it.toString()) } }
        return JsonObject().apply { add("mcID", ids) }.toString().toRequestBody(HttpClient.MediaTypes.JSON)
    }

    private fun claimUnchecked(
        requested: Set<UUID>,
        checkedUntil: MutableMap<UUID, Instant>,
        now: Instant,
        lifetime: Duration,
        limit: Int = Int.MAX_VALUE,
    ): List<UUID> {
        checkedUntil.entries.removeIf { !it.value.isAfter(now) }
        return requested.asSequence().filterNot(checkedUntil::containsKey).take(limit).toList().onEach { uuid ->
            checkedUntil[uuid] = now.plus(lifetime)
        }
    }

    private fun claimLaby(requested: Set<UUID>, now: Instant): List<UUID> {
        labyCheckedUntil.entries.removeIf { !it.value.isAfter(now) }
        return requested.asSequence()
            .filterNot(labyCheckedUntil::containsKey)
            .filterNot(labyInFlight::contains)
            .take(20)
            .toList()
            .onEach(labyInFlight::add)
    }

    private fun claimOptifine(players: Map<UUID, String>, now: Instant): List<Pair<UUID, String>> {
        optifineCheckedUntil.entries.removeIf { !it.value.isAfter(now) }
        return players.asSequence()
            .mapNotNull { (uuid, name) -> optifineCapeUrl(name)?.let { uuid to it } }
            .filterNot { (uuid) -> uuid in optifineCheckedUntil || uuid in optifineInFlight }
            .take(20)
            .toList()
            .onEach { (uuid) -> optifineInFlight.add(uuid) }
    }

    private fun String.isHttps(): Boolean = runCatching {
        URI(this).scheme.equals("https", ignoreCase = true)
    }.getOrDefault(false)
}

private const val PROFILE_LOOKUP_BATCH_SIZE = 10
private const val PROFILE_LOOKUP_LIMIT = 20

private data class CachedProfile(val uuid: UUID?, val expiresAt: Instant)

internal fun canonicalPlayerUuids(
    players: Map<UUID, String>,
    resolvedByName: Map<String, UUID>,
): Map<UUID, UUID> = players.mapValues { (uuid, name) ->
    if (uuid.version() == 4) {
        uuid
    } else {
        resolvedByName[name.normalizedMinecraftName()]?.takeIf { it.version() == 4 } ?: uuid
    }
}

private fun String.normalizedMinecraftName() = lowercase(Locale.ROOT)

internal fun optifineCapeUrl(name: String): String? =
    name.takeIf(MINECRAFT_NAME::matches)?.let { "$OPTIFINE_CAPE_PREFIX$it.png" }

internal fun isCapeContentType(value: String?): Boolean =
    value?.substringBefore(';')?.trim()?.equals("image/png", ignoreCase = true) == true
