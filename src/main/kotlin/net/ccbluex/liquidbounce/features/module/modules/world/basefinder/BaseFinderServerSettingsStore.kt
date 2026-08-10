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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.ConfigSystem
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

internal const val BASE_FINDER_SERVER_SETTINGS_VERSION = 1

/** User-controlled BaseFinder settings which follow a server or singleplayer world across dimensions. */
internal data class BaseFinderServerSettings(
    val worldSeed: String = "",
    val scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
)

/**
 * Versioned persistence keyed only by server/world identity.
 *
 * The server key is hashed before it reaches disk. A dimension is deliberately not part of this API, so callers
 * cannot accidentally split the seed or scoring profile when the player changes dimensions.
 */
@Suppress("TooManyFunctions")
internal class BaseFinderServerSettingsStore(
    private val root: Path = ConfigSystem.rootFolder.toPath().resolve("base-finder"),
) {

    private val ioLock = Any()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val settingsRoot = root.resolve("settings")
    private val settingsDirectory = settingsRoot.resolve("v$BASE_FINDER_SERVER_SETTINGS_VERSION")

    val migrationMarkerPath: Path = settingsRoot.resolve("legacy-global-settings.claimed")

    fun settingsPath(serverKey: String): Path = settingsDirectory.resolve("${hashServerKey(serverKey)}.json")

    /** Invalid, malformed, mismatched, or unsupported documents fail closed. */
    fun load(serverKey: String): BaseFinderServerSettings? = synchronized(ioLock) {
        val serverKeyHash = hashServerKey(serverKey)
        read(settingsPath(serverKey), serverKeyHash)
    }

    /** Atomically replaces one server/world profile and returns its final path. */
    fun save(serverKey: String, settings: BaseFinderServerSettings): Path = synchronized(ioLock) {
        val serverKeyHash = hashServerKey(serverKey)
        write(settingsPath(serverKey), serverKeyHash, settings)
    }

    /**
     * Loads a profile or creates it once. The old global module values are assigned to exactly one server/world;
     * every later unknown server/world starts with defaults instead of inheriting another world's seed or weights.
     */
    fun loadOrInitialize(
        serverKey: String,
        legacyCandidate: BaseFinderServerSettings,
    ): BaseFinderServerSettings = synchronized(ioLock) {
        val serverKeyHash = hashServerKey(serverKey)
        val path = settingsPath(serverKey)
        read(path, serverKeyHash)?.let { return@synchronized it }

        // Preserve an unreadable or future document instead of silently replacing user data.
        if (Files.exists(path)) {
            return@synchronized BaseFinderServerSettings()
        }

        val claimedLegacySettings = claimLegacySettings(serverKeyHash)
        val settings = if (claimedLegacySettings) legacyCandidate else BaseFinderServerSettings()
        try {
            write(path, serverKeyHash, settings)
            settings
        } catch (exception: IOException) {
            if (claimedLegacySettings) {
                releaseLegacyClaim(serverKeyHash)
            }
            throw exception
        }
    }

    private fun read(path: Path, expectedServerKeyHash: String): BaseFinderServerSettings? {
        if (!Files.isRegularFile(path)) {
            return null
        }

        return runCatching {
            val document = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).asJsonObject
            require(document.requiredInt("version") == BASE_FINDER_SERVER_SETTINGS_VERSION)
            require(document.requiredString("serverKeyHash") == expectedServerKeyHash)
            val worldSeed = document.requiredString("worldSeed")
            val scoring = document.requiredObject("scoring").entrySet().associate { (key, element) ->
                require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber)
                key to requireNotNull(element.asString.toIntOrNull())
            }
            BaseFinderServerSettings(worldSeed, BaseFinderScoringWeights.fromPersistedMap(scoring))
        }.getOrNull()
    }

    private fun write(
        path: Path,
        serverKeyHash: String,
        settings: BaseFinderServerSettings,
    ): Path {
        Files.createDirectories(path.parent)
        val temporaryPath = Files.createTempFile(path.parent, "${path.fileName}.", ".tmp")
        return try {
            Files.writeString(
                temporaryPath,
                encode(serverKeyHash, settings),
                StandardCharsets.UTF_8,
                WRITE,
            )
            moveAtomically(temporaryPath, path)
            path
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }

    private fun encode(serverKeyHash: String, settings: BaseFinderServerSettings): String {
        val scoring = JsonObject()
        settings.scoringWeights.toPersistedMap().forEach(scoring::addProperty)
        val document = JsonObject().apply {
            addProperty("version", BASE_FINDER_SERVER_SETTINGS_VERSION)
            addProperty("serverKeyHash", serverKeyHash)
            addProperty("worldSeed", settings.worldSeed)
            add("scoring", scoring)
        }
        return gson.toJson(document)
    }

    private fun claimLegacySettings(serverKeyHash: String): Boolean {
        Files.createDirectories(migrationMarkerPath.parent)
        try {
            Files.writeString(
                migrationMarkerPath,
                serverKeyHash,
                StandardCharsets.UTF_8,
                CREATE_NEW,
                WRITE,
            )
            return true
        } catch (_: FileAlreadyExistsException) {
            return false
        }
    }

    private fun releaseLegacyClaim(serverKeyHash: String) {
        runCatching {
            val markerOwner = Files.readString(migrationMarkerPath, StandardCharsets.UTF_8)
            if (markerOwner == serverKeyHash) {
                Files.deleteIfExists(migrationMarkerPath)
            }
        }
    }

    private fun hashServerKey(serverKey: String): String {
        require(serverKey.isNotBlank()) { "serverKey must not be blank" }
        return MessageDigest.getInstance("SHA-256")
            .digest(serverKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private fun moveAtomically(source: Path, destination: Path) {
        try {
            Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, REPLACE_EXISTING)
        }
    }

    private fun JsonObject.requiredString(key: String): String {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.requiredInt(key: String): Int {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return requireNotNull(value.asString.toIntOrNull())
    }

    private fun JsonObject.requiredObject(key: String): JsonObject {
        val value = get(key)
        require(value != null && value.isJsonObject)
        return value.asJsonObject
    }
}
